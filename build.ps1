$ErrorActionPreference = "Stop"

$ProjectDir = $PSScriptRoot
$ToolchainDir = Join-Path $env:USERPROFILE ".gemini\antigravity\scratch\toolchain"
$JdkHome = (Get-ChildItem "$ToolchainDir\jdk" -Directory | Select-Object -First 1).FullName
$SdkDir = "$ToolchainDir\sdk"
$BuildTools = "$SdkDir\build-tools\35.0.0"
$AndroidJarSrc = "$SdkDir\platforms\android-35\android.jar"

$Javac = "$JdkHome\bin\javac.exe"
$Jar = "$JdkHome\bin\jar.exe"
$Keytool = "$JdkHome\bin\keytool.exe"
$Aapt2 = "$BuildTools\aapt2.exe"
$D8 = "$BuildTools\d8.bat"
$Zipalign = "$BuildTools\zipalign.exe"
$Apksigner = "$BuildTools\apksigner.bat"

$env:JAVA_HOME = $JdkHome
$env:PATH = "$JdkHome\bin;$env:PATH"

Push-Location $ProjectDir
try {
    if (Test-Path "build") { Remove-Item -Recurse -Force "build" }
    New-Item -ItemType Directory -Force -Path "build\stub-classes", "build\res-flat", "build\gen", "build\app-classes", "build\dex", "dist" | Out-Null

    # Copy android.jar locally so aapt2/d8 receive pure ASCII relative path
    Copy-Item $AndroidJarSrc "build\android.jar" -Force

    Write-Host "[1/7] Compiling compile-only Xposed API stubs..."
    $stubSources = Get-ChildItem "xposed-api-stub" -Filter "*.java" -Recurse | ForEach-Object {
        $_.FullName.Substring($ProjectDir.Length + 1)
    }
    & $Javac -encoding UTF-8 -source 11 -target 11 -cp "build\android.jar" -d "build\stub-classes" $stubSources
    if ($LASTEXITCODE -ne 0) { throw "Stub compilation failed" }
    & $Jar cf "build\xposed-stub.jar" -C "build\stub-classes" .

    Write-Host "[2/7] Compiling Android resources with aapt2..."
    & $Aapt2 compile --dir "app\res" -o "build\res-flat\res.zip"
    if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

    & $Aapt2 link `
        -o "build\unsigned-raw.apk" `
        -I "build\android.jar" `
        --manifest "app\AndroidManifest.xml" `
        -A "app\assets" `
        --java "build\gen" `
        --min-sdk-version 31 `
        --target-sdk-version 35 `
        --auto-add-overlay `
        "build\res-flat\res.zip"
    if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

    Write-Host "[3/7] Compiling HyperNowBar Java sources..."
    $appSources = @(Get-ChildItem "app\java" -Filter "*.java" -Recurse | ForEach-Object { $_.FullName.Substring($ProjectDir.Length + 1) })
    $genSources = @(Get-ChildItem "build\gen" -Filter "*.java" -Recurse | ForEach-Object { $_.FullName.Substring($ProjectDir.Length + 1) })
    $allSources = $appSources + $genSources
    & $Javac -encoding UTF-8 -source 11 -target 11 -cp "build\android.jar;build\xposed-stub.jar" -d "build\app-classes" $allSources
    if ($LASTEXITCODE -ne 0) { throw "App Java compilation failed" }

    Write-Host "[4/7] Converting bytecode to DEX with d8..."
    $classFiles = Get-ChildItem "build\app-classes" -Filter "*.class" -Recurse | ForEach-Object { $_.FullName.Substring($ProjectDir.Length + 1) }
    & $D8 --min-api 31 --release --lib "build\android.jar" --output "build\dex" $classFiles
    if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

    Write-Host "[5/7] Packing classes.dex into APK & zipaligning..."
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $apkFull = Join-Path $ProjectDir "build\unsigned-raw.apk"
    $dexFull = Join-Path $ProjectDir "build\dex\classes.dex"
    $zip = [System.IO.Compression.ZipFile]::Open($apkFull, [System.IO.Compression.ZipArchiveMode]::Update)
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $dexFull, "classes.dex", [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
    $zip.Dispose()

    & $Zipalign -f -p 4 "build\unsigned-raw.apk" "build\aligned.apk"
    if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

    Write-Host "[6/7] Signing APK with v1/v2/v3 signatures..."
    if (-not (Test-Path "hypernowbar.jks")) {
        & $Keytool -genkeypair -keystore "hypernowbar.jks" -storepass hypernowbar123 -keypass hypernowbar123 -alias hypernowbar -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=HyperNowBar, OU=Mod, O=Custom, L=Local, S=Local, C=RU"
        if ($LASTEXITCODE -ne 0) { throw "keytool failed" }
    }

    & $Apksigner sign --v2-signing-enabled true --v3-signing-enabled true --ks "hypernowbar.jks" --ks-key-alias hypernowbar --ks-pass pass:hypernowbar123 --key-pass pass:hypernowbar123 --out "dist\HyperNowBar-1.G.1.apk" "build\aligned.apk"
    if ($LASTEXITCODE -ne 0) { throw "apksigner sign failed" }

    & $Apksigner verify --verbose "dist\HyperNowBar-1.G.1.apk"
    if ($LASTEXITCODE -ne 0) { throw "apksigner verify failed" }

    Write-Host "[7/7] Copying signed standalone Vector APK to Desktop..."
    $DesktopDir = [Environment]::GetFolderPath("Desktop")
    Get-ChildItem $DesktopDir -Filter "HyperNowBar-*.apk" -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue
    $DesktopApk = Join-Path $DesktopDir "HyperNowBar-1.G.1.apk"
    Copy-Item "dist\HyperNowBar-1.G.1.apk" $DesktopApk -Force

    $sizeKb = [math]::Round((Get-Item $DesktopApk).Length / 1KB, 1)
    Write-Host ""
    Write-Host "BUILD SUCCESSFUL! ($sizeKb KB)"
    Write-Host "APK (Desktop): $DesktopApk"
} finally {
    Pop-Location
}

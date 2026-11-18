package com.hypernowbar.hook;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import com.hypernowbar.model.MediaState;
import com.hypernowbar.ui.CapsuleColors;
import de.robv.android.xposed.XposedBridge;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks active media sessions directly inside SystemUI using MediaSessionManager.
 * Supports switching cleanly between multiple open music apps (e.g. Yandex Music <-> Spotify),
 * while ignoring stale/empty STATE_NONE background sessions.
 */
public final class MediaTracker {
    public interface Listener {
        void onMediaStateChanged(MediaState state);
    }

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private MediaSessionManager sessionManager;
    private final List<MediaController> validControllers = new ArrayList<>();
    private final Map<String, String> appLabelCache = new HashMap<>();
    private MediaController activeController;
    private String pinnedPackageName = null;
    private MediaState currentState = new MediaState();
    private Bitmap lastArtworkRef;
    private int cachedAccent = CapsuleColors.DEFAULT_ACCENT;

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener =
            new MediaSessionManager.OnActiveSessionsChangedListener() {
                @Override
                public void onActiveSessionsChanged(List<MediaController> controllers) {
                    mainHandler.post(() -> updateSessions(controllers));
                }
            };

    private final MediaController.Callback controllerCallback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            mainHandler.post(() -> {
                refreshStateFromController();
                if (state == null || state.getState() != PlaybackState.STATE_PLAYING) {
                    pollActiveSessions();
                }
            });
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            mainHandler.post(() -> refreshStateFromController());
        }

        @Override
        public void onSessionDestroyed() {
            mainHandler.post(() -> {
                pinnedPackageName = null;
                pollActiveSessions();
            });
        }
    };

    public MediaTracker(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public void start() {
        try {
            sessionManager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (sessionManager == null) return;
            sessionManager.addOnActiveSessionsChangedListener(sessionsListener, null, mainHandler);
            pollActiveSessions();
        } catch (Throwable t) {
            XposedBridge.log("[HyperNowBar] MediaTracker start error: " + t.getMessage());
        }
    }

    public void pollActiveSessions() {
        if (sessionManager == null) return;
        try {
            List<MediaController> list = sessionManager.getActiveSessions(null);
            updateSessions(list);
        } catch (Throwable t) {
            XposedBridge.log("[HyperNowBar] MediaTracker poll error: " + t.getMessage());
        }
    }

    /**
     * Cycles to the next open music player (when 2+ music apps are open).
     */
    public void switchToNextPlayer() {
        if (validControllers.size() <= 1) return;
        int curIdx = findActiveIndex();
        int nextIdx = (curIdx + 1) % validControllers.size();
        MediaController next = validControllers.get(nextIdx);
        pinnedPackageName = next.getPackageName();
        bindActiveController(next);
        refreshStateFromController();
    }

    private void updateSessions(List<MediaController> rawControllers) {
        // Filter out dead/empty sessions (e.g. STATE_NONE without track metadata) and deduplicate per package
        Map<String, MediaController> byPkg = new LinkedHashMap<>();
        if (rawControllers != null) {
            for (MediaController mc : rawControllers) {
                if (mc == null || !isRealMediaSession(mc)) continue;
                String pkg = mc.getPackageName() != null ? mc.getPackageName() : "";
                MediaController existing = byPkg.get(pkg);
                if (existing == null || isPlayingController(mc)) {
                    byPkg.put(pkg, mc);
                }
            }
        }

        validControllers.clear();
        validControllers.addAll(byPkg.values());

        MediaController chosen = null;

        // 1. If user explicitly pinned a player via switcher and it's still open, honor it
        if (pinnedPackageName != null) {
            for (MediaController mc : validControllers) {
                if (pinnedPackageName.equals(mc.getPackageName())) {
                    chosen = mc;
                    break;
                }
            }
            if (chosen == null) {
                pinnedPackageName = null;
            }
        }

        // 2. Otherwise prefer whichever session is actively PLAYING
        if (chosen == null) {
            for (MediaController mc : validControllers) {
                if (isPlayingController(mc)) {
                    chosen = mc;
                    break;
                }
            }
        }

        // 3. Fallback to first valid paused session
        if (chosen == null && !validControllers.isEmpty()) {
            chosen = validControllers.get(0);
        }

        bindActiveController(chosen);
        refreshStateFromController();
    }

    private static boolean isPlayingController(MediaController mc) {
        PlaybackState ps = mc.getPlaybackState();
        return ps != null && (ps.getState() == PlaybackState.STATE_PLAYING
                || ps.getState() == PlaybackState.STATE_BUFFERING);
    }

    private static boolean isRealMediaSession(MediaController mc) {
        PlaybackState ps = mc.getPlaybackState();
        int st = ps != null ? ps.getState() : PlaybackState.STATE_NONE;
        if (st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_BUFFERING) {
            return true;
        }
        MediaMetadata meta = mc.getMetadata();
        boolean hasTitle = false;
        if (meta != null) {
            String t1 = meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
            String t2 = meta.getString(MediaMetadata.METADATA_KEY_TITLE);
            hasTitle = (t1 != null && !t1.trim().isEmpty()) || (t2 != null && !t2.trim().isEmpty());
        }
        return st == PlaybackState.STATE_PAUSED && hasTitle;
    }

    private void bindActiveController(MediaController target) {
        if (activeController == target) return;
        if (activeController != null) {
            try {
                activeController.unregisterCallback(controllerCallback);
            } catch (Throwable ignored) {}
        }
        activeController = target;
        if (activeController != null) {
            try {
                activeController.registerCallback(controllerCallback, mainHandler);
            } catch (Throwable ignored) {}
        }
    }

    private int findActiveIndex() {
        if (activeController == null || validControllers.isEmpty()) return 0;
        for (int i = 0; i < validControllers.size(); i++) {
            MediaController mc = validControllers.get(i);
            if (mc == activeController || (mc.getPackageName() != null
                    && mc.getPackageName().equals(activeController.getPackageName()))) {
                return i;
            }
        }
        return 0;
    }

    private String resolveAppLabel(String pkg) {
        if (pkg == null || pkg.isEmpty()) return "";
        String cached = appLabelCache.get(pkg);
        if (cached != null) return cached;
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            CharSequence lbl = pm.getApplicationLabel(info);
            String name = lbl != null ? lbl.toString() : pkg;
            appLabelCache.put(pkg, name);
            return name;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private void refreshStateFromController() {
        MediaState s = new MediaState();
        MediaController mc = activeController;
        if (mc != null) {
            MediaMetadata meta = mc.getMetadata();
            PlaybackState ps = mc.getPlaybackState();
            int pState = ps != null ? ps.getState() : PlaybackState.STATE_NONE;
            boolean validState = pState != PlaybackState.STATE_NONE
                    && pState != PlaybackState.STATE_STOPPED
                    && pState != PlaybackState.STATE_ERROR;

            if (meta != null || validState) {
                s.hasSession = true;
                s.isPlaying = (pState == PlaybackState.STATE_PLAYING || pState == PlaybackState.STATE_BUFFERING);
                s.packageName = mc.getPackageName() != null ? mc.getPackageName() : "";
                s.appLabel = resolveAppLabel(s.packageName);
                s.sessionCount = Math.max(1, validControllers.size());
                s.activeSessionIndex = findActiveIndex();
                s.sessionActivity = mc.getSessionActivity();

                if (meta != null) {
                    String title = meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
                    if (title == null || title.isEmpty()) {
                        title = meta.getString(MediaMetadata.METADATA_KEY_TITLE);
                    }
                    String artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST);
                    if (artist == null || artist.isEmpty()) {
                        artist = meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
                    }
                    s.title = title != null && !title.isEmpty() ? title : "Медиаплеер";
                    s.artist = artist != null ? artist : "";
                    s.durationMs = Math.max(0L, meta.getLong(MediaMetadata.METADATA_KEY_DURATION));

                    Bitmap bmp = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                    if (bmp == null) bmp = meta.getBitmap(MediaMetadata.METADATA_KEY_ART);
                    if (bmp == null) bmp = meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
                    s.artwork = bmp;

                    if (bmp != null && bmp != lastArtworkRef) {
                        lastArtworkRef = bmp;
                        cachedAccent = CapsuleColors.extractVibrantAccent(bmp);
                    } else if (bmp == null) {
                        lastArtworkRef = null;
                        cachedAccent = CapsuleColors.DEFAULT_ACCENT;
                    }
                    s.accentColor = cachedAccent;
                } else {
                    s.title = "Воспроизведение";
                    s.artist = "";
                    s.accentColor = CapsuleColors.DEFAULT_ACCENT;
                }

                if (ps != null) {
                    s.positionMs = Math.max(0L, ps.getPosition());
                    s.playbackSpeed = ps.getPlaybackSpeed() > 0f ? ps.getPlaybackSpeed() : 1.0f;
                    s.positionUpdateTimeMs = ps.getLastPositionUpdateTime() > 0L
                            ? ps.getLastPositionUpdateTime()
                            : SystemClock.elapsedRealtime();
                }
            }
        }
        currentState = s;
        if (listener != null) {
            listener.onMediaStateChanged(s);
        }
    }

    public MediaState getCurrentState() {
        return currentState;
    }

    public void playPause() {
        if (activeController == null) return;
        try {
            if (currentState.isPlaying) {
                activeController.getTransportControls().pause();
            } else {
                activeController.getTransportControls().play();
            }
        } catch (Throwable ignored) {}
    }

    public void next() {
        if (activeController == null) return;
        try {
            activeController.getTransportControls().skipToNext();
        } catch (Throwable ignored) {}
    }

    public void previous() {
        if (activeController == null) return;
        try {
            activeController.getTransportControls().skipToPrevious();
        } catch (Throwable ignored) {}
    }

    public void seekTo(long positionMs) {
        if (activeController == null) return;
        try {
            activeController.getTransportControls().seekTo(Math.max(0L, positionMs));
        } catch (Throwable ignored) {}
    }

    public void openMediaApp() {
        if (currentState.sessionActivity == null) return;
        try {
            currentState.sessionActivity.send();
        } catch (Throwable ignored) {}
    }
}

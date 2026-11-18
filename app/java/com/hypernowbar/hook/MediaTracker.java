package com.hypernowbar.hook;

import android.app.PendingIntent;
import android.content.Context;
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
import java.util.List;

/**
 * Tracks active media sessions directly inside SystemUI using MediaSessionManager.
 */
public final class MediaTracker {
    public interface Listener {
        void onMediaStateChanged(MediaState state);
    }

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private MediaSessionManager sessionManager;
    private MediaController activeController;
    private MediaState currentState = new MediaState();
    private Bitmap lastArtworkRef;
    private int cachedAccent = CapsuleColors.DEFAULT_ACCENT;

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener =
            new MediaSessionManager.OnActiveSessionsChangedListener() {
                @Override
                public void onActiveSessionsChanged(List<MediaController> controllers) {
                    mainHandler.post(() -> selectController(controllers));
                }
            };

    private final MediaController.Callback controllerCallback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            mainHandler.post(() -> {
                refreshStateFromController();
                // If current controller stopped/paused, see if another session is playing
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
            mainHandler.post(() -> pollActiveSessions());
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
            selectController(list);
        } catch (Throwable t) {
            XposedBridge.log("[HyperNowBar] MediaTracker poll error: " + t.getMessage());
        }
    }

    private void selectController(List<MediaController> controllers) {
        MediaController best = null;
        if (controllers != null) {
            for (MediaController mc : controllers) {
                if (mc == null) continue;
                PlaybackState ps = mc.getPlaybackState();
                if (ps != null && ps.getState() == PlaybackState.STATE_PLAYING) {
                    best = mc;
                    break;
                }
                if (best == null && mc.getMetadata() != null) {
                    best = mc;
                }
            }
            if (best == null && !controllers.isEmpty()) {
                best = controllers.get(0);
            }
        }

        if (activeController != best) {
            if (activeController != null) {
                try {
                    activeController.unregisterCallback(controllerCallback);
                } catch (Throwable ignored) {}
            }
            activeController = best;
            if (activeController != null) {
                try {
                    activeController.registerCallback(controllerCallback, mainHandler);
                } catch (Throwable ignored) {}
            }
        }
        refreshStateFromController();
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

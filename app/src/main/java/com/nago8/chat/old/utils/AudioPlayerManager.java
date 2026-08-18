package com.nago8.chat.old.utils;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.nago8.chat.old.net.ApiClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 全局语音播放管理器
 * 支持：OkHttp 高速缓存下载 (100% 解决 Android 4.4 HTTPS/TLS -1004 错误与防盗链)、
 *      Referer 请求头、进度回调、快进快退拖动与后台播放
 */
public class AudioPlayerManager {

    private static final String TAG = "AudioPlayerManager";
    private static final String REFERER_VALUE = "http://myapp.jwznb.com";

    private static volatile AudioPlayerManager instance;

    public interface OnAudioPlayListener {
        void onStartPrepare();
        void onPrepared(int totalDurationMs);
        void onProgress(int currentMs, int totalMs);
        void onPause();
        void onResume();
        void onComplete();
        void onError(String errorMsg);
    }

    public interface GlobalAudioPlayListener {
        void onPlaybackStateChanged(State state, String msgId, int currentMs, int totalMs);
    }

    public interface GlobalAudioCompletionListener {
        void onAudioCompleted(String msgId);
    }

    public enum State {
        IDLE,
        PREPARING,
        PLAYING,
        PAUSED
    }

    private MediaPlayer mediaPlayer;
    private State currentState = State.IDLE;
    private String currentMsgId = null;
    private String currentAudioUrl = null;
    private int currentDuration = 0;
    private Call currentDownloadCall = null;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, OnAudioPlayListener> listeners = new HashMap<>();
    private final java.util.List<GlobalAudioPlayListener> globalListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<GlobalAudioCompletionListener> globalCompletionListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && currentState == State.PLAYING) {
                try {
                    int currentPos = mediaPlayer.getCurrentPosition();
                    int duration = mediaPlayer.getDuration();
                    if (duration > 0) {
                        currentDuration = duration;
                    }
                    notifyProgress(currentMsgId, currentPos, currentDuration);
                } catch (Exception ignored) {}
                mainHandler.postDelayed(this, 100);
            }
        }
    };

    private AudioPlayerManager() {}

    public static AudioPlayerManager getInstance() {
        if (instance == null) {
            synchronized (AudioPlayerManager.class) {
                if (instance == null) {
                    instance = new AudioPlayerManager();
                }
            }
        }
        return instance;
    }

    public void addGlobalListener(GlobalAudioPlayListener listener) {
        if (listener != null && !globalListeners.contains(listener)) {
            globalListeners.add(listener);
        }
    }

    public void removeGlobalListener(GlobalAudioPlayListener listener) {
        if (listener != null) {
            globalListeners.remove(listener);
        }
    }

    public void addGlobalCompletionListener(GlobalAudioCompletionListener listener) {
        if (listener != null && !globalCompletionListeners.contains(listener)) {
            globalCompletionListeners.add(listener);
        }
    }

    public void removeGlobalCompletionListener(GlobalAudioCompletionListener listener) {
        if (listener != null) {
            globalCompletionListeners.remove(listener);
        }
    }

    public synchronized void registerListener(String msgId, OnAudioPlayListener listener) {
        if (msgId != null && listener != null) {
            listeners.put(msgId, listener);
        }
    }

    public synchronized void unregisterListener(String msgId) {
        if (msgId != null) {
            listeners.remove(msgId);
        }
    }

    public synchronized State getCurrentState() {
        return currentState;
    }

    public synchronized State getCurrentState(String msgId) {
        if (msgId != null && msgId.equals(currentMsgId)) {
            return currentState;
        }
        return State.IDLE;
    }

    public synchronized int getCurrentPosition() {
        if (mediaPlayer != null) {
            try {
                return mediaPlayer.getCurrentPosition();
            } catch (Exception ignored) {}
        }
        return 0;
    }

    public synchronized int getCurrentPosition(String msgId) {
        if (msgId != null && msgId.equals(currentMsgId) && mediaPlayer != null) {
            try {
                return mediaPlayer.getCurrentPosition();
            } catch (Exception ignored) {}
        }
        return 0;
    }

    public synchronized int getDuration() {
        return currentDuration;
    }

    public synchronized int getDuration(String msgId) {
        if (msgId != null && msgId.equals(currentMsgId)) {
            return currentDuration;
        }
        return 0;
    }

    public synchronized String getCurrentMsgId() {
        return currentMsgId;
    }

    public synchronized boolean isAnyPlayingOrPaused() {
        return currentState == State.PLAYING || currentState == State.PAUSED || currentState == State.PREPARING;
    }

    /**
     * 切换当前播放/暂停
     */
    public synchronized void toggleCurrentPlay() {
        if (currentState == State.PLAYING) {
            pause();
        } else if (currentState == State.PAUSED) {
            resume();
        }
    }

    /**
     * 切换播放/暂停状态
     */
    public synchronized void togglePlay(Context context, String audioUrl, String msgId, int expectedDurationSec, OnAudioPlayListener listener) {
        if (TextUtils.isEmpty(audioUrl) || TextUtils.isEmpty(msgId)) return;

        registerListener(msgId, listener);

        if (msgId.equals(currentMsgId)) {
            if (currentState == State.PLAYING) {
                pause();
            } else if (currentState == State.PAUSED) {
                resume();
            } else if (currentState == State.IDLE) {
                play(context, audioUrl, msgId, expectedDurationSec);
            }
        } else {
            play(context, audioUrl, msgId, expectedDurationSec);
        }
    }

    /**
     * 开始播放
     */
    public synchronized void play(Context context, String audioUrl, String msgId, int expectedDurationSec) {
        if (TextUtils.isEmpty(audioUrl) || TextUtils.isEmpty(msgId)) return;

        stop();

        currentMsgId = msgId;
        currentAudioUrl = audioUrl;
        currentDuration = expectedDurationSec > 0 ? expectedDurationSec * 1000 : 0;
        currentState = State.PREPARING;

        notifyStartPrepare(msgId);

        final String targetUrl = normalizeUrl(audioUrl);
        File cacheFile = getCacheFile(context, targetUrl);

        if (cacheFile.exists() && cacheFile.length() > 0) {
            // 本地缓存已存在，直接播放
            startMediaPlayerWithFile(cacheFile.getAbsolutePath(), msgId);
        } else {
            // 本地不存在，通过 OkHttp 携带 Referer 下载并缓存到本地（解决 Android 4.4 TLS/SSL -1004 错误）
            downloadAndPlay(context, targetUrl, msgId, cacheFile);
        }
    }

    private void downloadAndPlay(Context context, String targetUrl, String msgId, File cacheFile) {
        Request.Builder reqBuilder = new Request.Builder().url(targetUrl);
        if (targetUrl.contains(".jwznb.com")) {
            reqBuilder.addHeader("Referer", REFERER_VALUE);
        }

        currentDownloadCall = ApiClient.getClient().newCall(reqBuilder.build());
        currentDownloadCall.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (call.isCanceled()) return;
                mainHandler.post(() -> {
                    synchronized (AudioPlayerManager.this) {
                        if (!msgId.equals(currentMsgId)) return;
                        currentState = State.IDLE;
                        notifyError(msgId, "下载失败: " + e.getMessage());
                        stop();
                    }
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (call.isCanceled()) {
                    response.close();
                    return;
                }
                if (!response.isSuccessful() || response.body() == null) {
                    response.close();
                    mainHandler.post(() -> {
                        synchronized (AudioPlayerManager.this) {
                            if (!msgId.equals(currentMsgId)) return;
                            currentState = State.IDLE;
                            notifyError(msgId, "下载失败 (HTTP " + response.code() + ")");
                            stop();
                        }
                    });
                    return;
                }

                File parentDir = cacheFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    parentDir.mkdirs();
                }

                File tempFile = new File(cacheFile.getAbsolutePath() + ".tmp");
                boolean success = false;
                try (InputStream is = response.body().byteStream();
                     FileOutputStream fos = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                    }
                    fos.flush();
                    success = true;
                } catch (Exception e) {
                    Log.e(TAG, "Failed to save voice cache", e);
                } finally {
                    response.close();
                }

                if (success && tempFile.length() > 0) {
                    if (cacheFile.exists()) {
                        //noinspection ResultOfMethodCallIgnored
                        cacheFile.delete();
                    }
                    //noinspection ResultOfMethodCallIgnored
                    tempFile.renameTo(cacheFile);

                    mainHandler.post(() -> {
                        synchronized (AudioPlayerManager.this) {
                            if (!msgId.equals(currentMsgId)) return;
                            startMediaPlayerWithFile(cacheFile.getAbsolutePath(), msgId);
                        }
                    });
                } else {
                    //noinspection ResultOfMethodCallIgnored
                    tempFile.delete();
                    mainHandler.post(() -> {
                        synchronized (AudioPlayerManager.this) {
                            if (!msgId.equals(currentMsgId)) return;
                            currentState = State.IDLE;
                            notifyError(msgId, "写入缓存失败");
                            stop();
                        }
                    });
                }
            }
        });
    }

    private void startMediaPlayerWithFile(String filePath, String msgId) {
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.setDataSource(filePath);

            mediaPlayer.setOnPreparedListener(mp -> {
                synchronized (AudioPlayerManager.this) {
                    if (!msgId.equals(currentMsgId)) return;
                    currentState = State.PLAYING;
                    int dur = mp.getDuration();
                    if (dur > 0) {
                        currentDuration = dur;
                    }
                    mp.start();
                    mainHandler.removeCallbacks(progressRunnable);
                    mainHandler.post(progressRunnable);
                    notifyPrepared(msgId, currentDuration);
                }
            });

            mediaPlayer.setOnCompletionListener(mp -> {
                synchronized (AudioPlayerManager.this) {
                    if (!msgId.equals(currentMsgId)) return;
                    currentState = State.IDLE;
                    mainHandler.removeCallbacks(progressRunnable);
                    notifyNaturalComplete(msgId);
                }
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                synchronized (AudioPlayerManager.this) {
                    if (!msgId.equals(currentMsgId)) return false;
                    currentState = State.IDLE;
                    mainHandler.removeCallbacks(progressRunnable);
                    notifyError(msgId, "播放出错 (" + what + ", " + extra + ")");
                    stop();
                    return true;
                }
            });

            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            Log.e(TAG, "Failed to start local audio playback", e);
            currentState = State.IDLE;
            notifyError(msgId, "播放失败: " + e.getMessage());
            stop();
        }
    }

    public synchronized void pause() {
        if (mediaPlayer != null && currentState == State.PLAYING) {
            try {
                mediaPlayer.pause();
                currentState = State.PAUSED;
                mainHandler.removeCallbacks(progressRunnable);
                notifyPause(currentMsgId);
            } catch (Exception e) {
                Log.e(TAG, "pause failed", e);
            }
        }
    }

    public synchronized void resume() {
        if (mediaPlayer != null && currentState == State.PAUSED) {
            try {
                mediaPlayer.start();
                currentState = State.PLAYING;
                mainHandler.removeCallbacks(progressRunnable);
                mainHandler.post(progressRunnable);
                notifyResume(currentMsgId);
            } catch (Exception e) {
                Log.e(TAG, "resume failed", e);
            }
        }
    }

    public synchronized void seekTo(int positionMs) {
        if (mediaPlayer != null && (currentState == State.PLAYING || currentState == State.PAUSED)) {
            try {
                mediaPlayer.seekTo(positionMs);
            } catch (Exception e) {
                Log.e(TAG, "seekTo failed", e);
            }
        }
    }

    public synchronized void stop() {
        mainHandler.removeCallbacks(progressRunnable);
        if (currentDownloadCall != null) {
            currentDownloadCall.cancel();
            currentDownloadCall = null;
        }
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
        String oldMsgId = currentMsgId;
        currentState = State.IDLE;
        currentMsgId = null;
        currentAudioUrl = null;
        currentDuration = 0;
        if (oldMsgId != null) {
            notifyStop(oldMsgId);
        }
    }

    private String normalizeUrl(String url) {
        if (url == null) return "";
        String trimmed = url.trim();
        if (Build.VERSION.SDK_INT < 21 || trimmed.contains(".jwznb.com")) {
            if (trimmed.startsWith("https://")) {
                return "http://" + trimmed.substring(8);
            }
        }
        return trimmed;
    }

    private File getCacheFile(Context context, String url) {
        File dir = new File(context.getCacheDir(), "voice_cache");
        String hash = md5(url);
        return new File(dir, "voice_" + hash + ".m4a");
    }

    private String md5(String s) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(s.getBytes());
            byte[] messageDigest = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : messageDigest) {
                String hex = Integer.toHexString(0xFF & b);
                while (hex.length() < 2) hex = "0" + hex;
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf(s.hashCode());
        }
    }

    // ──── 回调派发 ────

    private void notifyStartPrepare(String msgId) {
        mainHandler.post(() -> {
            OnAudioPlayListener listener = listeners.get(msgId);
            if (listener != null) listener.onStartPrepare();
            for (GlobalAudioPlayListener gl : globalListeners) {
                gl.onPlaybackStateChanged(State.PREPARING, msgId, 0, currentDuration);
            }
        });
    }

    private void notifyPrepared(String msgId, int durationMs) {
        mainHandler.post(() -> {
            OnAudioPlayListener listener = listeners.get(msgId);
            if (listener != null) listener.onPrepared(durationMs);
            for (GlobalAudioPlayListener gl : globalListeners) {
                gl.onPlaybackStateChanged(State.PLAYING, msgId, 0, durationMs);
            }
        });
    }

    private void notifyProgress(String msgId, int currentMs, int totalMs) {
        mainHandler.post(() -> {
            OnAudioPlayListener listener = listeners.get(msgId);
            if (listener != null) listener.onProgress(currentMs, totalMs);
            for (GlobalAudioPlayListener gl : globalListeners) {
                gl.onPlaybackStateChanged(State.PLAYING, msgId, currentMs, totalMs);
            }
        });
    }

    private void notifyPause(String msgId) {
        mainHandler.post(() -> {
            OnAudioPlayListener listener = listeners.get(msgId);
            if (listener != null) listener.onPause();
            for (GlobalAudioPlayListener gl : globalListeners) {
                gl.onPlaybackStateChanged(State.PAUSED, msgId, getCurrentPosition(), currentDuration);
            }
        });
    }

    private void notifyResume(String msgId) {
        mainHandler.post(() -> {
            OnAudioPlayListener listener = listeners.get(msgId);
            if (listener != null) listener.onResume();
            for (GlobalAudioPlayListener gl : globalListeners) {
                gl.onPlaybackStateChanged(State.PLAYING, msgId, getCurrentPosition(), currentDuration);
            }
        });
    }

    private void notifyNaturalComplete(String msgId) {
        mainHandler.post(() -> {
            OnAudioPlayListener listener = listeners.get(msgId);
            if (listener != null) listener.onComplete();
            for (GlobalAudioPlayListener gl : globalListeners) {
                gl.onPlaybackStateChanged(State.IDLE, msgId, 0, 0);
            }
            for (GlobalAudioCompletionListener gcl : globalCompletionListeners) {
                gcl.onAudioCompleted(msgId);
            }
        });
    }

    private void notifyStop(String msgId) {
        mainHandler.post(() -> {
            OnAudioPlayListener listener = listeners.get(msgId);
            if (listener != null) listener.onComplete();
            for (GlobalAudioPlayListener gl : globalListeners) {
                gl.onPlaybackStateChanged(State.IDLE, msgId, 0, 0);
            }
        });
    }

    private void notifyError(String msgId, String errorMsg) {
        mainHandler.post(() -> {
            OnAudioPlayListener listener = listeners.get(msgId);
            if (listener != null) listener.onError(errorMsg);
            for (GlobalAudioPlayListener gl : globalListeners) {
                gl.onPlaybackStateChanged(State.IDLE, msgId, 0, 0);
            }
        });
    }
}

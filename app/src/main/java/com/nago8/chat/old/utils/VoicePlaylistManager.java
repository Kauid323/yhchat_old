package com.nago8.chat.old.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.nago8.chat.old.model.VoicePlaylistItem;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 全局语音播放列表管理器
 * 支持：顺序播放、单曲循环、随机播放、拖拽排序、增删项以及自动切歌连播
 */
public class VoicePlaylistManager {

    private static final String PREF_NAME = "pref_voice_playlist";
    private static final String KEY_PLAYLIST = "key_playlist_json";
    private static final String KEY_PLAY_MODE = "key_play_mode";

    public enum PlayMode {
        SEQUENCE,     // 顺序循环
        SINGLE_LOOP,  // 单曲循环
        SHUFFLE       // 随机播放
    }

    public interface OnPlaylistChangeListener {
        void onPlaylistChanged();
        void onPlayModeChanged(PlayMode mode);
        void onCurrentItemChanged(VoicePlaylistItem currentItem, int index);
    }

    private static volatile VoicePlaylistManager instance;
    private final List<VoicePlaylistItem> playlist = new ArrayList<>();
    private final List<OnPlaylistChangeListener> listeners = new CopyOnWriteArrayList<>();
    private PlayMode playMode = PlayMode.SEQUENCE;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Context appContext;
    private final Random random = new Random();
    private boolean isInitialized = false;

    private VoicePlaylistManager() {}

    public static VoicePlaylistManager getInstance() {
        if (instance == null) {
            synchronized (VoicePlaylistManager.class) {
                if (instance == null) {
                    instance = new VoicePlaylistManager();
                }
            }
        }
        return instance;
    }

    public synchronized void init(Context context) {
        if (context == null) return;
        this.appContext = context.getApplicationContext();
        if (!isInitialized) {
            loadFromDisk();
            isInitialized = true;

            // 监听全局播放状态（仅用于通知 UI 刷新当前播放条目高亮）
            AudioPlayerManager.getInstance().addGlobalListener((state, msgId, currentMs, totalMs) -> {
                notifyCurrentItemChanged();
            });

            // 只有当音频真正播放完毕（自然结束）时，才调度连播下一首
            AudioPlayerManager.getInstance().addGlobalCompletionListener(this::handleAudioComplete);
        }
    }

    private synchronized void loadFromDisk() {
        if (appContext == null) return;
        SharedPreferences sp = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int modeOrdinal = sp.getInt(KEY_PLAY_MODE, PlayMode.SEQUENCE.ordinal());
        if (modeOrdinal >= 0 && modeOrdinal < PlayMode.values().length) {
            playMode = PlayMode.values()[modeOrdinal];
        }

        String json = sp.getString(KEY_PLAYLIST, null);
        if (json != null && !json.isEmpty()) {
            try {
                Type type = new TypeToken<ArrayList<VoicePlaylistItem>>() {}.getType();
                List<VoicePlaylistItem> list = new Gson().fromJson(json, type);
                playlist.clear();
                if (list != null) {
                    playlist.addAll(list);
                }
            } catch (Exception ignored) {}
        }
    }

    private synchronized void saveToDisk() {
        if (appContext == null) return;
        SharedPreferences sp = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = new Gson().toJson(playlist);
        sp.edit()
                .putString(KEY_PLAYLIST, json)
                .putInt(KEY_PLAY_MODE, playMode.ordinal())
                .apply();
    }

    public synchronized List<VoicePlaylistItem> getPlaylist() {
        return new ArrayList<>(playlist);
    }

    public synchronized int getCount() {
        return playlist.size();
    }

    public synchronized PlayMode getPlayMode() {
        return playMode;
    }

    public synchronized void setPlayMode(PlayMode mode) {
        if (mode == null) return;
        this.playMode = mode;
        saveToDisk();
        mainHandler.post(() -> {
            for (OnPlaylistChangeListener l : listeners) {
                l.onPlayModeChanged(playMode);
            }
        });
    }

    public synchronized void toggleNextPlayMode() {
        switch (playMode) {
            case SEQUENCE:
                setPlayMode(PlayMode.SINGLE_LOOP);
                break;
            case SINGLE_LOOP:
                setPlayMode(PlayMode.SHUFFLE);
                break;
            case SHUFFLE:
                setPlayMode(PlayMode.SEQUENCE);
                break;
        }
    }

    public synchronized boolean addItem(VoicePlaylistItem item) {
        if (item == null || item.msgId == null || item.msgId.isEmpty()) return false;
        for (VoicePlaylistItem existing : playlist) {
            if (item.msgId.equals(existing.msgId)) {
                return false; // 已存在
            }
        }
        playlist.add(item);
        saveToDisk();
        notifyPlaylistChanged();
        return true;
    }

    public synchronized void removeItem(int index) {
        if (index >= 0 && index < playlist.size()) {
            playlist.remove(index);
            saveToDisk();
            notifyPlaylistChanged();
        }
    }

    public synchronized void removeItem(String msgId) {
        if (msgId == null) return;
        boolean removed = false;
        for (int i = 0; i < playlist.size(); i++) {
            if (msgId.equals(playlist.get(i).msgId)) {
                playlist.remove(i);
                removed = true;
                break;
            }
        }
        if (removed) {
            saveToDisk();
            notifyPlaylistChanged();
        }
    }

    public synchronized void moveItem(int fromPosition, int toPosition) {
        if (fromPosition < 0 || fromPosition >= playlist.size() || toPosition < 0 || toPosition >= playlist.size()) {
            return;
        }
        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) {
                Collections.swap(playlist, i, i + 1);
            }
        } else {
            for (int i = fromPosition; i > toPosition; i--) {
                Collections.swap(playlist, i, i - 1);
            }
        }
        saveToDisk();
        notifyPlaylistChanged();
    }

    public synchronized void clear() {
        playlist.clear();
        saveToDisk();
        notifyPlaylistChanged();
    }

    public synchronized boolean contains(String msgId) {
        if (msgId == null) return false;
        for (VoicePlaylistItem item : playlist) {
            if (msgId.equals(item.msgId)) return true;
        }
        return false;
    }

    public synchronized int getCurrentPlayingIndex() {
        String currentMsgId = AudioPlayerManager.getInstance().getCurrentMsgId();
        if (currentMsgId == null) return -1;
        for (int i = 0; i < playlist.size(); i++) {
            if (currentMsgId.equals(playlist.get(i).msgId)) {
                return i;
            }
        }
        return -1;
    }

    public synchronized VoicePlaylistItem getCurrentPlayingItem() {
        int idx = getCurrentPlayingIndex();
        if (idx >= 0 && idx < playlist.size()) {
            return playlist.get(idx);
        }
        return null;
    }

    /**
     * 播放指定下标的语音
     */
    public synchronized void playItem(Context context, int index) {
        if (index < 0 || index >= playlist.size()) return;
        VoicePlaylistItem item = playlist.get(index);
        playItemInternal(context, item);
    }

    private long lastAutoPlayTime = 0;

    /**
     * 播放下一首
     */
    public synchronized void playNext(Context context) {
        if (playlist.isEmpty()) return;
        int currentIndex = getCurrentPlayingIndex();
        playNextFromIndex(context, currentIndex);
    }

    public synchronized void playNextFromIndex(Context context, int currentIndex) {
        if (playlist.isEmpty()) return;
        int nextIndex;

        if (playMode == PlayMode.SINGLE_LOOP) {
            nextIndex = (currentIndex >= 0 && currentIndex < playlist.size()) ? currentIndex : 0;
        } else if (playMode == PlayMode.SHUFFLE) {
            if (playlist.size() > 1) {
                do {
                    nextIndex = random.nextInt(playlist.size());
                } while (nextIndex == currentIndex);
            } else {
                nextIndex = 0;
            }
        } else {
            // SEQUENCE 顺序循环
            if (currentIndex >= 0 && currentIndex + 1 < playlist.size()) {
                nextIndex = currentIndex + 1;
            } else {
                nextIndex = 0; // 回到第一首
            }
        }

        playItem(context, nextIndex);
    }

    private void playItemInternal(Context context, VoicePlaylistItem item) {
        if (item == null || item.audioUrl == null) return;
        Context ctx = context != null ? context : appContext;
        if (ctx == null) return;

        AudioPlayerManager.getInstance().play(ctx, item.audioUrl, item.msgId, item.durationSec);
        notifyCurrentItemChanged();
    }

    private synchronized void handleAudioComplete(String lastMsgId) {
        if (lastMsgId == null || playlist.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (now - lastAutoPlayTime < 500) {
            return;
        }

        int lastIndex = -1;
        for (int i = 0; i < playlist.size(); i++) {
            if (lastMsgId.equals(playlist.get(i).msgId)) {
                lastIndex = i;
                break;
            }
        }

        if (lastIndex >= 0) {
            lastAutoPlayTime = now;
            final int completedIndex = lastIndex;
            mainHandler.postDelayed(() -> {
                if (appContext != null && !playlist.isEmpty()) {
                    playNextFromIndex(appContext, completedIndex);
                }
            }, 300);
        }
    }

    public void addListener(OnPlaylistChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(OnPlaylistChangeListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    private void notifyPlaylistChanged() {
        mainHandler.post(() -> {
            for (OnPlaylistChangeListener l : listeners) {
                l.onPlaylistChanged();
            }
        });
    }

    private void notifyCurrentItemChanged() {
        mainHandler.post(() -> {
            VoicePlaylistItem item = getCurrentPlayingItem();
            int idx = getCurrentPlayingIndex();
            for (OnPlaylistChangeListener l : listeners) {
                l.onCurrentItemChanged(item, idx);
            }
        });
    }
}

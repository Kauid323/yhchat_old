package com.nago8.chat.old.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * 语音播放列表项
 */
public class VoicePlaylistItem implements Serializable {
    public String msgId;
    public String audioUrl;
    public int durationSec;
    public String title;
    public String subtitle;
    public long timestamp;

    public VoicePlaylistItem() {}

    public VoicePlaylistItem(String msgId, String audioUrl, int durationSec, String title, String subtitle) {
        this.msgId = msgId;
        this.audioUrl = audioUrl;
        this.durationSec = durationSec;
        this.title = title != null ? title : "";
        this.subtitle = subtitle != null ? subtitle : "";
        this.timestamp = System.currentTimeMillis();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VoicePlaylistItem that = (VoicePlaylistItem) o;
        return Objects.equals(msgId, that.msgId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(msgId);
    }
}

package com.nago8.chat.old.model;

import android.text.TextUtils;

import java.io.Serializable;

public class StickerItem implements Serializable {
    public static final String IMAGE_BASE_URL = "https://chat-img.jwznb.com/";

    public long id;
    public String name;
    public String url;
    public long stickerPackId;
    public String createBy;
    public long createTime;
    public int delFlag;

    public StickerItem() {
    }

    public StickerItem(long id, String name, String url, long stickerPackId) {
        this.id = id;
        this.name = name;
        this.url = url;
        this.stickerPackId = stickerPackId;
    }

    /**
     * 获取完整可直接加载的图片 URL
     */
    public String getFullUrl() {
        if (TextUtils.isEmpty(url)) return "";
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        if (url.startsWith("/")) {
            return IMAGE_BASE_URL + url.substring(1);
        }
        return IMAGE_BASE_URL + url;
    }
}

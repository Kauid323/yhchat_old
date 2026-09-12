package com.nago8.chat.old.model;

import android.text.TextUtils;
import java.io.Serializable;

public class Expression implements Serializable {
    public static final String IMAGE_BASE_URL = "https://chat-img.jwznb.com/";

    public long id;
    public String url;
    public String urlOriginal;
    public int delFlag;
    public long createTime;
    public String createBy;

    public Expression() {
    }

    public Expression(long id, String url) {
        this.id = id;
        this.url = url;
    }

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

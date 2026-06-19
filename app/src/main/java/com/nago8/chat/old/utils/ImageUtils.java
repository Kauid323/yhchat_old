package com.nago8.chat.old.utils;

import android.content.Context;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.nago8.chat.old.R;

public class ImageUtils {
    // 这个是用来请求获取缩略图用的参数，如果请求原图就别用这个
    private static final String AVATAR_RESIZE_PARAM = "imageView2/2/w/100/h/100";

    public static void loadAvatar(Context context, String url, ImageView imageView) {
        if (url == null || url.isEmpty()) {
            Glide.with(context).load(android.R.drawable.ic_menu_gallery).circleCrop().into(imageView);
            return;
        }

        // 智能处理裁切参数
        String finalUrl = url;
        if (finalUrl.contains("?")) {
            finalUrl = finalUrl + "&" + AVATAR_RESIZE_PARAM;
        } else {
            finalUrl = finalUrl + "?" + AVATAR_RESIZE_PARAM;
        }

        GlideUrl glideUrl;
        // 云湖数据床 Referer 校验严格，使用 http
        if (finalUrl.contains(".jwznb.com")) {
            glideUrl = new GlideUrl(finalUrl, new LazyHeaders.Builder()
                    .addHeader("Referer", "http://myapp.jwznb.com")
                    .build());
        } else {
            glideUrl = new GlideUrl(finalUrl);
        }

        Glide.with(context)
                .load(glideUrl)
                .placeholder(android.R.drawable.ic_menu_gallery) // 使用系统内置非矢量图占位，提高兼容性
                .error(android.R.drawable.ic_menu_report_image)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .circleCrop()
                .into(imageView);
    }
}
package com.nago8.chat.old.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.nago8.chat.old.cache.AvatarCache;

import java.io.File;

public class ImageUtils {
    private static final String AVATAR_RESIZE_PARAM = "imageView2/2/w/100/h/100";

    public static void loadAvatar(Context context, String url, ImageView imageView) {
        if (context == null || imageView == null) return;

        if (url == null || url.isEmpty()) {
            Glide.with(context)
                    .load(android.R.drawable.ic_menu_gallery)
                    .circleCrop()
                    .into(imageView);
            return;
        }

        String finalUrl = url.trim();

        // 兼容 Android 4.x (SDK < 21) 系统 SSL/TLS 协议低版本导致的 HTTPS 握手失败，转换为 HTTP 访问
        if (Build.VERSION.SDK_INT < 21 || finalUrl.contains(".jwznb.com")) {
            if (finalUrl.startsWith("https://")) {
                finalUrl = "http://" + finalUrl.substring(8);
            }
        }

        // 避免给默认头像添加 resize 参数 (兼容 default-avatars 和 defalut-avatars 拼写)
        if (!finalUrl.contains("/default-avatars/") && !finalUrl.contains("/defalut-avatars/")) {
            if (finalUrl.contains("?")) {
                finalUrl = finalUrl + "&" + AVATAR_RESIZE_PARAM;
            } else {
                finalUrl = finalUrl + "?" + AVATAR_RESIZE_PARAM;
            }
        }

        final String cacheKeyUrl = finalUrl;
        File cachedFile = AvatarCache.getAvatarFile(context, cacheKeyUrl);
        if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 0) {
            Glide.with(context)
                    .asBitmap()
                    .load(cachedFile)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_report_image)
                    .circleCrop()
                    .into(imageView);
            return;
        }

        GlideUrl glideUrl;
        if (finalUrl.contains(".jwznb.com")) {
            glideUrl = new GlideUrl(finalUrl, new LazyHeaders.Builder()
                    .addHeader("Referer", "http://myapp.jwznb.com")
                    .build());
        } else {
            glideUrl = new GlideUrl(finalUrl);
        }

        final String originalUrl = url;

        Glide.with(context)
                .asBitmap()
                .load(glideUrl)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .circleCrop()
                .listener(new RequestListener<Bitmap>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Bitmap> target, boolean isFirstResource) {
                        if (originalUrl != null && originalUrl.startsWith("https://") && model instanceof GlideUrl) {
                            final String httpUrl = "http://" + originalUrl.substring(8);
                            final GlideUrl retryUrl = new GlideUrl(httpUrl, new LazyHeaders.Builder()
                                    .addHeader("Referer", "http://myapp.jwznb.com")
                                    .build());
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                try {
                                    Glide.with(context)
                                            .asBitmap()
                                            .load(retryUrl)
                                            .placeholder(android.R.drawable.ic_menu_gallery)
                                            .error(android.R.drawable.ic_menu_report_image)
                                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                                            .circleCrop()
                                            .into(imageView);
                                } catch (Exception ignored) {}
                            });
                        }
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target, DataSource dataSource, boolean isFirstResource) {
                        if (resource != null) {
                            AvatarCache.saveAvatarCache(context, cacheKeyUrl, resource);
                        }
                        return false;
                    }
                })
                .into(imageView);
    }
}

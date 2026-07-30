package com.nago8.chat.old.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
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

        String finalUrl = url;
        if (!finalUrl.contains("/default-avatars/")) {
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

        Glide.with(context)
                .load(glideUrl)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .circleCrop()
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        if (resource instanceof BitmapDrawable) {
                            Bitmap bitmap = ((BitmapDrawable) resource).getBitmap();
                            AvatarCache.saveAvatarCache(context, cacheKeyUrl, bitmap);
                        }
                        return false;
                    }
                })
                .into(imageView);
    }
}

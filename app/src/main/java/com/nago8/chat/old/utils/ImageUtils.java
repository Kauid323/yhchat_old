package com.nago8.chat.old.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
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
import com.nago8.chat.old.R;
import com.nago8.chat.old.cache.AvatarCache;
import com.nago8.chat.old.net.ApiClient;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Request;
import okhttp3.Response;

public class ImageUtils {
    private static final String AVATAR_RESIZE_PARAM = "imageView2/2/w/100/h/100";
    public static final String PREF_KEY_AVATAR_LOAD_THREADS = "pref_avatar_load_threads";
    public static final int DEFAULT_AVATAR_LOAD_THREADS = 4;

    private static volatile ThreadPoolExecutor avatarExecutor;

    public static synchronized ThreadPoolExecutor getAvatarExecutor(Context context) {
        if (avatarExecutor == null || avatarExecutor.isShutdown()) {
            int threadCount = getAvatarLoadThreads(context);
            avatarExecutor = new ThreadPoolExecutor(
                    threadCount,
                    threadCount,
                    60L, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(),
                    new ThreadFactory() {
                        private final AtomicInteger count = new AtomicInteger(1);
                        @Override
                        public Thread newThread(Runnable r) {
                            return new Thread(r, "AvatarLoaderThread-" + count.getAndIncrement());
                        }
                    }
            );
            avatarExecutor.allowCoreThreadTimeOut(true);
        }
        return avatarExecutor;
    }

    public static int getAvatarLoadThreads(Context context) {
        if (context == null) return DEFAULT_AVATAR_LOAD_THREADS;
        android.content.SharedPreferences sp = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        int threads = sp.getInt(PREF_KEY_AVATAR_LOAD_THREADS, DEFAULT_AVATAR_LOAD_THREADS);
        return Math.max(1, threads); // 最低为1线程
    }

    public static void setAvatarLoadThreads(Context context, int threads) {
        if (threads < 1) threads = 1; // 最低为1线程
        if (context != null) {
            android.content.SharedPreferences sp = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
            sp.edit().putInt(PREF_KEY_AVATAR_LOAD_THREADS, threads).apply();
        }
        synchronized (ImageUtils.class) {
            if (avatarExecutor != null && !avatarExecutor.isShutdown()) {
                try {
                    int currentMax = avatarExecutor.getMaximumPoolSize();
                    if (threads > currentMax) {
                        avatarExecutor.setMaximumPoolSize(threads);
                        avatarExecutor.setCorePoolSize(threads);
                    } else {
                        avatarExecutor.setCorePoolSize(threads);
                        avatarExecutor.setMaximumPoolSize(threads);
                    }
                } catch (Exception e) {
                    avatarExecutor.shutdown();
                    avatarExecutor = null;
                }
            }
        }
    }

    public static void loadAvatar(Context context, String url, ImageView imageView) {
        if (context == null || imageView == null) return;

        if (url == null || url.trim().isEmpty()) {
            imageView.setTag(R.id.ivAvatar, null);
            Glide.with(context)
                    .load(android.R.drawable.ic_menu_gallery)
                    .circleCrop()
                    .into(imageView);
            return;
        }

        String trimmedUrl = url.trim();
        Object currentTag = imageView.getTag(R.id.ivAvatar);
        // 如果此 ImageView 已渲染当前 URL 的头像且已有图片 Drawable，避免重复加载引起界面闪烁
        if ((currentTag == trimmedUrl || (currentTag != null && currentTag.equals(trimmedUrl))) && imageView.getDrawable() != null) {
            return;
        }

        imageView.setTag(R.id.ivAvatar, trimmedUrl);

        String finalUrl = trimmedUrl;

        // 兼容 Android 4.x (SDK < 21) 系统 SSL/TLS 协议低版本导致的 HTTPS 握手失败
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
        final String rawUrl = trimmedUrl;

        // 优先同步快速检查本地文件缓存（毫秒级判断）
        File cachedFile = AvatarCache.getAvatarFile(context, cacheKeyUrl);
        Drawable existingDrawable = imageView.getDrawable();
        if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 0) {
            Glide.with(context)
                    .asBitmap()
                    .load(cachedFile)
                    .placeholder(existingDrawable != null ? existingDrawable : context.getResources().getDrawable(android.R.drawable.ic_menu_gallery))
                    .error(android.R.drawable.ic_menu_report_image)
                    .circleCrop()
                    .into(imageView);
            return;
        }

        // 未命中本地缓存时提交给专用线程池异步加载
        getAvatarExecutor(context.getApplicationContext()).execute(() -> {
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    GlideUrl glideUrl;
                    if (cacheKeyUrl.contains(".jwznb.com")) {
                        glideUrl = new GlideUrl(cacheKeyUrl, new LazyHeaders.Builder()
                                .addHeader("Referer", "http://myapp.jwznb.com")
                                .build());
                    } else {
                        glideUrl = new GlideUrl(cacheKeyUrl);
                    }

                    Drawable currentDrawable = imageView.getDrawable();
                    Glide.with(context)
                            .asBitmap()
                            .load(glideUrl)
                            .placeholder(currentDrawable != null ? currentDrawable : context.getResources().getDrawable(android.R.drawable.ic_menu_gallery))
                            .error(android.R.drawable.ic_menu_report_image)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .circleCrop()
                            .listener(new RequestListener<Bitmap>() {
                                @Override
                                public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Bitmap> target, boolean isFirstResource) {
                                    fetchAvatarWithOkHttp(context, cacheKeyUrl, rawUrl, imageView);
                                    return false;
                                }

                                @Override
                                public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target, DataSource dataSource, boolean isFirstResource) {
                                    if (resource != null) {
                                        getAvatarExecutor(context.getApplicationContext()).execute(() ->
                                                AvatarCache.saveAvatarCache(context, cacheKeyUrl, resource));
                                    }
                                    return false;
                                }
                            })
                            .into(imageView);
                } catch (Exception ignored) {}
            });
        });
    }

    private static void fetchAvatarWithOkHttp(Context context, String cacheKeyUrl, String targetUrl, ImageView imageView) {
        if (context == null || imageView == null || targetUrl == null || targetUrl.trim().isEmpty()) return;
        getAvatarExecutor(context.getApplicationContext()).execute(() -> {
            try {
                Request request = new Request.Builder()
                        .url(targetUrl)
                        .header("Referer", "http://myapp.jwznb.com")
                        .build();
                Response response = ApiClient.getClient().newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    byte[] bytes = response.body().bytes();
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (bitmap != null) {
                        AvatarCache.saveAvatarCache(context, cacheKeyUrl, bitmap);
                        new Handler(Looper.getMainLooper()).post(() -> {
                            try {
                                Drawable currentDrawable = imageView.getDrawable();
                                Glide.with(context)
                                        .asBitmap()
                                        .load(bitmap)
                                        .placeholder(currentDrawable != null ? currentDrawable : context.getResources().getDrawable(android.R.drawable.ic_menu_gallery))
                                        .error(android.R.drawable.ic_menu_report_image)
                                        .circleCrop()
                                        .into(imageView);
                            } catch (Exception ignored) {}
                        });
                    }
                }
            } catch (Exception ignored) {}
        });
    }
}

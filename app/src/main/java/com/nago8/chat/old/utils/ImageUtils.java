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
import com.nago8.chat.old.cache.StickerCache;
import com.nago8.chat.old.net.ApiClient;

import java.io.File;

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

    /**
     * 为七牛云图片 URL 动态拼接 imageView2 采样/缩放/压缩参数
     * 例如: imageView2/2/w/200/h/200/q/75
     */
    public static String appendQiniuParam(String url, int width, int height) {
        if (url == null || url.trim().isEmpty()) return "";
        String trimmed = url.trim();
        if (width <= 0 && height <= 0) return trimmed;

        // 如果已经包含 imageView2 参数，不再重复拼接
        if (trimmed.contains("imageView2/")) return trimmed;

        // 避开默认头像路径
        if (trimmed.contains("/default-avatars/") || trimmed.contains("/defalut-avatars/")) {
            return trimmed;
        }

        // 避开不支持七牛图片缩放处理的后缀格式（如 .tmp、.gif、.svg、.mp4 等非静态位图格式）
        String lower = trimmed.toLowerCase();
        String pathPart = lower.contains("?") ? lower.substring(0, lower.indexOf('?')) : lower;
        if (pathPart.endsWith(".tmp") || pathPart.endsWith(".gif") || pathPart.endsWith(".svg")
                || pathPart.endsWith(".mp4") || pathPart.endsWith(".mp3") || pathPart.endsWith(".wav")
                || pathPart.endsWith(".apk") || pathPart.endsWith(".zip") || pathPart.endsWith(".pdf")
                || pathPart.endsWith(".doc") || pathPart.endsWith(".docx")) {
            return trimmed;
        }

        StringBuilder param = new StringBuilder("imageView2/2");
        if (width > 0) param.append("/w/").append(width);
        if (height > 0) param.append("/h/").append(height);
        param.append("/q/75");

        if (trimmed.contains("?")) {
            if (trimmed.endsWith("?") || trimmed.endsWith("&")) {
                return trimmed + param.toString();
            } else {
                return trimmed + "&" + param.toString();
            }
        } else {
            return trimmed + "?" + param.toString();
        }
    }

    public static void loadImage(Context context, String url, ImageView imageView, int reqWidth, int reqHeight) {
        if (context == null || imageView == null) return;
        if (url == null || url.trim().isEmpty()) {
            imageView.setImageDrawable(null);
            return;
        }

        String finalUrl = appendQiniuParam(url.trim(), reqWidth, reqHeight);
        if (Build.VERSION.SDK_INT < 21 && finalUrl.startsWith("https://")) {
            finalUrl = "http://" + finalUrl.substring(8);
        }

        try {
            GlideUrl glideUrl;
            if (finalUrl.contains(".jwznb.com")) {
                glideUrl = new GlideUrl(finalUrl, new LazyHeaders.Builder()
                        .addHeader("Referer", "https://myapp.jwznb.com")
                        .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
                        .build());
            } else {
                glideUrl = new GlideUrl(finalUrl);
            }

            Glide.with(context)
                    .load(glideUrl)
                    .override(reqWidth > 0 ? reqWidth : Target.SIZE_ORIGINAL, reqHeight > 0 ? reqHeight : Target.SIZE_ORIGINAL)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(imageView);
        } catch (Exception ignored) {}
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

        String finalUrl = appendQiniuParam(trimmedUrl, 120, 120);

        // 兼容 Android 4.x (SDK < 21) 系统 SSL/TLS 协议低版本导致的 HTTPS 握手失败
        if (Build.VERSION.SDK_INT < 21 && finalUrl.startsWith("https://")) {
            finalUrl = "http://" + finalUrl.substring(8);
        }

        final String cacheKeyUrl = finalUrl;
        final String rawUrl = trimmedUrl;

        try {
            GlideUrl glideUrl;
            if (cacheKeyUrl.contains(".jwznb.com")) {
                glideUrl = new GlideUrl(cacheKeyUrl, new LazyHeaders.Builder()
                        .addHeader("Referer", "https://myapp.jwznb.com")
                        .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
                        .build());
            } else {
                glideUrl = new GlideUrl(cacheKeyUrl);
            }

            Glide.with(context)
                    .asBitmap()
                    .load(glideUrl)
                    .override(120, 120)
                    .placeholder(android.R.drawable.ic_menu_gallery)
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
                            return false;
                        }
                    })
                    .into(imageView);
        } catch (Exception e) {
            fetchAvatarWithOkHttp(context, cacheKeyUrl, rawUrl, imageView);
        }
    }

    private static void fetchAvatarWithOkHttp(Context context, String cacheKeyUrl, String targetUrl, ImageView imageView) {
        if (context == null || imageView == null || targetUrl == null || targetUrl.trim().isEmpty()) return;
        getAvatarExecutor(context.getApplicationContext()).execute(() -> {
            try {
                String primaryUrl = (targetUrl.startsWith("http://") || targetUrl.startsWith("https://")) ? targetUrl : cacheKeyUrl;
                Request request = new Request.Builder()
                        .url(primaryUrl)
                        .header("Referer", "https://myapp.jwznb.com")
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
                        .build();
                Response response = ApiClient.getClient().newCall(request).execute();
                if (!response.isSuccessful() && !primaryUrl.equals(cacheKeyUrl)) {
                    request = new Request.Builder()
                            .url(cacheKeyUrl)
                            .header("Referer", "https://myapp.jwznb.com")
                            .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
                            .build();
                    response = ApiClient.getClient().newCall(request).execute();
                }
                if (response.isSuccessful() && response.body() != null) {
                    byte[] bytes = response.body().bytes();
                    if (bytes != null && bytes.length > 64) {
                        new Handler(Looper.getMainLooper()).post(() -> {
                            try {
                                Object checkTag = imageView.getTag(R.id.ivAvatar);
                                if (checkTag != null && checkTag.equals(targetUrl)) {
                                    Glide.with(context)
                                            .asBitmap()
                                            .load(bytes)
                                            .override(120, 120)
                                            .placeholder(android.R.drawable.ic_menu_gallery)
                                            .error(android.R.drawable.ic_menu_report_image)
                                            .circleCrop()
                                            .into(imageView);
                                }
                            } catch (Exception ignored) {}
                        });
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    public static void loadSticker(Context context, String url, ImageView imageView) {
        loadSticker(context, url, imageView, 0, 0);
    }

    public static void loadSticker(Context context, String url, ImageView imageView, int reqWidth, int reqHeight) {
        if (context == null || imageView == null) return;
        if (url == null || url.trim().isEmpty()) {
            imageView.setTag(R.id.ivSticker, null);
            imageView.setImageDrawable(null);
            return;
        }

        String trimmedUrl = url.trim();
        if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
            if (trimmedUrl.startsWith("/")) {
                trimmedUrl = "https://chat-img.jwznb.com" + trimmedUrl;
            } else {
                trimmedUrl = "https://chat-img.jwznb.com/" + trimmedUrl;
            }
        }

        int targetWidth = reqWidth > 0 ? reqWidth : 120;
        int targetHeight = reqHeight > 0 ? reqHeight : 120;
        String finalUrl = appendQiniuParam(trimmedUrl, targetWidth, targetHeight);
        if (Build.VERSION.SDK_INT < 21 && finalUrl.startsWith("https://")) {
            finalUrl = "http://" + finalUrl.substring(8);
        }

        final String cacheKeyUrl = finalUrl;
        final String rawUrl = trimmedUrl;

        Object currentTag = imageView.getTag(R.id.ivSticker);
        if (rawUrl.equals(currentTag) && imageView.getDrawable() != null) {
            return;
        }
        imageView.setTag(R.id.ivSticker, rawUrl);

        // 直接使用 Glide 异步磁盘与内存缓存调度（命中内存缓存时 0 延时瞬时渲染，无需主线程磁盘I/O）
        try {
            GlideUrl glideUrl = new GlideUrl(cacheKeyUrl, new LazyHeaders.Builder()
                    .addHeader("Referer", "https://myapp.jwznb.com")
                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
                    .build());

            Glide.with(context)
                    .load(glideUrl)
                    .override(targetWidth, targetHeight)
                    .placeholder(R.drawable.ic_image)
                    .error(R.drawable.ic_image)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .dontAnimate()
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            fetchStickerWithOkHttp(context, cacheKeyUrl, rawUrl, imageView, targetWidth, targetHeight);
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            return false;
                        }
                    })
                    .into(imageView);
        } catch (Exception e) {
            fetchStickerWithOkHttp(context, cacheKeyUrl, rawUrl, imageView, targetWidth, targetHeight);
        }
    }

    private static void fetchStickerWithOkHttp(Context context, String cacheKeyUrl, String targetUrl, ImageView imageView, int targetWidth, int targetHeight) {
        if (context == null || imageView == null || targetUrl == null || targetUrl.trim().isEmpty()) return;
        getAvatarExecutor(context.getApplicationContext()).execute(() -> {
            try {
                String primaryUrl = (targetUrl.startsWith("http://") || targetUrl.startsWith("https://")) ? targetUrl : cacheKeyUrl;
                Request request = new Request.Builder()
                        .url(primaryUrl)
                        .header("Referer", "https://myapp.jwznb.com")
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
                        .build();
                Response response = ApiClient.getClient().newCall(request).execute();
                if (!response.isSuccessful() && !primaryUrl.equals(cacheKeyUrl)) {
                    request = new Request.Builder()
                            .url(cacheKeyUrl)
                            .header("Referer", "https://myapp.jwznb.com")
                            .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
                            .build();
                    response = ApiClient.getClient().newCall(request).execute();
                }
                if (response.isSuccessful() && response.body() != null) {
                    byte[] bytes = response.body().bytes();
                    if (bytes != null && bytes.length > 64) {
                        String head = new String(bytes, 0, Math.min(bytes.length, 64)).toLowerCase();
                        if (head.contains("html") || head.contains("xml") || head.contains("error") || head.contains("denied")) {
                            return;
                        }
                        StickerCache.saveStickerBytes(context, cacheKeyUrl, bytes);
                        new Handler(Looper.getMainLooper()).post(() -> {
                            try {
                                Object checkTag = imageView.getTag(R.id.ivSticker);
                                if (checkTag != null && checkTag.equals(targetUrl)) {
                                    Glide.with(context)
                                            .load(bytes)
                                            .override(targetWidth, targetHeight)
                                            .placeholder(R.drawable.ic_image)
                                            .error(R.drawable.ic_image)
                                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                                            .into(imageView);
                                }
                            } catch (Exception ignored) {}
                        });
                    }
                }
            } catch (Exception ignored) {}
        });
    }
}

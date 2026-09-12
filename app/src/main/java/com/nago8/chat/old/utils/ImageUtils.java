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
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.nago8.chat.old.R;
import com.nago8.chat.old.cache.AvatarCache;
import com.nago8.chat.old.cache.StickerCache;
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

        StringBuilder param = new StringBuilder("imageView2/2");
        if (width > 0) param.append("/w/").append(width);
        if (height > 0) param.append("/h/").append(height);
        param.append("/q/75");

        if (trimmed.contains("?")) {
            return trimmed + "&" + param.toString();
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

        // 优先同步快速检查本地文件缓存（毫秒级判断）
        File cachedFile = AvatarCache.getAvatarFile(context, cacheKeyUrl);
        if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 0) {
            Glide.with(context)
                    .asBitmap()
                    .load(cachedFile)
                    .override(120, 120)
                    .placeholder(android.R.drawable.ic_menu_gallery)
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
                        .header("Referer", "https://myapp.jwznb.com")
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
                        .build();
                Response response = ApiClient.getClient().newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    byte[] bytes = response.body().bytes();
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (bitmap != null) {
                        AvatarCache.saveAvatarCache(context, cacheKeyUrl, bitmap);
                        new Handler(Looper.getMainLooper()).post(() -> {
                            try {
                                Glide.with(context)
                                        .asBitmap()
                                        .load(bitmap)
                                        .placeholder(android.R.drawable.ic_menu_gallery)
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
        Object currentTag = imageView.getTag(R.id.ivSticker);
        if ((currentTag == trimmedUrl || (currentTag != null && currentTag.equals(trimmedUrl))) && imageView.getDrawable() != null) {
            return;
        }
        imageView.setTag(R.id.ivSticker, trimmedUrl);

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

        // 重用 View 时重置当前 Drawable，防止之前的 GifDrawable 在被 Glide 回收后继续被 Canvas 绘制导致 isRecycled() 空指针崩溃
        imageView.setImageResource(R.drawable.ic_image);

        // 统一通过设置中配置的专用线程池按需、逐个异步排队渲染，避免主线程一次性批量解码造成卡顿
        getAvatarExecutor(context.getApplicationContext()).execute(() -> {
            // 1. 检查本地磁盘缓存
            File cachedFile = StickerCache.getStickerFile(context, cacheKeyUrl);
            if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 0) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    try {
                        Object checkTag = imageView.getTag(R.id.ivSticker);
                        if (checkTag != null && checkTag.equals(rawUrl)) {
                            Glide.with(context)
                                    .load(cachedFile)
                                    .override(targetWidth, targetHeight)
                                    .placeholder(R.drawable.ic_image)
                                    .error(R.drawable.ic_image)
                                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                                    .into(imageView);
                        }
                    } catch (Exception ignored) {}
                });
                return;
            }

            // 2. 未命中缓存，通过支持 TLS 1.2 的 OkHttp 后台拉取并回填
            try {
                Request request = new Request.Builder()
                        .url(cacheKeyUrl)
                        .header("Referer", "https://myapp.jwznb.com")
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
                        .build();
                Response response = ApiClient.getClient().newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    byte[] bytes = response.body().bytes();
                    if (bytes != null && bytes.length > 0) {
                        StickerCache.saveStickerBytes(context, cacheKeyUrl, bytes);
                        File freshlySaved = StickerCache.getStickerFile(context, cacheKeyUrl);
                        new Handler(Looper.getMainLooper()).post(() -> {
                            try {
                                Object checkTag = imageView.getTag(R.id.ivSticker);
                                if (checkTag != null && checkTag.equals(rawUrl)) {
                                    Glide.with(context)
                                            .load(freshlySaved != null && freshlySaved.exists() ? freshlySaved : bytes)
                                            .override(targetWidth, targetHeight)
                                            .placeholder(R.drawable.ic_image)
                                            .error(R.drawable.ic_image)
                                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                                            .into(imageView);
                                }
                            } catch (Exception ignored) {}
                        });
                        return;
                    }
                }
            } catch (Exception ignored) {}

            // 3. OkHttp 异常兜底（如 404 等）：通过带 Header 的 Glide 后台加载，失败时安全显示 error 图标
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    Object checkTag = imageView.getTag(R.id.ivSticker);
                    if (checkTag != null && checkTag.equals(rawUrl)) {
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
                                .into(imageView);
                    }
                } catch (Exception ignored) {}
            });
        });
    }
}

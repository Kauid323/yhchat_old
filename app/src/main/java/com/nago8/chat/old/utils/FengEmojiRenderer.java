package com.nago8.chat.old.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Picture;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ImageSpan;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.caverock.androidsvg.SVG;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FengEmojiRenderer {

    private static final String ASSET_DIR = "fengtwemoji";
    private static final Pattern EMOJI_PATTERN = Pattern.compile("\\[\\.[^\\[\\]\\r\\n]+\\]");
    private static final Object LOCK = new Object();
    private static volatile Set<String> emojiNames;
    private static final LruCache<String, Bitmap> BITMAP_CACHE = new LruCache<>(512);

    private FengEmojiRenderer() {
    }

    /**
     * 预热 Twemoji 渲染缓存（在后台线程预加载常用 Twemoji SVG）
     */
    public static void prewarmCache(@NonNull Context context, int sizePx) {
        new Thread(() -> {
            try {
                ensureEmojiNamesLoaded(context);
                if (emojiNames != null) {
                    for (String token : emojiNames) {
                        getBitmap(context, token, sizePx);
                    }
                }
            } catch (Exception ignored) {}
        }, "TwemojiPrewarmer").start();
    }

    @Nullable
    public static Bitmap getBitmap(@NonNull Context context, @NonNull String token, int sizePx) {
        String cacheKey = token + "_" + sizePx;
        Bitmap cached = BITMAP_CACHE.get(cacheKey);
        if (cached != null && !cached.isRecycled()) {
            return cached;
        }

        InputStream inputStream = null;
        try {
            inputStream = context.getAssets().open(ASSET_DIR + "/" + token + ".svg");
            SVG svg = SVG.getFromInputStream(inputStream);
            Picture picture = svg.renderToPicture(sizePx, sizePx);
            Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            picture.draw(canvas);
            BITMAP_CACHE.put(cacheKey, bitmap);
            return bitmap;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ignored) {}
            }
        }
    }

    @Nullable
    public static Drawable getDrawable(@NonNull Context context, @NonNull String token, int sizePx) {
        Bitmap bitmap = getBitmap(context, token, sizePx);
        if (bitmap != null) {
            BitmapDrawable drawable = new BitmapDrawable(context.getResources(), bitmap);
            drawable.setBounds(0, 0, sizePx, sizePx);
            return drawable;
        }
        return null;
    }

    @NonNull
    public static CharSequence apply(@NonNull Context context, CharSequence source, int sizePx) {
        if (source == null || source.length() < 3) {
            return source;
        }
        String str = source.toString();
        if (!str.contains("[.")) {
            return source;
        }
        ensureEmojiNamesLoaded(context);

        Matcher matcher = EMOJI_PATTERN.matcher(source);
        SpannableStringBuilder builder = null;
        while (matcher.find()) {
            String token = matcher.group();
            if (token == null) continue;
            if (emojiNames != null && !emojiNames.isEmpty() && !emojiNames.contains(token)) {
                continue;
            }
            Drawable drawable = getDrawable(context, token, sizePx);
            if (drawable == null) {
                continue;
            }
            if (builder == null) {
                builder = new SpannableStringBuilder(source);
            }
            builder.setSpan(new CenteredImageSpan(drawable), matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return builder != null ? builder : source;
    }

    private static void ensureEmojiNamesLoaded(@NonNull Context context) {
        if (emojiNames != null) {
            return;
        }
        synchronized (LOCK) {
            if (emojiNames != null) {
                return;
            }
            emojiNames = new HashSet<>();
        }
        Context appContext = context.getApplicationContext();
        new Thread(() -> {
            Set<String> names = new HashSet<>();
            try {
                String[] files = appContext.getAssets().list(ASSET_DIR);
                if (files != null) {
                    for (String file : files) {
                        if (file == null || !file.toLowerCase(Locale.ROOT).endsWith(".svg")) {
                            continue;
                        }
                        int dot = file.lastIndexOf('.');
                        if (dot > 0) {
                            names.add(file.substring(0, dot));
                        }
                    }
                }
            } catch (IOException ignored) {
            }
            synchronized (LOCK) {
                emojiNames = Collections.unmodifiableSet(names);
            }
        }, "TwemojiNameLoader").start();
    }

    private static class CenteredImageSpan extends ImageSpan {
        CenteredImageSpan(@NonNull Drawable drawable) {
            super(drawable, DynamicDrawableSpan.ALIGN_BOTTOM);
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, android.graphics.Paint paint) {
            Drawable drawable = getDrawable();
            canvas.save();
            int transY = bottom - drawable.getBounds().bottom - paint.getFontMetricsInt().descent / 2;
            canvas.translate(x, transY);
            drawable.draw(canvas);
            canvas.restore();
        }
    }
}

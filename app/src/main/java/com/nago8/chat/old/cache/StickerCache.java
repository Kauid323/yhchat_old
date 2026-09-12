package com.nago8.chat.old.cache;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;

public class StickerCache {
    private static final String TAG = "StickerCache";
    private static final String DIR_STICKER_CACHE = "sticker_cache";

    public static File getStickerDir(Context context) {
        if (context == null) return null;
        File dir = new File(context.getCacheDir(), DIR_STICKER_CACHE);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static File getStickerFile(Context context, String url) {
        if (context == null || url == null || url.isEmpty()) return null;
        File dir = getStickerDir(context);
        if (dir == null) return null;
        String fileName = md5(url);
        return new File(dir, fileName);
    }

    public static void saveStickerBytes(Context context, String url, byte[] data) {
        if (context == null || url == null || data == null || data.length < 64) return;
        
        // 过滤非图片错误响应内容 (如 403 Forbidden / 404 Not Found / HTML / XML 报错)
        String head = new String(data, 0, Math.min(data.length, 64)).toLowerCase();
        if (head.contains("html") || head.contains("xml") || head.contains("error") || head.contains("denied")) {
            Log.w(TAG, "saveStickerBytes: skipped invalid non-image content for URL: " + url + ", head=" + head);
            return;
        }

        File file = getStickerFile(context, url);
        if (file == null) return;

        new Thread(() -> {
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(file);
                fos.write(data);
                fos.flush();
                Log.d(TAG, "Sticker bytes cached to disk: " + file.getAbsolutePath());
            } catch (Exception e) {
                Log.e(TAG, "saveStickerBytes failed", e);
                try {
                    if (file.exists()) {
                        file.delete();
                    }
                } catch (Exception ignored) {}
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (Exception ignored) {}
                }
            }
        }).start();
    }

    public static void saveStickerBitmap(Context context, String url, Bitmap bitmap) {
        if (context == null || url == null || bitmap == null) return;
        File file = getStickerFile(context, url);
        if (file == null) return;

        new Thread(() -> {
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(file);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.flush();
                Log.d(TAG, "Sticker bitmap cached to disk: " + file.getAbsolutePath());
            } catch (Exception e) {
                Log.e(TAG, "saveStickerBitmap failed", e);
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (Exception ignored) {}
                }
            }
        }).start();
    }

    public static void clearCache(Context context) {
        if (context == null) return;
        File dir = getStickerDir(context);
        if (dir != null && dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f != null && f.exists()) {
                        f.delete();
                    }
                }
            }
        }
    }

    public static long getCacheSize(Context context) {
        if (context == null) return 0;
        File dir = getStickerDir(context);
        long size = 0;
        if (dir != null && dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f != null && f.exists()) {
                        size += f.length();
                    }
                }
            }
        }
        return size;
    }

    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();

    private static String md5(String string) {
        if (string == null || string.isEmpty()) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(string.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            char[] hexChars = new char[bytes.length * 2];
            for (int i = 0; i < bytes.length; i++) {
                int v = bytes[i] & 0xFF;
                hexChars[i * 2] = HEX_ARRAY[v >>> 4];
                hexChars[i * 2 + 1] = HEX_ARRAY[v & 0x0F];
            }
            return new String(hexChars);
        } catch (Exception e) {
            return String.valueOf(string.hashCode());
        }
    }
}

package com.nago8.chat.old.cache;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;

public class AvatarCache {
    private static final String TAG = "AvatarCache";
    private static final String DIR_AVATAR_CACHE = "avatar_cache";

    public static File getAvatarDir(Context context) {
        if (context == null) return null;
        File dir = new File(context.getCacheDir(), DIR_AVATAR_CACHE);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static File getAvatarFile(Context context, String url) {
        if (context == null || url == null || url.isEmpty()) return null;
        File dir = getAvatarDir(context);
        if (dir == null) return null;
        String fileName = md5(url) + ".png";
        return new File(dir, fileName);
    }

    public static void saveAvatarCache(Context context, String url, Bitmap bitmap) {
        if (context == null || url == null || bitmap == null) return;
        File file = getAvatarFile(context, url);
        if (file == null) return;

        new Thread(() -> {
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(file);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.flush();
                Log.d(TAG, "Avatar cached to disk: " + file.getAbsolutePath());
            } catch (Exception e) {
                Log.e(TAG, "saveAvatarCache failed", e);
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
        File dir = getAvatarDir(context);
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
        File dir = getAvatarDir(context);
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

    private static String md5(String string) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(string.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(string.hashCode());
        }
    }
}

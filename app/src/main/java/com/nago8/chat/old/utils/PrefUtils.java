package com.nago8.chat.old.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class PrefUtils {
    private static final String PREF_NAME = "user_prefs";
    private static final String KEY_TOKEN = "secure_token";

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static void saveToken(Context context, String token) {
        try {
            String encrypted = CryptoUtils.encrypt(token);
            getPrefs(context).edit().putString(KEY_TOKEN, encrypted).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getToken(Context context) {
        try {
            String encrypted = getPrefs(context).getString(KEY_TOKEN, null);
            if (encrypted != null) {
                return CryptoUtils.decrypt(encrypted);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void clearToken(Context context) {
        getPrefs(context).edit().remove(KEY_TOKEN).apply();
    }
}
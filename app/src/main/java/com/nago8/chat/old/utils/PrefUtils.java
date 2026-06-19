package com.nago8.chat.old.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class PrefUtils {
    private static final String PREF_NAME = "user_prefs";
    private static final String KEY_TOKEN = "secure_token";
    private static final String KEY_LANGUAGE = "app_language";
    public static final String LANG_SYSTEM = "system";
    public static final String LANG_ZH = "zh";
    public static final String LANG_EN = "en";

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

    public static String getLanguage(Context context) {
        return getPrefs(context).getString(KEY_LANGUAGE, LANG_SYSTEM);
    }

    public static void setLanguage(Context context, String language) {
        getPrefs(context).edit().putString(KEY_LANGUAGE, language).apply();
    }
}

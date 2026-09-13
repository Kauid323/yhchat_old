package com.nago8.chat.old.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class PrefUtils {
    private static final String PREF_NAME = "user_prefs";
    private static final String KEY_TOKEN = "secure_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_IS_VIP = "is_vip";
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

    public static void saveUserId(Context context, String userId) {
        getPrefs(context).edit().putString(KEY_USER_ID, userId).apply();
    }

    public static String getUserId(Context context) {
        return getPrefs(context).getString(KEY_USER_ID, "");
    }

    public static void saveIsVip(Context context, boolean isVip) {
        getPrefs(context).edit().putBoolean(KEY_IS_VIP, isVip).apply();
    }

    public static boolean isVip(Context context) {
        return getPrefs(context).getBoolean(KEY_IS_VIP, false);
    }

    private static final String KEY_DARK_MODE_ENABLED = "dark_mode_enabled";

    public static boolean isDarkModeEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_DARK_MODE_ENABLED, false);
    }

    public static void setDarkModeEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_DARK_MODE_ENABLED, enabled).apply();
    }

    public static void applyDarkMode(Context context) {
        boolean enabled = isDarkModeEnabled(context);
        int mode = enabled ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode);
    }

    public static final String EMOJI_TAB_UNICODE = "unicode";
    public static final String EMOJI_TAB_TWEMOJI = "twemoji";
    public static final String EMOJI_TAB_FAVORITE = "favorite";
    public static final String EMOJI_TAB_PACK = "pack";

    private static final String KEY_EMOJI_DEFAULT_TAB_TYPE = "emoji_default_tab_type";
    private static final String KEY_EMOJI_DEFAULT_PACK_ID = "emoji_default_pack_id";
    private static final String KEY_EMOJI_DEFAULT_PACK_NAME = "emoji_default_pack_name";
    private static final String KEY_EMOJI_RANDOM_PACK_ENABLED = "emoji_random_pack_enabled";

    public static String getEmojiDefaultTabType(Context context) {
        return getPrefs(context).getString(KEY_EMOJI_DEFAULT_TAB_TYPE, EMOJI_TAB_UNICODE);
    }

    public static void setEmojiDefaultTabType(Context context, String tabType) {
        getPrefs(context).edit().putString(KEY_EMOJI_DEFAULT_TAB_TYPE, tabType).apply();
    }

    public static long getEmojiDefaultPackId(Context context) {
        return getPrefs(context).getLong(KEY_EMOJI_DEFAULT_PACK_ID, 0L);
    }

    public static void setEmojiDefaultPackId(Context context, long packId) {
        getPrefs(context).edit().putLong(KEY_EMOJI_DEFAULT_PACK_ID, packId).apply();
    }

    public static String getEmojiDefaultPackName(Context context) {
        return getPrefs(context).getString(KEY_EMOJI_DEFAULT_PACK_NAME, "");
    }

    public static void setEmojiDefaultPackName(Context context, String packName) {
        getPrefs(context).edit().putString(KEY_EMOJI_DEFAULT_PACK_NAME, packName).apply();
    }

    public static boolean isEmojiRandomPackEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_EMOJI_RANDOM_PACK_ENABLED, false);
    }

    public static void setEmojiRandomPackEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_EMOJI_RANDOM_PACK_ENABLED, enabled).apply();
    }

    private static final String KEY_DISABLE_HTML_IMAGE_PRELOAD = "disable_html_image_preload";

    public static boolean isDisableHtmlImagePreload(Context context) {
        return getPrefs(context).getBoolean(KEY_DISABLE_HTML_IMAGE_PRELOAD, false);
    }

    public static void setDisableHtmlImagePreload(Context context, boolean disable) {
        getPrefs(context).edit().putBoolean(KEY_DISABLE_HTML_IMAGE_PRELOAD, disable).apply();
    }

    private static final String KEY_SHOW_RAW_HTML = "show_raw_html";

    public static boolean isShowRawHtml(Context context) {
        return getPrefs(context).getBoolean(KEY_SHOW_RAW_HTML, false);
    }

    public static void setShowRawHtml(Context context, boolean showRaw) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_RAW_HTML, showRaw).apply();
    }
}

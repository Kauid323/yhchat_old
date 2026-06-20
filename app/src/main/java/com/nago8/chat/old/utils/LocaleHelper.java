package com.nago8.chat.old.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.DisplayMetrics;

import java.util.Locale;

public class LocaleHelper {

    public static Context wrap(Context context) {
        Locale locale = getLocale(context);

        Locale.setDefault(locale);

        Resources res = context.getResources();
        Configuration config = new Configuration(res.getConfiguration());
        config.locale = locale;
        DisplayMetrics dm = res.getDisplayMetrics();
        res.updateConfiguration(config, dm);

        return context;
    }

    public static void applyToApplication(Context appContext) {
        Locale locale = getLocale(appContext);

        Locale.setDefault(locale);

        Resources res = appContext.getResources();
        Configuration config = new Configuration(res.getConfiguration());
        config.locale = locale;
        DisplayMetrics dm = res.getDisplayMetrics();
        res.updateConfiguration(config, dm);
    }

    /**
     * 根据保存的语言偏好获取 Locale。
     * - LANG_EN → English
     * - LANG_ZH → 简体中文
     * - LANG_SYSTEM → 系统当前 Locale
     */
    private static Locale getLocale(Context context) {
        String lang = PrefUtils.getLanguage(context);
        if (PrefUtils.LANG_EN.equals(lang)) {
            return Locale.ENGLISH;
        } else if (PrefUtils.LANG_ZH.equals(lang)) {
            return Locale.SIMPLIFIED_CHINESE;
        } else {
            // 跟随系统：用系统当前 locale，而非之前可能被覆盖的 Application locale
            return Resources.getSystem().getConfiguration().locale;
        }
    }
}

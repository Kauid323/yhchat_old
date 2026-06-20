package com.nago8.chat.old.utils;

import android.os.Build;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.DisplayMetrics;

import java.util.Locale;

public class LocaleHelper {

    public static Context wrap(Context context) {
        Locale locale = getLocale(context);

        Locale.setDefault(locale);

        // Android 17+ 用 createConfigurationContext（高版本 updateConfiguration 已废弃）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            Configuration config = new Configuration(context.getResources().getConfiguration());
            config.setLocale(locale);
            return context.createConfigurationContext(config);
        } else {
            // Android 14-16 用 updateConfiguration
            Resources res = context.getResources();
            Configuration config = new Configuration(res.getConfiguration());
            config.locale = locale;
            DisplayMetrics dm = res.getDisplayMetrics();
            res.updateConfiguration(config, dm);
            return context;
        }
    }

    public static void applyToApplication(Context appContext) {
        Locale locale = getLocale(appContext);

        Locale.setDefault(locale);

        // Android 17+ 用 createConfigurationContext 更新 Application 资源
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            Configuration config = new Configuration(appContext.getResources().getConfiguration());
            config.setLocale(locale);
            appContext.getResources().updateConfiguration(config,
                    appContext.getResources().getDisplayMetrics());
        } else {
            Resources res = appContext.getResources();
            Configuration config = new Configuration(res.getConfiguration());
            config.locale = locale;
            DisplayMetrics dm = res.getDisplayMetrics();
            res.updateConfiguration(config, dm);
        }
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

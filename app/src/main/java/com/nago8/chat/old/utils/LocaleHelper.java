package com.nago8.chat.old.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.DisplayMetrics;

import java.util.Locale;

public class LocaleHelper {

    public static Context wrap(Context context) {
        String lang = PrefUtils.getLanguage(context);
        Locale locale;
        if (PrefUtils.LANG_EN.equals(lang)) {
            locale = Locale.ENGLISH;
        } else if (PrefUtils.LANG_ZH.equals(lang)) {
            locale = Locale.SIMPLIFIED_CHINESE;
        } else {
            return context;
        }

        Locale.setDefault(locale);

        Resources res = context.getResources();
        Configuration config = new Configuration(res.getConfiguration());
        config.locale = locale;
        DisplayMetrics dm = res.getDisplayMetrics();
        res.updateConfiguration(config, dm);

        return context;
    }

    public static void applyToApplication(Context appContext) {
        String lang = PrefUtils.getLanguage(appContext);
        Locale locale;
        if (PrefUtils.LANG_EN.equals(lang)) {
            locale = Locale.ENGLISH;
        } else if (PrefUtils.LANG_ZH.equals(lang)) {
            locale = Locale.SIMPLIFIED_CHINESE;
        } else {
            locale = Resources.getSystem().getConfiguration().locale;
        }

        Locale.setDefault(locale);

        Resources res = appContext.getResources();
        Configuration config = new Configuration(res.getConfiguration());
        config.locale = locale;
        DisplayMetrics dm = res.getDisplayMetrics();
        res.updateConfiguration(config, dm);
    }
}

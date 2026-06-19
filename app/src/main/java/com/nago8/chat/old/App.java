package com.nago8.chat.old;

import android.app.Application;
import android.content.Context;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.DisplayMetrics;

import com.nago8.chat.old.utils.PrefUtils;

import java.util.Locale;

public class App extends Application {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        applyLanguage(this);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyLanguage(this);
    }

    public static void applyLanguage(Context context) {
        String lang = PrefUtils.getLanguage(context);
        Locale locale;
        if (PrefUtils.LANG_EN.equals(lang)) {
            locale = Locale.ENGLISH;
        } else if (PrefUtils.LANG_ZH.equals(lang)) {
            locale = Locale.SIMPLIFIED_CHINESE;
        } else {
            locale = Resources.getSystem().getConfiguration().locale;
        }

        Locale.setDefault(locale);

        Resources res = context.getResources();
        Configuration config = new Configuration(res.getConfiguration());
        config.locale = locale;
        DisplayMetrics dm = res.getDisplayMetrics();
        res.updateConfiguration(config, dm);
    }
}

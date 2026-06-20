package com.nago8.chat.old;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.DisplayMetrics;

import com.nago8.chat.old.utils.PrefUtils;

import java.util.Locale;

public class App extends Application {

    @Override
    protected void attachBaseContext(Context base) {
        // 用 LocaleHelper.wrap 包裹 base，确保 attachBaseContext 阶段就应用正确 locale
        super.attachBaseContext(com.nago8.chat.old.utils.LocaleHelper.wrap(base));
        applyLanguage(this);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 系统语言变化时重新应用用户选择的语言
        applyLanguage(this);
    }

    public static void applyLanguage(Context context) {
        // 复用 LocaleHelper.applyToApplication 统一逻辑
        com.nago8.chat.old.utils.LocaleHelper.applyToApplication(context);
    }
}

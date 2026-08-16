package com.nago8.chat.old.utils;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;
import androidx.core.widget.CompoundButtonCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.nago8.chat.old.R;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

public class ThemeUtils {

    public static final String COLOR_WHALE_BLUE = "#648ce6"; // 鲸蓝
    public static final String COLOR_DEEP_RED = "#e3342c";   // 深红
    public static final String COLOR_SOUL_CYAN = "#2a3842";  // 魂青

    public static final String DEFAULT_COLOR = COLOR_WHALE_BLUE;

    private static final String PREF_KEY_THEME_COLOR = "app_theme_primary_color";
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#[0-9a-fA-F]{6}$");

    // 追踪所有活着的 Activity 以便颜色改变时全量响应式刷新
    private static final List<WeakReference<Activity>> aliveActivities = new ArrayList<>();

    public static synchronized void registerActivity(Activity activity) {
        if (activity == null) return;
        cleanUpDeadReferences();
        for (WeakReference<Activity> ref : aliveActivities) {
            if (ref.get() == activity) return;
        }
        aliveActivities.add(new WeakReference<>(activity));

        if (activity instanceof FragmentActivity) {
            ((FragmentActivity) activity).getSupportFragmentManager().registerFragmentLifecycleCallbacks(
                    new FragmentManager.FragmentLifecycleCallbacks() {
                        @Override
                        public void onFragmentViewCreated(@NonNull FragmentManager fm, @NonNull Fragment f, @NonNull View v, @Nullable android.os.Bundle savedInstanceState) {
                            applyThemeToViewTree(v, getThemeColor(f.getContext()));
                        }
                    }, true
            );
        }
    }

    public static synchronized void unregisterActivity(Activity activity) {
        if (activity == null) return;
        Iterator<WeakReference<Activity>> it = aliveActivities.iterator();
        while (it.hasNext()) {
            Activity a = it.next().get();
            if (a == null || a == activity) {
                it.remove();
            }
        }
    }

    private static synchronized void cleanUpDeadReferences() {
        Iterator<WeakReference<Activity>> it = aliveActivities.iterator();
        while (it.hasNext()) {
            if (it.next().get() == null) {
                it.remove();
            }
        }
    }

    /**
     * 验证是否为合法的 #RRGGBB 格式十六进制颜色字符串
     */
    public static boolean isValidColorHex(String hex) {
        if (hex == null) return false;
        return HEX_COLOR_PATTERN.matcher(hex.trim()).matches();
    }

    /**
     * 获取当前设置的主题色十六进制字符串（如 #648ce6）
     */
    public static String getThemeColorHex(Context context) {
        if (context == null) return DEFAULT_COLOR;
        String color = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                .getString(PREF_KEY_THEME_COLOR, DEFAULT_COLOR);
        if (isValidColorHex(color)) {
            return color;
        }
        return DEFAULT_COLOR;
    }

    /**
     * 设置当前主题色十六进制字符串，并全局响应式刷新所有 Activity！
     */
    public static boolean setThemeColorHex(Context context, String hex) {
        if (context == null || !isValidColorHex(hex)) return false;
        context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_KEY_THEME_COLOR, hex.trim().toLowerCase())
                .apply();

        notifyThemeChanged();
        return true;
    }

    /**
     * 通知所有存活的 Activity 响应式刷新主题颜色
     */
    public static synchronized void notifyThemeChanged() {
        cleanUpDeadReferences();
        for (WeakReference<Activity> ref : aliveActivities) {
            Activity a = ref.get();
            if (a != null && !a.isFinishing()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && a.isDestroyed()) continue;
                a.runOnUiThread(() -> applyThemeToActivity(a));
            }
        }
    }

    /**
     * 获取当前主题色的整型 ARGB 值
     */
    public static int getThemeColor(Context context) {
        String hex = getThemeColorHex(context);
        try {
            return Color.parseColor(hex);
        } catch (Exception e) {
            return Color.parseColor(DEFAULT_COLOR);
        }
    }

    /**
     * 获取比主色调略深一点的颜色（用于状态栏等）
     */
    public static int getThemeDarkColor(Context context) {
        int color = getThemeColor(context);
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= 0.85f; // 降低15%明度
        return Color.HSVToColor(hsv);
    }

    /**
     * 判断颜色是否偏深色
     */
    public static boolean isColorDark(int color) {
        double darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0;
        return darkness >= 0.45;
    }

    /**
     * 根据背景颜色获取强对比前景色（深色背景返回纯白，浅色背景返回深黑）
     */
    public static int getContrastingForegroundColor(int backgroundColor) {
        return isColorDark(backgroundColor) ? Color.WHITE : Color.parseColor("#212121");
    }

    /**
     * 根据背景颜色获取次要对比前景色
     */
    public static int getContrastingSecondaryColor(int backgroundColor) {
        return isColorDark(backgroundColor) ? Color.parseColor("#B3FFFFFF") : Color.parseColor("#8A000000");
    }

    /**
     * 全局核心方法：为 Activity 动态注入主题颜色（状态栏、Toolbar、顶部栏、TabLayout、FAB、主按钮等）
     */
    public static void applyThemeToActivity(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed()) return;

        if (activity instanceof com.nago8.chat.old.ImagePreviewActivity
                || activity instanceof com.nago8.chat.old.VideoPlayerActivity) {
            return;
        }

        registerActivity(activity);

        int primaryColor = getThemeColor(activity);
        int darkColor = getThemeDarkColor(activity);

        // 1. 动态状态栏沉浸着色（Android 5.0+ API 21 兼容保护，防止无谓重复设置导致黑边重绘闪烁）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = activity.getWindow();
            if (window != null) {
                if (window.getStatusBarColor() != darkColor) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                    window.setStatusBarColor(darkColor);
                }

                View decorView = window.getDecorView();
                if (decorView != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    int flags = decorView.getSystemUiVisibility();
                    int targetFlags = flags;
                    if (isColorDark(darkColor)) {
                        targetFlags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                    } else {
                        targetFlags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                    }
                    if (flags != targetFlags) {
                        decorView.setSystemUiVisibility(targetFlags);
                    }
                }
            }
        }

        // 2. 深度遍历 Activity 的 View 树应用主题颜色
        View content = activity.findViewById(android.R.id.content);
        if (content != null) {
            applyThemeToViewTree(content, primaryColor);
        }
    }

    /**
     * 递归为 View 树中的 Primary 控件（Toolbar、顶部栏、FAB、TabLayout 等）动态染上主色与自适应前景色
     */
    public static void applyThemeToViewTree(View view, int primaryColor) {
        if (view == null) return;

        int fgColor = getContrastingForegroundColor(primaryColor);
        int fgSecondary = getContrastingSecondaryColor(primaryColor);

        // A. Toolbar 处理
        if (view instanceof Toolbar) {
            Toolbar toolbar = (Toolbar) view;
            toolbar.setBackgroundColor(primaryColor);
            toolbar.setTitleTextColor(fgColor);
            if (toolbar.getNavigationIcon() != null) {
                toolbar.getNavigationIcon().mutate().setColorFilter(fgColor, android.graphics.PorterDuff.Mode.SRC_IN);
            }
        }
        // B. AppBarLayout 处理
        else if (view instanceof AppBarLayout) {
            view.setBackgroundColor(primaryColor);
        }
        // C. TabLayout 处理（根据顶栏/内容区背景智能自适应指示线、选中文本、未选中文本、Tab 图标）
        else if (view instanceof TabLayout) {
            TabLayout tabLayout = (TabLayout) view;
            boolean isInTopBar = isViewInTopBar(tabLayout);
            if (isInTopBar) {
                tabLayout.setBackgroundColor(primaryColor);
                tabLayout.setSelectedTabIndicatorColor(fgColor);
                tabLayout.setTabTextColors(fgSecondary, fgColor);
                tabLayout.setTabIconTint(createColorStateList(fgSecondary, fgColor));
            } else {
                tabLayout.setSelectedTabIndicatorColor(primaryColor);
                int unselectedColor = Color.parseColor("#888888");
                tabLayout.setTabTextColors(unselectedColor, primaryColor);
                tabLayout.setTabIconTint(createColorStateList(unselectedColor, primaryColor));
            }
        }
        // D. FloatingActionButton 处理（图标与背景对比自适应）
        else if (view instanceof FloatingActionButton) {
            FloatingActionButton fab = (FloatingActionButton) view;
            fab.setBackgroundTintList(ColorStateList.valueOf(primaryColor));
            ImageViewCompat.setImageTintList(fab, ColorStateList.valueOf(fgColor));
        }
        // E. MaterialButton 处理（区分实心按钮与边框按钮）
        else if (view instanceof MaterialButton) {
            MaterialButton btn = (MaterialButton) view;
            if (btn.getStrokeWidth() > 0) {
                btn.setStrokeColor(ColorStateList.valueOf(primaryColor));
                btn.setTextColor(primaryColor);
                btn.setIconTint(ColorStateList.valueOf(primaryColor));
            } else if (btn.getBackgroundTintList() != null) {
                btn.setBackgroundTintList(ColorStateList.valueOf(primaryColor));
            }
        }
        // F. SwitchCompat 处理（群聊免打扰、置顶等开关）
        else if (view instanceof SwitchCompat) {
            SwitchCompat sc = (SwitchCompat) view;
            int semiTrans = (primaryColor & 0x00FFFFFF) | 0x77000000;
            sc.setThumbTintList(createSwitchColorStateList(Color.parseColor("#ECECEC"), primaryColor));
            sc.setTrackTintList(createSwitchColorStateList(Color.parseColor("#9E9E9E"), semiTrans));
        }
        // G. 单选框 / 复选框处理
        else if (view instanceof CompoundButton) {
            CompoundButtonCompat.setButtonTintList(
                    (CompoundButton) view,
                    createColorStateList(Color.parseColor("#888888"), primaryColor)
            );
        }
        // H. 输入框处理（下划线着色）
        else if (view instanceof EditText) {
            ViewCompat.setBackgroundTintList((EditText) view, ColorStateList.valueOf(primaryColor));
        }
        // I. 文本颜色检查（如作者链接等）
        else if (view instanceof TextView) {
            TextView tv = (TextView) view;
            int textColor = tv.getCurrentTextColor();
            if (isLegacyPrimaryColor(textColor)) {
                tv.setTextColor(primaryColor);
            }
        }
        // J. ImageView 处理（“我的”界面的 3 个图标、发消息按钮等）
        else if (view instanceof ImageView) {
            ImageView iv = (ImageView) view;
            int id = view.getId();
            String idName = null;
            try {
                if (id != View.NO_ID && view.getResources() != null) {
                    idName = view.getResources().getResourceEntryName(id);
                }
            } catch (Exception ignored) {}

            boolean shouldTint = false;
            if (idName != null && idName.toLowerCase().equals("btnsend")) {
                shouldTint = true;
            } else {
                ColorStateList tintList = ImageViewCompat.getImageTintList(iv);
                if (tintList != null && isLegacyPrimaryColor(tintList.getDefaultColor())) {
                    shouldTint = true;
                }
            }
            if (shouldTint) {
                ImageViewCompat.setImageTintList(iv, ColorStateList.valueOf(primaryColor));
            }
        }
        // K. 检查特定 ID、顶部栏容器、侧边栏头部
        else {
            int id = view.getId();
            String idName = null;
            try {
                if (id != View.NO_ID && view.getResources() != null) {
                    idName = view.getResources().getResourceEntryName(id);
                }
            } catch (Exception ignored) {}

            if (idName != null) {
                String lower = idName.toLowerCase();
                // 如果是顶部栏、工具栏、侧边栏或头部容器
                if (lower.contains("toolbar") || lower.contains("titlebar") || lower.contains("statusbarfiller")
                        || lower.equals("headerview") || lower.equals("topbar")) {
                    view.setBackgroundColor(primaryColor);
                }
            }

            // L. 如果该 View 的背景是硬编码的原默认主题色，自动动态替换
            Drawable bg = view.getBackground();
            if (bg instanceof ColorDrawable) {
                int currentColor = ((ColorDrawable) bg).getColor();
                if (isLegacyPrimaryColor(currentColor)) {
                    view.setBackgroundColor(primaryColor);
                }
            }
        }

        // 递归子 View
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyThemeToViewTree(group.getChildAt(i), primaryColor);
            }
        }
    }

    private static boolean isLegacyPrimaryColor(int color) {
        return color == 0xFF3F51B5 || color == 0xFF1A237E || color == 0xFF303F9F
                || color == 0xFF648CE6 || color == 0xFFE3342C || color == 0xFF2A3842;
    }

    private static boolean isViewInTopBar(View view) {
        if (view == null) return false;
        android.view.ViewParent parent = view.getParent();
        while (parent instanceof View) {
            View pv = (View) parent;
            if (pv instanceof AppBarLayout || pv instanceof Toolbar) {
                return true;
            }
            int id = pv.getId();
            if (id != View.NO_ID && pv.getResources() != null) {
                try {
                    String name = pv.getResources().getResourceEntryName(id).toLowerCase();
                    if (name.contains("appbar") || name.contains("toolbar") || name.contains("topbar") || name.contains("header")) {
                        return true;
                    }
                } catch (Exception ignored) {}
            }
            parent = pv.getParent();
        }
        return false;
    }

    public static ColorStateList createColorStateList(int normalColor, int selectedColor) {
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_selected},
                new int[]{android.R.attr.state_checked},
                new int[]{}
        };
        int[] colors = new int[]{
                selectedColor,
                selectedColor,
                normalColor
        };
        return new ColorStateList(states, colors);
    }

    public static ColorStateList createSwitchColorStateList(int normalColor, int checkedColor) {
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{}
        };
        int[] colors = new int[]{
                checkedColor,
                normalColor
        };
        return new ColorStateList(states, colors);
    }

    /**
     * 为弹窗（AlertDialog / Dialog）内的按钮与 View 树动态注入主题色
     */
    public static void applyThemeToDialog(android.content.DialogInterface dialogInterface, Context context) {
        if (dialogInterface == null || context == null) return;
        int primaryColor = getThemeColor(context);

        if (dialogInterface instanceof androidx.appcompat.app.AlertDialog) {
            androidx.appcompat.app.AlertDialog ad = (androidx.appcompat.app.AlertDialog) dialogInterface;
            android.widget.Button pos = ad.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
            if (pos != null) pos.setTextColor(primaryColor);
            android.widget.Button neg = ad.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE);
            if (neg != null) neg.setTextColor(primaryColor);
            android.widget.Button neu = ad.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL);
            if (neu != null) neu.setTextColor(primaryColor);

            Window win = ad.getWindow();
            if (win != null && win.getDecorView() != null) {
                applyThemeToViewTree(win.getDecorView(), primaryColor);
            }
        } else if (dialogInterface instanceof android.app.AlertDialog) {
            android.app.AlertDialog ad = (android.app.AlertDialog) dialogInterface;
            android.widget.Button pos = ad.getButton(android.app.AlertDialog.BUTTON_POSITIVE);
            if (pos != null) pos.setTextColor(primaryColor);
            android.widget.Button neg = ad.getButton(android.app.AlertDialog.BUTTON_NEGATIVE);
            if (neg != null) neg.setTextColor(primaryColor);
            android.widget.Button neu = ad.getButton(android.app.AlertDialog.BUTTON_NEUTRAL);
            if (neu != null) neu.setTextColor(primaryColor);

            Window win = ad.getWindow();
            if (win != null && win.getDecorView() != null) {
                applyThemeToViewTree(win.getDecorView(), primaryColor);
            }
        }
    }

    /**
     * 自动着色并展示 AlertDialog
     */
    public static androidx.appcompat.app.AlertDialog showThemedDialog(androidx.appcompat.app.AlertDialog.Builder builder) {
        if (builder == null) return null;
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> applyThemeToDialog(d, dialog.getContext()));
        dialog.show();
        applyThemeToDialog(dialog, dialog.getContext());
        return dialog;
    }

    public static androidx.appcompat.app.AlertDialog showThemedDialog(androidx.appcompat.app.AlertDialog dialog) {
        if (dialog == null) return null;
        dialog.setOnShowListener(d -> applyThemeToDialog(d, dialog.getContext()));
        dialog.show();
        applyThemeToDialog(dialog, dialog.getContext());
        return dialog;
    }

    public static com.google.android.material.dialog.MaterialAlertDialogBuilder applyThemeToMaterialBuilder(com.google.android.material.dialog.MaterialAlertDialogBuilder builder) {
        return builder;
    }

    /**
     * 获取当前颜色的人类可读显示名称
     */
    public static String getColorDisplayName(Context context, String hex) {
        if (hex == null) return "";
        String normalized = hex.trim().toLowerCase();
        if (COLOR_WHALE_BLUE.equalsIgnoreCase(normalized)) {
            return context.getString(R.string.theme_color_whale_blue);
        } else if (COLOR_DEEP_RED.equalsIgnoreCase(normalized)) {
            return context.getString(R.string.theme_color_deep_red);
        } else if (COLOR_SOUL_CYAN.equalsIgnoreCase(normalized)) {
            return context.getString(R.string.theme_color_soul_cyan);
        } else {
            return context.getString(R.string.theme_color_custom, normalized);
        }
    }
}

package com.nago8.chat.old;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.AppCompatImageButton;

import com.bumptech.glide.Glide;
import com.nago8.chat.old.cache.AddressBookCache;
import com.nago8.chat.old.cache.AvatarCache;
import com.nago8.chat.old.cache.ConversationCache;
import com.nago8.chat.old.utils.ImageUtils;
import com.nago8.chat.old.utils.LocaleHelper;

import java.io.File;

public class SettingsActivity extends AppCompatActivity {

    private TextView tvCacheSize;
    private TextView tvAvatarThreads;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        AppCompatImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> onBackPressed());

        androidx.appcompat.widget.SwitchCompat switchDarkMode = findViewById(R.id.switchDarkMode);
        boolean darkModeEnabled = com.nago8.chat.old.utils.PrefUtils.isDarkModeEnabled(this);
        if (switchDarkMode != null) {
            switchDarkMode.setChecked(darkModeEnabled);
        }

        findViewById(R.id.menuDarkMode).setOnClickListener(v -> {
            boolean current = com.nago8.chat.old.utils.PrefUtils.isDarkModeEnabled(this);
            boolean next = !current;
            if (switchDarkMode != null) {
                switchDarkMode.setChecked(next);
            }
            com.nago8.chat.old.utils.PrefUtils.setDarkModeEnabled(this, next);
            com.nago8.chat.old.utils.PrefUtils.applyDarkMode(this);
        });

        findViewById(R.id.menuThemeColor).setOnClickListener(v -> showThemeColorDialog());

        findViewById(R.id.menuArchived).setOnClickListener(v -> {
            Intent intent = new Intent(this, ArchivedConversationsActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.menuWsLog).setOnClickListener(v -> {
            Intent intent = new Intent(this, WsLogActivity.class);
            startActivity(intent);
        });

        tvAvatarThreads = findViewById(R.id.tvAvatarThreads);
        findViewById(R.id.menuAvatarThreads).setOnClickListener(v -> showAvatarThreadsDialog());

        tvCacheSize = findViewById(R.id.tvCacheSize);
        findViewById(R.id.menuClearCache).setOnClickListener(v -> confirmClearCache());

        findViewById(R.id.menuAbout).setOnClickListener(v -> showAboutDialog());

        updateThemeColorDisplay();
        updateAvatarThreadsDisplay();
        updateCacheSize();
        updateArchivedCount();
        updateAboutVersion();
    }

    private void updateThemeColorDisplay() {
        TextView tvThemeColor = findViewById(R.id.tvThemeColor);
        com.google.android.material.imageview.ShapeableImageView ivThemeColorPreview = findViewById(R.id.ivThemeColorPreview);

        String currentHex = com.nago8.chat.old.utils.ThemeUtils.getThemeColorHex(this);
        int colorInt = com.nago8.chat.old.utils.ThemeUtils.getThemeColor(this);

        if (tvThemeColor != null) {
            tvThemeColor.setText(com.nago8.chat.old.utils.ThemeUtils.getColorDisplayName(this, currentHex));
        }
        if (ivThemeColorPreview != null) {
            ivThemeColorPreview.setBackgroundColor(colorInt);
        }
    }

    private void showThemeColorDialog() {
        final String[] options = new String[]{
                getString(R.string.theme_color_whale_blue),
                getString(R.string.theme_color_deep_red),
                getString(R.string.theme_color_soul_cyan),
                getString(R.string.theme_color_custom_input_title) + "..."
        };
        final String[] colorValues = new String[]{
                com.nago8.chat.old.utils.ThemeUtils.COLOR_WHALE_BLUE,
                com.nago8.chat.old.utils.ThemeUtils.COLOR_DEEP_RED,
                com.nago8.chat.old.utils.ThemeUtils.COLOR_SOUL_CYAN,
                ""
        };

        String currentHex = com.nago8.chat.old.utils.ThemeUtils.getThemeColorHex(this);
        int checked = 0;
        if (com.nago8.chat.old.utils.ThemeUtils.COLOR_DEEP_RED.equalsIgnoreCase(currentHex)) {
            checked = 1;
        } else if (com.nago8.chat.old.utils.ThemeUtils.COLOR_SOUL_CYAN.equalsIgnoreCase(currentHex)) {
            checked = 2;
        } else if (!com.nago8.chat.old.utils.ThemeUtils.COLOR_WHALE_BLUE.equalsIgnoreCase(currentHex)) {
            checked = 3;
        }

        com.nago8.chat.old.utils.ThemeUtils.showThemedDialog(
                new AlertDialog.Builder(this)
                        .setTitle(R.string.theme_color_dialog_title)
                        .setSingleChoiceItems(options, checked, (dialog, which) -> {
                            dialog.dismiss();
                            if (which == 3) {
                                showCustomColorInputDialog();
                            } else {
                                applySelectedThemeColor(colorValues[which]);
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
        );
    }

    private void showCustomColorInputDialog() {
        final EditText editText = new EditText(this);
        editText.setHint(R.string.theme_color_custom_hint);
        String currentHex = com.nago8.chat.old.utils.ThemeUtils.getThemeColorHex(this);
        editText.setText(currentHex);
        editText.setSelection(editText.getText().length());

        com.nago8.chat.old.utils.ThemeUtils.showThemedDialog(
                new AlertDialog.Builder(this)
                        .setTitle(R.string.theme_color_custom_input_title)
                        .setView(editText)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            String input = editText.getText() != null ? editText.getText().toString().trim() : "";
                            if (!com.nago8.chat.old.utils.ThemeUtils.isValidColorHex(input)) {
                                Toast.makeText(SettingsActivity.this, R.string.theme_color_invalid_format, Toast.LENGTH_LONG).show();
                                return;
                            }
                            applySelectedThemeColor(input);
                        })
                        .setNegativeButton(android.R.string.cancel, null)
        );
    }

    private void applySelectedThemeColor(String colorHex) {
        boolean success = com.nago8.chat.old.utils.ThemeUtils.setThemeColorHex(this, colorHex);
        if (success) {
            updateThemeColorDisplay();
            Toast.makeText(this, getString(R.string.theme_color_applied_toast, colorHex), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.theme_color_invalid_format, Toast.LENGTH_SHORT).show();
        }
    }

    private void updateAboutVersion() {
        TextView tvAboutVersion = findViewById(R.id.tvAboutVersion);
        if (tvAboutVersion != null) {
            String versionName = getAppVersionName();
            tvAboutVersion.setText(getString(R.string.about_version_format, versionName));
        }
    }

    private String getAppVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "1.0.0";
        }
    }

    private void showAboutDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_about, null);
        TextView tvDialogVersion = dialogView.findViewById(R.id.tvAboutDialogVersion);
        if (tvDialogVersion != null) {
            tvDialogVersion.setText(getString(R.string.about_version_format, getAppVersionName()));
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton(R.string.about_dialog_close, null)
                .create();

        // 作者1：Kauid323 -> https://github.com/Kauid323
        View layoutKauid = dialogView.findViewById(R.id.layoutAuthorKauid);
        if (layoutKauid != null) {
            layoutKauid.setOnClickListener(v -> {
                try {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/Kauid323"));
                    startActivity(browserIntent);
                } catch (Exception e) {
                    Toast.makeText(SettingsActivity.this, "https://github.com/Kauid323", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // 作者2：那狗吧 -> yunhu://add-chat?id=8516939&type=user / UserProfileActivity
        View layoutNago = dialogView.findViewById(R.id.layoutAuthorNago);
        if (layoutNago != null) {
            layoutNago.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(SettingsActivity.this, UserProfileActivity.class);
                    intent.putExtra(UserProfileActivity.EXTRA_USER_ID, "8516939");
                    startActivity(intent);
                } catch (Exception e) {
                    try {
                        Intent schemeIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("yunhu://add-chat?id=8516939&type=user"));
                        startActivity(schemeIntent);
                    } catch (Exception ignored) {}
                }
            });
        }

        com.nago8.chat.old.utils.ThemeUtils.showThemedDialog(dialog);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateArchivedCount();
    }

    private void updateArchivedCount() {
        TextView tvArchivedCount = findViewById(R.id.tvArchivedCount);
        if (tvArchivedCount != null) {
            int count = com.nago8.chat.old.cache.ArchiveManager.getInstance().getArchivedCount(this);
            if (count > 0) {
                tvArchivedCount.setText(getString(R.string.archived_count_format, count));
            } else {
                tvArchivedCount.setText(R.string.settings_archived_conversations_desc);
            }
        }
    }

    private void updateAvatarThreadsDisplay() {
        if (tvAvatarThreads != null) {
            int threads = ImageUtils.getAvatarLoadThreads(this);
            tvAvatarThreads.setText(getString(R.string.settings_avatar_threads_format, threads));
        }
    }

    private void showAvatarThreadsDialog() {
        final String[] options = new String[]{
                "1 " + getString(R.string.settings_avatar_threads_format, 1).replace("1 ", ""),
                "2 " + getString(R.string.settings_avatar_threads_format, 2).replace("2 ", ""),
                "4 " + getString(R.string.settings_avatar_threads_format, 4).replace("4 ", ""),
                "8 " + getString(R.string.settings_avatar_threads_format, 8).replace("8 ", ""),
                "16 " + getString(R.string.settings_avatar_threads_format, 16).replace("16 ", ""),
                "自定义线程数..."
        };
        final int[] threadValues = new int[]{1, 2, 4, 8, 16, -1};

        int currentThreads = ImageUtils.getAvatarLoadThreads(this);
        int checked = 2; // 默认 4 线程
        for (int i = 0; i < threadValues.length - 1; i++) {
            if (currentThreads == threadValues[i]) {
                checked = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_avatar_threads_dialog_title)
                .setSingleChoiceItems(options, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == options.length - 1) {
                        showCustomThreadsInputDialog();
                    } else {
                        setAvatarThreads(threadValues[which]);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showCustomThreadsInputDialog() {
        final EditText editText = new EditText(this);
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        editText.setHint(R.string.settings_avatar_threads_min_hint);
        int currentThreads = ImageUtils.getAvatarLoadThreads(this);
        editText.setText(String.valueOf(currentThreads));

        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_avatar_threads_dialog_title)
                .setView(editText)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String input = editText.getText() != null ? editText.getText().toString().trim() : "";
                    if (!input.isEmpty()) {
                        try {
                            int threads = Integer.parseInt(input);
                            if (threads < 1) threads = 1; // 最低为1线程
                            setAvatarThreads(threads);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void setAvatarThreads(int threads) {
        if (threads < 1) threads = 1; // 最低为 1 线程
        ImageUtils.setAvatarLoadThreads(this, threads);
        updateAvatarThreadsDisplay();
        Toast.makeText(this, getString(R.string.settings_avatar_threads_format, threads), Toast.LENGTH_SHORT).show();
    }

    private void updateCacheSize() {
        long avatarSize = AvatarCache.getCacheSize(this);
        long glideCacheSize = getFolderSize(Glide.getPhotoCacheDir(this));
        long totalBytes = avatarSize + glideCacheSize;

        if (tvCacheSize != null) {
            tvCacheSize.setText(getString(R.string.settings_cache_size_format, formatSize(totalBytes)));
        }
    }

    private void confirmClearCache() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_clear_all_cache)
                .setMessage("确定要清除通讯录、会话和所有本地头像缓存吗？")
                .setPositiveButton(android.R.string.ok, (dialog, which) -> clearAllCache())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void clearAllCache() {
        // 清除通讯录缓存
        AddressBookCache.clearCache(this);

        // 清除会话内存缓存
        ConversationCache.getInstance().clearCache();

        // 清除本地头像磁盘缓存
        AvatarCache.clearCache(this);

        // 清除 Glide 缓存
        try {
            Glide.get(this).clearMemory();
            new Thread(() -> Glide.get(SettingsActivity.this).clearDiskCache()).start();
        } catch (Exception ignored) {}

        updateCacheSize();
        Toast.makeText(this, R.string.settings_cache_cleared, Toast.LENGTH_SHORT).show();
    }

    private long getFolderSize(File file) {
        long size = 0;
        if (file != null && file.exists()) {
            if (file.isDirectory()) {
                File[] children = file.listFiles();
                if (children != null) {
                    for (File child : children) {
                        size += getFolderSize(child);
                    }
                }
            } else {
                size = file.length();
            }
        }
        return size;
    }

    private String formatSize(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.2f %sB", bytes / Math.pow(1024, exp), pre);
    }
}

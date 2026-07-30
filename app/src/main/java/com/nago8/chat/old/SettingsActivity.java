package com.nago8.chat.old;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
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
import com.nago8.chat.old.utils.LocaleHelper;

import java.io.File;

public class SettingsActivity extends AppCompatActivity {

    private TextView tvCacheSize;

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

        findViewById(R.id.menuWsLog).setOnClickListener(v -> {
            Intent intent = new Intent(this, WsLogActivity.class);
            startActivity(intent);
        });

        tvCacheSize = findViewById(R.id.tvCacheSize);
        findViewById(R.id.menuClearCache).setOnClickListener(v -> confirmClearCache());

        updateCacheSize();
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

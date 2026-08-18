package com.nago8.chat.old;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.AppCompatImageButton;

import com.nago8.chat.old.utils.FengEmojiRenderer;
import com.nago8.chat.old.utils.LocaleHelper;
import com.nago8.chat.old.utils.ThemeUtils;

/**
 * 自由复制界面：展示原文并提供原生文本划词选取、拖拽复制以及一键复制全部功能
 */
public class FreeCopyActivity extends AppCompatActivity {

    public static final String EXTRA_CONTENT = "content";

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_free_copy);

        // 顶栏主题适配
        View topBar = findViewById(R.id.topBar);
        int primaryColor = ThemeUtils.getThemeColor(this);
        int fgColor = ThemeUtils.getContrastingForegroundColor(primaryColor);

        if (topBar != null) {
            topBar.setBackgroundColor(primaryColor);
        }

        AppCompatImageButton btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setColorFilter(fgColor);
            btnBack.setOnClickListener(v -> onBackPressed());
        }

        TextView tvTitle = findViewById(R.id.tvTitle);
        if (tvTitle != null) {
            tvTitle.setTextColor(fgColor);
        }

        TextView btnCopyAll = findViewById(R.id.btnCopyAll);
        if (btnCopyAll != null) {
            btnCopyAll.setTextColor(fgColor);
        }

        final TextView tvContent = findViewById(R.id.tvContent);
        final String content = getIntent().getStringExtra(EXTRA_CONTENT);

        if (tvContent != null) {
            if (!TextUtils.isEmpty(content)) {
                int emojiSize = (int) (22 * getResources().getDisplayMetrics().density + 0.5f);
                tvContent.setText(FengEmojiRenderer.apply(this, content, emojiSize), TextView.BufferType.SPANNABLE);
            } else {
                tvContent.setText("");
            }
        }

        if (btnCopyAll != null) {
            btnCopyAll.setOnClickListener(v -> {
                if (TextUtils.isEmpty(content)) return;
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("text", content));
                    Toast.makeText(this, R.string.copy_all_success, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}

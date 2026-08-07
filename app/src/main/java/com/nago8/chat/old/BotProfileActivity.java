package com.nago8.chat.old;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.AppCompatImageView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.nago8.chat.old.net.ApiClient;
import com.nago8.chat.old.proto.bot.bot_info;
import com.nago8.chat.old.proto.bot.bot_info_send;
import com.nago8.chat.old.utils.ImageUtils;
import com.nago8.chat.old.utils.LocaleHelper;
import com.nago8.chat.old.utils.PrefUtils;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class BotProfileActivity extends AppCompatActivity {

    public static final String EXTRA_BOT_ID = "bot_id";

    private AppCompatImageView ivAvatar;
    private TextView tvName;
    private TextView tvBotId;
    private TextView tvIntroduction;
    private TextView tvHeadcount;
    private TextView tvCreateTime;
    private TextView tvCreateBy;
    private TextView tvPrivate;
    private TextView tvStatus;
    private ProgressBar progressBar;
    private Call runningCall;

    private FloatingActionButton fabMain;
    private FloatingActionButton fabAddOrChat;
    private FloatingActionButton fabReport;
    private View fabOverlay;
    private View layoutSubAddOrChat;
    private View layoutSubReport;
    private TextView tvAddOrChatLabel;
    private boolean isFabExpanded = false;

    private String currentBotId;
    private String currentBotName;
    private String currentBotAvatar;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bot_profile);

        String botId = getIntent().getStringExtra(EXTRA_BOT_ID);
        currentBotId = botId;

        AppCompatImageButton btnBack = findViewById(R.id.btnBack);
        ivAvatar = findViewById(R.id.ivAvatar);
        tvName = findViewById(R.id.tvName);
        tvBotId = findViewById(R.id.tvBotId);
        tvIntroduction = findViewById(R.id.tvIntroduction);
        tvHeadcount = findViewById(R.id.tvHeadcount);
        tvCreateTime = findViewById(R.id.tvCreateTime);
        tvCreateBy = findViewById(R.id.tvCreateBy);
        tvPrivate = findViewById(R.id.tvPrivate);
        tvStatus = findViewById(R.id.tvStatus);
        progressBar = findViewById(R.id.progressBar);

        fabMain = findViewById(R.id.fabMain);
        fabOverlay = findViewById(R.id.fabOverlay);
        layoutSubAddOrChat = findViewById(R.id.layoutSubAddOrChat);
        layoutSubReport = findViewById(R.id.layoutSubReport);
        fabAddOrChat = findViewById(R.id.fabAddOrChat);
        fabReport = findViewById(R.id.fabReport);
        tvAddOrChatLabel = findViewById(R.id.tvAddOrChatLabel);

        if (fabMain != null) {
            fabMain.setOnClickListener(v -> toggleFabMenu());
        }
        if (fabOverlay != null) {
            fabOverlay.setOnClickListener(v -> collapseFabMenu());
        }
        if (fabReport != null) {
            fabReport.setOnClickListener(v -> {
                collapseFabMenu();
                showReportDialog();
            });
        }
        if (layoutSubReport != null) {
            layoutSubReport.setOnClickListener(v -> {
                collapseFabMenu();
                showReportDialog();
            });
        }
        if (fabAddOrChat != null) {
            fabAddOrChat.setOnClickListener(v -> {
                collapseFabMenu();
                openChat();
            });
        }
        if (layoutSubAddOrChat != null) {
            layoutSubAddOrChat.setOnClickListener(v -> {
                collapseFabMenu();
                openChat();
            });
        }

        btnBack.setOnClickListener(v -> onBackPressed());

        fetchBotInfo(botId);
    }

    @Override
    protected void onDestroy() {
        if (runningCall != null) runningCall.cancel();
        super.onDestroy();
    }

    private void toggleFabMenu() {
        if (isFabExpanded) {
            collapseFabMenu();
        } else {
            expandFabMenu();
        }
    }

    private void expandFabMenu() {
        isFabExpanded = true;
        if (fabMain != null) fabMain.animate().rotation(45f).setDuration(200).start();
        if (fabOverlay != null) fabOverlay.setVisibility(View.VISIBLE);
        if (layoutSubReport != null) {
            layoutSubReport.setVisibility(View.VISIBLE);
            layoutSubReport.setAlpha(0f);
            layoutSubReport.setTranslationY(20f);
            layoutSubReport.animate().alpha(1f).translationY(0f).setDuration(200).start();
        }
        if (layoutSubAddOrChat != null) {
            layoutSubAddOrChat.setVisibility(View.VISIBLE);
            layoutSubAddOrChat.setAlpha(0f);
            layoutSubAddOrChat.setTranslationY(20f);
            layoutSubAddOrChat.animate().alpha(1f).translationY(0f).setDuration(200).start();
        }
    }

    private void collapseFabMenu() {
        if (!isFabExpanded) return;
        isFabExpanded = false;
        if (fabMain != null) fabMain.animate().rotation(0f).setDuration(200).start();
        if (fabOverlay != null) fabOverlay.setVisibility(View.GONE);
        if (layoutSubReport != null) {
            layoutSubReport.animate().alpha(0f).translationY(20f).setDuration(150).withEndAction(() -> layoutSubReport.setVisibility(View.GONE)).start();
        }
        if (layoutSubAddOrChat != null) {
            layoutSubAddOrChat.animate().alpha(0f).translationY(20f).setDuration(150).withEndAction(() -> layoutSubAddOrChat.setVisibility(View.GONE)).start();
        }
    }

    private void openChat() {
        if (currentBotId == null || currentBotId.isEmpty()) return;
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_CHAT_ID, currentBotId);
        intent.putExtra(ChatActivity.EXTRA_CHAT_TYPE, 3); // 3 = 机器人
        intent.putExtra(ChatActivity.EXTRA_CHAT_NAME, currentBotName != null ? currentBotName : "");
        intent.putExtra(ChatActivity.EXTRA_CHAT_AVATAR, currentBotAvatar != null ? currentBotAvatar : "");
        startActivity(intent);
    }

    private void showReportDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder =
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);
        builder.setTitle(R.string.report_user_dialog_title);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        root.setPadding(padding, padding / 2, padding, padding / 2);

        TextView tvReasonTitle = new TextView(this);
        tvReasonTitle.setText(R.string.report_type_label);
        tvReasonTitle.setTextSize(14);
        tvReasonTitle.setTextColor(0xFF888888);
        root.addView(tvReasonTitle);

        final String[] reasons = getResources().getStringArray(R.array.report_reasons);
        Spinner spinnerReason = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, reasons);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerReason.setAdapter(adapter);
        spinnerReason.setPadding(0, (int) (4 * getResources().getDisplayMetrics().density), 0, (int) (12 * getResources().getDisplayMetrics().density));
        root.addView(spinnerReason);

        com.google.android.material.textfield.TextInputLayout inputLayoutContent =
                new com.google.android.material.textfield.TextInputLayout(this, null, com.google.android.material.R.attr.textInputStyle);
        inputLayoutContent.setHint(getString(R.string.report_content_label));

        final com.google.android.material.textfield.TextInputEditText etContent =
                new com.google.android.material.textfield.TextInputEditText(inputLayoutContent.getContext());
        etContent.setHint(R.string.report_content_hint);
        etContent.setMinLines(3);
        etContent.setGravity(Gravity.TOP | Gravity.START);
        inputLayoutContent.addView(etContent);

        LinearLayout.LayoutParams paramsInput = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        paramsInput.topMargin = (int) (8 * getResources().getDisplayMetrics().density);
        paramsInput.bottomMargin = (int) (12 * getResources().getDisplayMetrics().density);
        inputLayoutContent.setLayoutParams(paramsInput);
        root.addView(inputLayoutContent);

        builder.setView(root);
        builder.setPositiveButton(R.string.dialog_confirm, (dialog, which) -> {
            Toast.makeText(this, R.string.report_submitted, Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton(R.string.dialog_cancel, null);
        builder.show();
    }

    private void fetchBotInfo(String botId) {
        if (botId == null || botId.isEmpty()) {
            Toast.makeText(this, R.string.bot_profile_load_failed, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String token = PrefUtils.getToken(this);
        if (token == null) {
            finish();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        bot_info_send requestProto = new bot_info_send.Builder()
                .id(botId)
                .build();

        RequestBody body = RequestBody.create(
                MediaType.parse("application/x-protobuf"),
                requestProto.encode()
        );

        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/bot/bot-info")
                .header("token", token)
                .post(body)
                .build();

        runningCall = ApiClient.getClient().newCall(request);
        runningCall.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(BotProfileActivity.this, R.string.bot_profile_load_failed, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        final bot_info result = bot_info.ADAPTER.decode(response.body().source());
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            if (result == null || result.data == null) {
                                Toast.makeText(BotProfileActivity.this, R.string.bot_profile_load_failed, Toast.LENGTH_SHORT).show();
                                return;
                            }
                            bindBot(result.data);
                        });
                    } catch (Exception e) {
                        Log.e("BotProfileActivity", "Decode bot info failed", e);
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(BotProfileActivity.this, R.string.bot_profile_load_failed, Toast.LENGTH_SHORT).show();
                        });
                    }
                } else {
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(BotProfileActivity.this, R.string.bot_profile_load_failed, Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private void bindBot(bot_info.Bot_data data) {
        String name = data.name;
        if (name == null || "未知用户".equals(name) || "Unknown user".equals(name)) {
            name = "";
        }
        currentBotName = name;
        currentBotAvatar = data.avatar_url;

        tvName.setText(name);
        tvBotId.setText(getString(R.string.user_id_format, data.bot_id));
        ImageUtils.loadAvatar(this, data.avatar_url, ivAvatar);

        String intro = data.introduction != null && !data.introduction.isEmpty() ? data.introduction : "";
        tvIntroduction.setText(getString(R.string.bot_profile_introduction, intro));

        tvHeadcount.setText(getString(R.string.bot_profile_headcount, String.valueOf(data.headcount)));

        if (data.create_time > 0) {
            String timeStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(data.create_time * 1000L));
            tvCreateTime.setText(getString(R.string.bot_profile_create_time, timeStr));
        } else {
            tvCreateTime.setText(getString(R.string.bot_profile_create_time, getString(R.string.bot_profile_unknown)));
        }

        String createBy = data.create_by != null && !data.create_by.isEmpty() ? data.create_by : "";
        tvCreateBy.setText(getString(R.string.bot_profile_create_by, createBy));

        tvPrivate.setText(getString(R.string.bot_profile_private, getString(data.private_ == 1 ? R.string.bot_profile_private_yes : R.string.bot_profile_private_no)));

        String statusStr;
        if (data.is_stop == 1) {
            statusStr = getString(R.string.bot_profile_status_stopped);
        } else {
            statusStr = getString(R.string.bot_profile_status_active);
        }
        tvStatus.setText(getString(R.string.bot_profile_status, statusStr));
    }
}

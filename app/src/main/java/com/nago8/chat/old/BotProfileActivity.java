package com.nago8.chat.old;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.AppCompatImageView;

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

        btnBack.setOnClickListener(v -> onBackPressed());

        fetchBotInfo(botId);
    }

    @Override
    protected void onDestroy() {
        if (runningCall != null) runningCall.cancel();
        super.onDestroy();
    }

    private void fetchBotInfo(String botId) {
        if (botId == null || botId.length() == 0) {
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
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(BotProfileActivity.this, R.string.bot_profile_load_failed, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
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
                        e.printStackTrace();
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
        tvName.setText(data.name != null && data.name.length() > 0 ? data.name : getString(R.string.unknown_user));
        tvBotId.setText("ID: " + data.bot_id);
        ImageUtils.loadAvatar(this, data.avatar_url, ivAvatar);

        String intro = data.introduction != null && data.introduction.length() > 0 ? data.introduction : "";
        tvIntroduction.setText(getString(R.string.bot_profile_introduction, intro));

        tvHeadcount.setText(getString(R.string.bot_profile_headcount, String.valueOf(data.headcount)));

        if (data.create_time > 0) {
            String timeStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(data.create_time * 1000L));
            tvCreateTime.setText(getString(R.string.bot_profile_create_time, timeStr));
        } else {
            tvCreateTime.setText(getString(R.string.bot_profile_create_time, getString(R.string.bot_profile_unknown)));
        }

        String createBy = data.create_by != null && data.create_by.length() > 0 ? data.create_by : "";
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

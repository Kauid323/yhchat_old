package com.nago8.chat.old;

import android.content.Intent;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nago8.chat.old.net.ApiClient;
import com.nago8.chat.old.utils.LocaleHelper;
import com.nago8.chat.old.utils.PrefUtils;

import java.io.IOException;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class PostDetailActivity extends AppCompatActivity {

    public static final String EXTRA_POST_ID = "extra_post_id";
    public static final String EXTRA_POST_TITLE = "extra_post_title";

    private ProgressBar progressBar;
    private ScrollView scrollView;
    private TextView tvTitle;
    private TextView tvToolbarTitle;
    private TextView tvAuthor;
    private View authorBlock;
    private String senderId = "";
    private TextView tvTime;
    private TextView tvContent;
    private Markwon markwon;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_detail);

        markwon = Markwon.builder(this)
                .usePlugin(StrikethroughPlugin.create())
                .build();

        progressBar = findViewById(R.id.progressBar);
        scrollView = findViewById(R.id.scrollView);
        tvTitle = findViewById(R.id.tvPostTitle);
        tvToolbarTitle = findViewById(R.id.tvToolbarTitle);
        tvAuthor = findViewById(R.id.tvPostAuthor);
        authorBlock = findViewById(R.id.authorBlock);
        tvTime = findViewById(R.id.tvPostTime);
        tvContent = findViewById(R.id.tvPostContent);

        // 返回按钮
        findViewById(R.id.btnBack).setOnClickListener(v -> onBackPressed());

        // 点击作者区域进入用户详情
        authorBlock.setOnClickListener(v -> {
            if (senderId != null && senderId.length() > 0) {
                Intent intent = new Intent(this, UserProfileActivity.class);
                intent.putExtra(UserProfileActivity.EXTRA_USER_ID, senderId);
                startActivity(intent);
            }
        });

        // 先用传入的标题占位
        String title = getIntent().getStringExtra(EXTRA_POST_TITLE);
        if (title != null && title.length() > 0) {
            tvTitle.setText(title);
        }


        String postId = getIntent().getStringExtra(EXTRA_POST_ID);
        if (postId == null || postId.length() == 0) {
            finish();
            return;
        }

        fetchPostDetail(postId);
    }

    private void fetchPostDetail(String postId) {
        String token = PrefUtils.getToken(this);
        if (token == null) {
            Toast.makeText(this, R.string.post_load_failed, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        scrollView.setVisibility(View.GONE);

        String json = "{\"id\":" + postId + "}";
        RequestBody body = RequestBody.create(
                MediaType.parse("application/json; charset=utf-8"), json);

        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/community/posts/post-detail")
                .header("token", token)
                .post(body)
                .build();

        ApiClient.getClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(PostDetailActivity.this, R.string.post_load_failed, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String respStr = response.body().string();
                        JsonObject root = JsonParser.parseString(respStr).getAsJsonObject();
                        if (root.has("data") && !root.get("data").isJsonNull()) {
                            JsonObject data = root.getAsJsonObject("data");
                            JsonObject post = data.has("post") && !data.get("post").isJsonNull()
                                    ? data.getAsJsonObject("post") : null;
                            if (post != null) {
                                runOnUiThread(() -> renderPost(post));
                            } else {
                                runOnUiThread(() -> {
                                    progressBar.setVisibility(View.GONE);
                                    Toast.makeText(PostDetailActivity.this, R.string.post_load_failed, Toast.LENGTH_SHORT).show();
                                });
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(PostDetailActivity.this, R.string.post_load_failed, Toast.LENGTH_SHORT).show();
                        });
                    }
                } else {
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(PostDetailActivity.this, R.string.post_load_failed, Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private void renderPost(JsonObject post) {
        progressBar.setVisibility(View.GONE);
        scrollView.setVisibility(View.VISIBLE);

        String title = getJsonString(post, "title");
        String senderNickname = getJsonString(post, "senderNickname");
        senderId = getJsonString(post, "senderId");
        String createTimeText = getJsonString(post, "createTimeText");
        String content = getJsonString(post, "content");
        int contentType = getJsonInt(post, "contentType", 1);

        if (title.length() > 0) {
            tvTitle.setText(title);
            tvToolbarTitle.setText(title);
        }
        tvAuthor.setText(getString(R.string.post_author_format, senderNickname));
        tvTime.setText(getString(R.string.post_time_format, createTimeText));

        if (content.length() > 0) {
            if (contentType == 2) {
                // Markdown 内容
                markwon.setMarkdown(tvContent, content);
            } else {
                tvContent.setText(content);
            }
        } else {
            tvContent.setText(R.string.post_loading);
        }
    }

    private String getJsonString(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return "";
    }

    private int getJsonInt(JsonObject obj, String key, int def) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            try {
                return obj.get(key).getAsInt();
            } catch (Exception e) {
                return def;
            }
        }
        return def;
    }
}

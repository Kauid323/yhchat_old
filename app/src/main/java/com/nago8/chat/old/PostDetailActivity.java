package com.nago8.chat.old;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatImageView;
import android.content.res.ColorStateList;
import androidx.core.widget.ImageViewCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.tabs.TabLayout;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nago8.chat.old.net.ApiClient;
import com.nago8.chat.old.repository.CommunityRepository;
import com.nago8.chat.old.utils.InternalLinkUtils;
import com.nago8.chat.old.utils.LocaleHelper;
import com.nago8.chat.old.utils.PrefUtils;

import java.io.IOException;
import java.util.Locale;

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

    // ==================== Views ====================
    private ProgressBar progressBar;
    private ScrollView scrollView;
    private TextView tvTitle;
    private TextView tvToolbarTitle;
    private TextView tvAuthor;
    private View authorBlock;
    private TextView tvTime;
    private TextView tvContent;
    private Markwon markwon;

    // Interaction tab views
    private LinearLayout interactionLayout;
    private LinearLayout btnLike;
    private AppCompatImageView ivLike;
    private TextView tvLikeNum;
    private LinearLayout btnCollect;
    private AppCompatImageView ivCollect;
    private TextView tvCollectNum;
    private LinearLayout btnReward;
    private AppCompatImageView ivReward;
    private TextView tvRewardNum;
    private TextView tvCommentTitle;
    private LinearLayout commentListContainer;
    private ProgressBar commentProgressBar;
    private TextView tvNoComment;
    private TextView tvLoadMoreComments;
    private EditText etComment;
    private AppCompatButton btnSendComment;

    // ==================== State ====================
    private String senderId = "";
    private long postId = 0;
    private boolean isLiked = false;
    private boolean isCollected = false;
    private boolean isRewarded = false;
    private long likeNum = 0;
    private long collectNum = 0;
    private double rewardNum = 0;
    private int commentPage = 1;
    private int commentTotal = 0;
    private boolean commentLoading = false;
    private boolean interactionTabLoaded = false;
    // replyTarget: 0 = top-level comment, >0 = reply to this commentId
    private long replyTargetCommentId = 0;
    private String replyHint = "写评论...";

    private SwipeRefreshLayout swipeRefreshLayout;
    private CommunityRepository communityRepo;
    private Call runningCommentCall;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_detail);

        communityRepo = new CommunityRepository();

        markwon = Markwon.builder(this)
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(new io.noties.markwon.AbstractMarkwonPlugin() {
                    @Override
                    public void configureConfiguration(@NonNull io.noties.markwon.MarkwonConfiguration.Builder builder) {
                        builder.linkResolver((view, link) -> {
                            if (!InternalLinkUtils.handleUrl(view.getContext(), link)) {
                                try {
                                    String openUrl = link;
                                    if (!openUrl.startsWith("http://") && !openUrl.startsWith("https://") && !openUrl.startsWith("yunhu://")) {
                                        openUrl = "http://" + openUrl;
                                    }
                                    startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(openUrl)));
                                } catch (Exception ignored) {}
                            }
                        });
                    }
                })
                .build();

        // Article tab views
        progressBar = findViewById(R.id.progressBar);
        scrollView = findViewById(R.id.scrollView);
        tvTitle = findViewById(R.id.tvPostTitle);
        tvToolbarTitle = findViewById(R.id.tvToolbarTitle);
        tvAuthor = findViewById(R.id.tvPostAuthor);
        authorBlock = findViewById(R.id.authorBlock);
        tvTime = findViewById(R.id.tvPostTime);
        tvContent = findViewById(R.id.tvPostContent);

        // Interaction tab views
        interactionLayout = findViewById(R.id.interactionLayout);
        btnLike = findViewById(R.id.btnLike);
        ivLike = findViewById(R.id.ivLike);
        tvLikeNum = findViewById(R.id.tvLikeNum);
        btnCollect = findViewById(R.id.btnCollect);
        ivCollect = findViewById(R.id.ivCollect);
        tvCollectNum = findViewById(R.id.tvCollectNum);
        btnReward = findViewById(R.id.btnReward);
        ivReward = findViewById(R.id.ivReward);
        tvRewardNum = findViewById(R.id.tvRewardNum);
        tvCommentTitle = findViewById(R.id.tvCommentTitle);
        commentListContainer = findViewById(R.id.commentListContainer);
        commentProgressBar = findViewById(R.id.commentProgressBar);
        tvNoComment = findViewById(R.id.tvNoComment);
        tvLoadMoreComments = findViewById(R.id.tvLoadMoreComments);
        etComment = findViewById(R.id.etComment);
        btnSendComment = findViewById(R.id.btnSendComment);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(() -> {
                if (postId > 0) {
                    fetchPostDetail(String.valueOf(postId));
                    loadComments(true);
                } else {
                    swipeRefreshLayout.setRefreshing(false);
                }
            });
        }

        // Back
        findViewById(R.id.btnBack).setOnClickListener(v -> onBackPressed());

        // Author block
        authorBlock.setOnClickListener(v -> {
            if (senderId != null && senderId.length() > 0) {
                Intent intent = new Intent(this, UserProfileActivity.class);
                intent.putExtra(UserProfileActivity.EXTRA_USER_ID, senderId);
                startActivity(intent);
            }
        });

        // Tab layout
        TabLayout tabLayout = findViewById(R.id.tabLayout);
        tabLayout.addTab(tabLayout.newTab().setText("文章"));
        tabLayout.addTab(tabLayout.newTab().setText("互动"));
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    scrollView.setVisibility(View.VISIBLE);
                    interactionLayout.setVisibility(View.GONE);
                } else {
                    scrollView.setVisibility(View.GONE);
                    interactionLayout.setVisibility(View.VISIBLE);
                    if (!interactionTabLoaded && postId > 0) {
                        interactionTabLoaded = true;
                        loadComments(true);
                    }
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        // Interaction buttons
        btnLike.setOnClickListener(v -> toggleLike());
        btnCollect.setOnClickListener(v -> toggleCollect());
        btnReward.setOnClickListener(v -> showRewardDialog());

        // Comment send
        btnSendComment.setOnClickListener(v -> submitComment());
        etComment.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submitComment();
                return true;
            }
            return false;
        });

        // Load more comments
        tvLoadMoreComments.setOnClickListener(v -> loadComments(false));

        // Pre-fill title from intent
        String title = getIntent().getStringExtra(EXTRA_POST_TITLE);
        if (title != null && title.length() > 0) {
            tvTitle.setText(title);
        }

        // Resolve postId
        String postIdStr = getIntent().getStringExtra(EXTRA_POST_ID);
        if (postIdStr == null || postIdStr.length() == 0) {
            if (getIntent().getData() != null) {
                postIdStr = InternalLinkUtils.parsePostId(getIntent().getData().toString());
            }
        }
        if (postIdStr == null || postIdStr.length() == 0) {
            finish();
            return;
        }
        try {
            postId = Long.parseLong(postIdStr);
        } catch (NumberFormatException e) {
            finish();
            return;
        }

        fetchPostDetail(String.valueOf(postId));
    }

    // ==================== Fetch Article ====================

    private void fetchPostDetail(String pid) {
        String token = PrefUtils.getToken(this);
        if (token == null) {
            Toast.makeText(this, R.string.post_load_failed, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        TabLayout tabLayout = findViewById(R.id.tabLayout);
        int selectedTab = tabLayout != null ? tabLayout.getSelectedTabPosition() : 0;
        if (selectedTab == 0) {
            scrollView.setVisibility(View.GONE);
        }

        String json = "{\"id\":" + pid + "}";
        RequestBody body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), json);
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
        TabLayout tabLayout = findViewById(R.id.tabLayout);
        int selectedTab = tabLayout != null ? tabLayout.getSelectedTabPosition() : 0;
        if (selectedTab == 1) {
            scrollView.setVisibility(View.GONE);
            interactionLayout.setVisibility(View.VISIBLE);
        } else {
            scrollView.setVisibility(View.VISIBLE);
            interactionLayout.setVisibility(View.GONE);
        }

        String title = getJsonString(post, "title");
        String senderNickname = getJsonString(post, "senderNickname");
        senderId = getJsonString(post, "senderId");
        String createTimeText = getJsonString(post, "createTimeText");
        String content = getJsonString(post, "content");
        int contentType = getJsonInt(post, "contentType", 1);

        // Interaction state from API
        isLiked = getJsonIntOrString(post, "isLiked") == 1;
        isCollected = getJsonIntOrString(post, "isCollected") == 1;
        isRewarded = getJsonIntOrString(post, "isReward") == 1;
        likeNum = getJsonLong(post, "likeNum", 0);
        collectNum = getJsonLong(post, "collectNum", 0);
        rewardNum = getJsonDouble(post, "amountNum", 0);
        long commentNum = getJsonLong(post, "commentNum", 0);

        if (title.length() > 0) {
            tvTitle.setText(title);
            tvToolbarTitle.setText(title);
        }
        tvAuthor.setText(getString(R.string.post_author_format, senderNickname));
        tvTime.setText(getString(R.string.post_time_format, createTimeText));

        if (content.length() > 0) {
            if (contentType == 2) {
                markwon.setMarkdown(tvContent, content);
            } else {
                tvContent.setText(content);
            }
        } else {
            tvContent.setText(R.string.post_loading);
        }

        // Update interaction UI
        updateLikeUI();
        updateCollectUI();
        updateRewardUI();
        tvCommentTitle.setText("评论 " + commentNum);
    }

    // ==================== Interaction Actions ====================

    private void toggleLike() {
        String token = PrefUtils.getToken(this);
        if (token == null) return;
        boolean wasLiked = isLiked;
        isLiked = !wasLiked;
        likeNum += isLiked ? 1 : -1;
        updateLikeUI();
        communityRepo.likePost(token, postId, new CommunityRepository.SimpleCallback() {
            @Override public void onSuccess() {}
            @Override public void onError(String msg) {
                runOnUiThread(() -> {
                    isLiked = wasLiked;
                    likeNum += isLiked ? 1 : -1;
                    updateLikeUI();
                    Toast.makeText(PostDetailActivity.this, msg, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void toggleCollect() {
        String token = PrefUtils.getToken(this);
        if (token == null) return;
        boolean wasCollected = isCollected;
        isCollected = !wasCollected;
        collectNum += isCollected ? 1 : -1;
        updateCollectUI();
        communityRepo.collectPost(token, postId, new CommunityRepository.SimpleCallback() {
            @Override public void onSuccess() {}
            @Override public void onError(String msg) {
                runOnUiThread(() -> {
                    isCollected = wasCollected;
                    collectNum += isCollected ? 1 : -1;
                    updateCollectUI();
                    Toast.makeText(PostDetailActivity.this, msg, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showRewardDialog() {
        if (isRewarded) {
            Toast.makeText(this, "已经投过币了", Toast.LENGTH_SHORT).show();
            return;
        }
        String token = PrefUtils.getToken(this);
        if (token == null) return;

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(16), dp(20), dp(8));

        TextView tvSubtitle = new TextView(this);
        tvSubtitle.setText("快捷选择投币数量：");
        tvSubtitle.setTextSize(13f);
        tvSubtitle.setTextColor(getResources().getColor(R.color.text_secondary));
        layout.addView(tvSubtitle);

        // 快捷选项按钮行 (1, 2, 5, 10)
        LinearLayout presetRow = new LinearLayout(this);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(8);
        rowParams.bottomMargin = dp(16);
        presetRow.setLayoutParams(rowParams);

        final int[] presetAmounts = {1, 2, 5, 10};
        final TextView[] presetBtns = new TextView[presetAmounts.length];

        final EditText input = new EditText(this);
        input.setHint("自定义投币数量");
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText("1");
        input.setTextSize(14f);
        input.setBackgroundResource(R.drawable.bg_input_field);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));

        for (int i = 0; i < presetAmounts.length; i++) {
            final int val = presetAmounts[i];
            TextView btn = new TextView(this);
            btn.setText(val + "金币");
            btn.setTextSize(13f);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(0, dp(8), 0, dp(8));
            btn.setClickable(true);
            btn.setFocusable(true);

            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) btnParams.leftMargin = dp(6);
            btn.setLayoutParams(btnParams);

            if (i == 0) {
                btn.setBackgroundResource(R.drawable.bg_button_primary);
                btn.setTextColor(0xFFFFFFFF);
            } else {
                btn.setBackgroundResource(R.drawable.bg_input_field);
                btn.setTextColor(getResources().getColor(R.color.text_primary));
            }

            final int index = i;
            btn.setOnClickListener(v -> {
                input.setText(String.valueOf(val));
                input.setSelection(input.getText().length());
                for (int j = 0; j < presetBtns.length; j++) {
                    if (j == index) {
                        presetBtns[j].setBackgroundResource(R.drawable.bg_button_primary);
                        presetBtns[j].setTextColor(0xFFFFFFFF);
                    } else {
                        presetBtns[j].setBackgroundResource(R.drawable.bg_input_field);
                        presetBtns[j].setTextColor(getResources().getColor(R.color.text_primary));
                    }
                }
            });

            presetBtns[i] = btn;
            presetRow.addView(btn);
        }

        layout.addView(presetRow);

        TextView tvCustomLabel = new TextView(this);
        tvCustomLabel.setText("自定义数量：");
        tvCustomLabel.setTextSize(13f);
        tvCustomLabel.setTextColor(getResources().getColor(R.color.text_secondary));
        layout.addView(tvCustomLabel);

        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inputParams.topMargin = dp(6);
        input.setLayoutParams(inputParams);
        layout.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("文章投币")
                .setView(layout)
                .setPositiveButton("确认投币", (dialog, which) -> {
                    String amountStr = input.getText().toString().trim();
                    if (amountStr.isEmpty()) return;
                    try {
                        double amount = Double.parseDouble(amountStr);
                        if (amount <= 0) {
                            Toast.makeText(this, "请输入有效金币数量", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        communityRepo.rewardPost(token, postId, senderId, amount, new CommunityRepository.SimpleCallback() {
                            @Override public void onSuccess() {
                                runOnUiThread(() -> {
                                    isRewarded = true;
                                    rewardNum += amount;
                                    updateRewardUI();
                                    Toast.makeText(PostDetailActivity.this, "投币成功！", Toast.LENGTH_SHORT).show();
                                });
                            }
                            @Override public void onError(String msg) {
                                runOnUiThread(() -> Toast.makeText(PostDetailActivity.this, "投币失败: " + msg, Toast.LENGTH_SHORT).show());
                            }
                        });
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "请输入有效金币数量", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateLikeUI() {
        if (isLiked) {
            ivLike.setImageResource(R.drawable.ic_like_filled);
            ImageViewCompat.setImageTintList(ivLike, null);
            tvLikeNum.setTextColor(0xFFE53935);
        } else {
            ivLike.setImageResource(R.drawable.ic_like_outline);
            ImageViewCompat.setImageTintList(ivLike, ColorStateList.valueOf(getResources().getColor(R.color.text_secondary)));
            tvLikeNum.setTextColor(getResources().getColor(R.color.text_secondary));
        }
        tvLikeNum.setText(formatNum(likeNum));
    }

    private void updateCollectUI() {
        if (isCollected) {
            ivCollect.setImageResource(R.drawable.ic_star_filled);
            ImageViewCompat.setImageTintList(ivCollect, null);
            tvCollectNum.setTextColor(0xFFFFC107);
        } else {
            ivCollect.setImageResource(R.drawable.ic_star_outline);
            ImageViewCompat.setImageTintList(ivCollect, ColorStateList.valueOf(getResources().getColor(R.color.text_secondary)));
            tvCollectNum.setTextColor(getResources().getColor(R.color.text_secondary));
        }
        tvCollectNum.setText(formatNum(collectNum));
    }

    private void updateRewardUI() {
        if (isRewarded) {
            ImageViewCompat.setImageTintList(ivReward, ColorStateList.valueOf(0xFFFFA726));
            tvRewardNum.setTextColor(0xFFFFA726);
        } else {
            ImageViewCompat.setImageTintList(ivReward, ColorStateList.valueOf(getResources().getColor(R.color.text_secondary)));
            tvRewardNum.setTextColor(getResources().getColor(R.color.text_secondary));
        }
        tvRewardNum.setText(rewardNum > 0 ? String.format(Locale.US, "%.0f", rewardNum) : "0");
    }

    // ==================== Comments ====================

    private void loadComments(boolean reset) {
        String token = PrefUtils.getToken(this);
        if (token == null) return;

        if (reset) {
            if (runningCommentCall != null) {
                runningCommentCall.cancel();
                runningCommentCall = null;
            }
            commentLoading = false;
            commentPage = 1;
            commentTotal = 0;
            removeCommentViews();
        } else if (commentLoading) {
            return;
        }

        commentLoading = true;
        commentProgressBar.setVisibility(View.VISIBLE);
        tvLoadMoreComments.setVisibility(View.GONE);

        runningCommentCall = communityRepo.getCommentList(token, postId, commentPage, 20, new CommunityRepository.StringCallback() {
            @Override
            public void onSuccess(String body) {
                runOnUiThread(() -> {
                    runningCommentCall = null;
                    commentLoading = false;
                    commentProgressBar.setVisibility(View.GONE);
                    if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                    try {
                        com.google.gson.JsonObject rootGson = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
                        int code = rootGson.has("code") ? rootGson.get("code").getAsInt() : 0;
                        if (code != 1) { return; }
                        if (!rootGson.has("data") || rootGson.get("data").isJsonNull()) return;
                        com.google.gson.JsonObject data = rootGson.getAsJsonObject("data");
                        commentTotal = data.has("total") ? data.get("total").getAsInt() : 0;
                        tvCommentTitle.setText("评论 " + commentTotal);
                        if (commentTotal == 0) {
                            tvNoComment.setVisibility(View.VISIBLE);
                            return;
                        }
                        tvNoComment.setVisibility(View.GONE);
                        com.google.gson.JsonArray comments = data.has("comments") ? data.getAsJsonArray("comments") : new com.google.gson.JsonArray();
                        if (reset) {
                            removeCommentViews();
                        }
                        for (int i = 0; i < comments.size(); i++) {
                            addCommentView(comments.get(i).getAsJsonObject());
                        }
                        commentPage++;
                        // show load more if there are more
                        int loadedSoFar = (commentPage - 1) * 20;
                        if (loadedSoFar < commentTotal) {
                            tvLoadMoreComments.setVisibility(View.VISIBLE);
                        } else {
                            tvLoadMoreComments.setVisibility(View.GONE);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
            @Override
            public void onError(String msg) {
                runOnUiThread(() -> {
                    runningCommentCall = null;
                    commentLoading = false;
                    commentProgressBar.setVisibility(View.GONE);
                    if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                });
            }
        });
    }

    private void removeCommentViews() {
        // Keep: commentProgressBar(idx 0), tvNoComment(idx 1), tvLoadMoreComments(idx 2)
        int keepCount = 3;
        while (commentListContainer.getChildCount() > keepCount) {
            commentListContainer.removeViewAt(keepCount);
        }
    }

    @SuppressWarnings("deprecation")
    private void addCommentView(com.google.gson.JsonObject comment) {
        long commentId = comment.has("id") ? comment.get("id").getAsLong() : 0;
        final String commentSenderId = comment.has("senderId") ? comment.get("senderId").getAsString() : "";
        String senderNick = comment.has("senderNickname") ? comment.get("senderNickname").getAsString() : "";
        String senderAvatar = comment.has("senderAvatar") ? comment.get("senderAvatar").getAsString() : "";
        String content = comment.has("content") ? comment.get("content").getAsString() : "";
        String createTime = comment.has("createTimeText") ? comment.get("createTimeText").getAsString() : "";
        long cLikeNum = comment.has("likeNum") ? comment.get("likeNum").getAsLong() : 0;
        boolean cIsLiked = comment.has("isLiked") && !comment.get("isLiked").isJsonNull() &&
                (comment.get("isLiked").getAsString().equals("1") || comment.get("isLiked").getAsInt() == 1);
        int repliesNum = comment.has("repliesNum") ? comment.get("repliesNum").getAsInt() : 0;
        com.google.gson.JsonArray replies = comment.has("replies") && comment.get("replies").isJsonArray()
                ? comment.getAsJsonArray("replies") : new com.google.gson.JsonArray();

        // Root comment container
        LinearLayout commentCard = new LinearLayout(this);
        commentCard.setOrientation(LinearLayout.VERTICAL);
        commentCard.setPadding(dp(12), dp(10), dp(12), dp(6));

        // Top row: avatar + nick + like btn
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        // Avatar
        ImageView avatar = new ImageView(this);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        avatarParams.rightMargin = dp(8);
        avatar.setLayoutParams(avatarParams);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        
        // 使用框架统一的 ImageUtils 加载与缓存头像，支持 Android 4.x HTTP 降级与缓存处理
        com.nago8.chat.old.utils.ImageUtils.loadAvatar(this, senderAvatar, avatar);

        // 点击评论头像或昵称进入用户详情
        if (!TextUtils.isEmpty(commentSenderId)) {
            View.OnClickListener openUserListener = v -> {
                Intent intent = new Intent(PostDetailActivity.this, UserProfileActivity.class);
                intent.putExtra(UserProfileActivity.EXTRA_USER_ID, commentSenderId);
                startActivity(intent);
            };
            avatar.setOnClickListener(openUserListener);
            avatar.setClickable(true);
            avatar.setFocusable(true);
        }

        // Nick + time
        LinearLayout nickTimeCol = new LinearLayout(this);
        nickTimeCol.setOrientation(LinearLayout.VERTICAL);
        nickTimeCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView tvNick = new TextView(this);
        tvNick.setText(senderNick);
        tvNick.setTextSize(13f);
        tvNick.setTextColor(getResources().getColor(R.color.text_primary));
        tvNick.setTypeface(tvNick.getTypeface(), android.graphics.Typeface.BOLD);
        if (!TextUtils.isEmpty(commentSenderId)) {
            tvNick.setOnClickListener(v -> {
                Intent intent = new Intent(PostDetailActivity.this, UserProfileActivity.class);
                intent.putExtra(UserProfileActivity.EXTRA_USER_ID, commentSenderId);
                startActivity(intent);
            });
            tvNick.setClickable(true);
            tvNick.setFocusable(true);
        }
        TextView tvTime2 = new TextView(this);
        tvTime2.setText(createTime);
        tvTime2.setTextSize(11f);
        tvTime2.setTextColor(getResources().getColor(R.color.text_secondary));
        nickTimeCol.addView(tvNick);
        nickTimeCol.addView(tvTime2);

        // Like button
        final long[] curLikeNum = {cLikeNum};
        final boolean[] curIsLiked = {cIsLiked};
        LinearLayout likeBtn = new LinearLayout(this);
        likeBtn.setOrientation(LinearLayout.HORIZONTAL);
        likeBtn.setGravity(android.view.Gravity.CENTER_VERTICAL);
        likeBtn.setPadding(dp(8), dp(4), 0, dp(4));
        likeBtn.setClickable(true);
        likeBtn.setFocusable(true);
        ImageView likeBtnIv = new ImageView(this);
        likeBtnIv.setLayoutParams(new LinearLayout.LayoutParams(dp(16), dp(16)));
        likeBtnIv.setImageResource(curIsLiked[0] ? R.drawable.ic_like_filled : R.drawable.ic_like_outline);
        ImageViewCompat.setImageTintList(likeBtnIv, curIsLiked[0] ? null : ColorStateList.valueOf(getResources().getColor(R.color.text_secondary)));
        TextView likeBtnTv = new TextView(this);
        likeBtnTv.setText(formatNum(curLikeNum[0]));
        likeBtnTv.setTextSize(11f);
        likeBtnTv.setTextColor(curIsLiked[0] ? 0xFFE53935 : getResources().getColor(R.color.text_secondary));
        LinearLayout.LayoutParams likeTvParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        likeTvParams.leftMargin = dp(3);
        likeBtnTv.setLayoutParams(likeTvParams);
        likeBtn.addView(likeBtnIv);
        likeBtn.addView(likeBtnTv);
        likeBtn.setOnClickListener(v -> {
            String token = PrefUtils.getToken(this);
            if (token == null) return;
            boolean wasCLiked = curIsLiked[0];
            curIsLiked[0] = !wasCLiked;
            curLikeNum[0] += curIsLiked[0] ? 1 : -1;
            likeBtnIv.setImageResource(curIsLiked[0] ? R.drawable.ic_like_filled : R.drawable.ic_like_outline);
            ImageViewCompat.setImageTintList(likeBtnIv, curIsLiked[0] ? null : ColorStateList.valueOf(getResources().getColor(R.color.text_secondary)));
            likeBtnTv.setText(formatNum(curLikeNum[0]));
            likeBtnTv.setTextColor(curIsLiked[0] ? 0xFFE53935 : getResources().getColor(R.color.text_secondary));
            communityRepo.likeComment(token, commentId, new CommunityRepository.SimpleCallback() {
                @Override public void onSuccess() {}
                @Override public void onError(String msg) {
                    runOnUiThread(() -> {
                        curIsLiked[0] = wasCLiked;
                        curLikeNum[0] += curIsLiked[0] ? 1 : -1;
                        likeBtnIv.setImageResource(curIsLiked[0] ? R.drawable.ic_like_filled : R.drawable.ic_like_outline);
                        likeBtnTv.setText(formatNum(curLikeNum[0]));
                    });
                }
            });
        });

        topRow.addView(avatar);
        topRow.addView(nickTimeCol);
        topRow.addView(likeBtn);
        commentCard.addView(topRow);

        // Comment body text
        TextView tvBody = new TextView(this);
        tvBody.setText(content);
        tvBody.setTextSize(14f);
        tvBody.setTextColor(getResources().getColor(R.color.text_primary));
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyParams.leftMargin = dp(44);
        bodyParams.topMargin = dp(4);
        tvBody.setLayoutParams(bodyParams);
        commentCard.addView(tvBody);

        // Reply button
        TextView tvReplyBtn = new TextView(this);
        tvReplyBtn.setText("回复");
        tvReplyBtn.setTextSize(12f);
        tvReplyBtn.setTextColor(getResources().getColor(R.color.app_primary));
        tvReplyBtn.setPadding(0, dp(4), 0, dp(4));
        LinearLayout.LayoutParams replyBtnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        replyBtnParams.leftMargin = dp(44);
        tvReplyBtn.setLayoutParams(replyBtnParams);
        tvReplyBtn.setClickable(true);
        tvReplyBtn.setFocusable(true);
        tvReplyBtn.setOnClickListener(v -> {
            replyTargetCommentId = commentId;
            replyHint = "回复 @" + senderNick + "...";
            etComment.setHint(replyHint);
            etComment.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(etComment, InputMethodManager.SHOW_IMPLICIT);
        });
        commentCard.addView(tvReplyBtn);

        // Replies (nested)
        // Replies (nested)
        if (replies.size() > 0) {
            LinearLayout repliesContainer = new LinearLayout(this);
            repliesContainer.setOrientation(LinearLayout.VERTICAL);
            repliesContainer.setBackgroundColor(0x0A000000);
            LinearLayout.LayoutParams repliesParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            repliesParams.leftMargin = dp(44);
            repliesParams.rightMargin = dp(12);
            repliesParams.topMargin = dp(6);
            repliesContainer.setLayoutParams(repliesParams);
            repliesContainer.setPadding(dp(8), dp(6), dp(8), dp(6));

            // Show first reply
            com.google.gson.JsonObject firstReply = replies.get(0).getAsJsonObject();
            addReplyView(repliesContainer, firstReply, commentId);

            // "展开剩余 N 条" button
            int extraCount = replies.size() - 1;
            if (extraCount > 0) {
                TextView tvExpandReplies = new TextView(this);
                tvExpandReplies.setText("展开剩余 " + extraCount + " 条回复 ▼");
                tvExpandReplies.setTextSize(12f);
                tvExpandReplies.setTextColor(getResources().getColor(R.color.app_primary));
                tvExpandReplies.setPadding(0, dp(6), 0, dp(4));
                tvExpandReplies.setClickable(true);
                tvExpandReplies.setFocusable(true);

                final boolean[] expanded = {false};
                tvExpandReplies.setOnClickListener(v -> {
                    if (!expanded[0]) {
                        expanded[0] = true;
                        tvExpandReplies.setVisibility(View.GONE);
                        for (int i = 1; i < replies.size(); i++) {
                            addReplyView(repliesContainer, replies.get(i).getAsJsonObject(), commentId);
                        }
                    }
                });
                repliesContainer.addView(tvExpandReplies);
            }

            commentCard.addView(repliesContainer);
        }

        // Divider
        View divider = new View(this);
        divider.setBackgroundColor(getResources().getColor(R.color.divider_color));
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
        divParams.topMargin = dp(8);
        divider.setLayoutParams(divParams);
        commentCard.addView(divider);

        // Insert before tvLoadMoreComments (last child of commentListContainer)
        int insertIdx = commentListContainer.getChildCount() - 1;
        commentListContainer.addView(commentCard, insertIdx);
    }

    @SuppressWarnings("deprecation")
    private void addReplyView(LinearLayout container, com.google.gson.JsonObject reply, long parentCommentId) {
        if (container == null || reply == null) return;

        String senderId = "";
        if (reply.has("senderId") && !reply.get("senderId").isJsonNull()) {
            senderId = reply.get("senderId").getAsString();
        } else if (reply.has("sender_id") && !reply.get("sender_id").isJsonNull()) {
            senderId = reply.get("sender_id").getAsString();
        } else if (reply.has("user_id") && !reply.get("user_id").isJsonNull()) {
            senderId = reply.get("user_id").getAsString();
        }

        String nick = (reply.has("senderNickname") && !reply.get("senderNickname").isJsonNull())
                ? reply.get("senderNickname").getAsString() : "";
        if ("未知用户".equals(nick) || "Unknown user".equals(nick)) {
            nick = "";
        }
        String cnt = (reply.has("content") && !reply.get("content").isJsonNull())
                ? reply.get("content").getAsString() : "";
        String time = (reply.has("createTimeText") && !reply.get("createTimeText").isJsonNull())
                ? reply.get("createTimeText").getAsString() : "";

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.TOP);
        row.setPadding(0, dp(4), 0, dp(4));

        TextView tvNick = new TextView(this);
        tvNick.setText(nick.isEmpty() ? "" : nick);
        tvNick.setTextSize(13f);
        tvNick.setTypeface(null, android.graphics.Typeface.BOLD);
        tvNick.setTextColor(getResources().getColor(R.color.app_primary));
        tvNick.setPadding(0, 0, dp(4), 0);

        if (!android.text.TextUtils.isEmpty(senderId)) {
            final String targetUserId = senderId;
            tvNick.setOnClickListener(v -> {
                Intent intent = new Intent(PostDetailActivity.this, UserProfileActivity.class);
                intent.putExtra(UserProfileActivity.EXTRA_USER_ID, targetUserId);
                startActivity(intent);
            });
            tvNick.setClickable(true);
            tvNick.setFocusable(true);
        }

        TextView tvContent = new TextView(this);
        tvContent.setText(": " + cnt);
        tvContent.setTextSize(13f);
        tvContent.setTextColor(getResources().getColor(R.color.text_primary));
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tvContent.setLayoutParams(contentParams);

        TextView tvT = new TextView(this);
        tvT.setText(time.length() > 10 ? time.substring(5, 10) : time);
        tvT.setTextSize(11f);
        tvT.setTextColor(getResources().getColor(R.color.text_secondary));
        LinearLayout.LayoutParams tvTParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tvTParams.leftMargin = dp(6);
        tvT.setLayoutParams(tvTParams);

        final String finalNick = nick;
        View.OnClickListener replyClickListener = v -> {
            replyTargetCommentId = parentCommentId;
            replyHint = "回复 @" + (finalNick.isEmpty() ? "" : finalNick) + "...";
            etComment.setHint(replyHint);
            etComment.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(etComment, InputMethodManager.SHOW_IMPLICIT);
        };

        tvContent.setOnClickListener(replyClickListener);
        tvT.setOnClickListener(replyClickListener);

        row.addView(tvNick);
        row.addView(tvContent);
        row.addView(tvT);

        container.addView(row);
    }

    private void submitComment() {
        String text = etComment.getText().toString().trim();
        if (TextUtils.isEmpty(text)) {
            Toast.makeText(this, "请输入评论内容", Toast.LENGTH_SHORT).show();
            return;
        }
        String token = PrefUtils.getToken(this);
        if (token == null) return;

        btnSendComment.setEnabled(false);
        long targetCommentId = replyTargetCommentId;

        communityRepo.sendComment(token, postId, targetCommentId, text, new CommunityRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    btnSendComment.setEnabled(true);
                    etComment.setText("");
                    replyTargetCommentId = 0;
                    etComment.setHint("写评论...");
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (imm != null) imm.hideSoftInputFromWindow(etComment.getWindowToken(), 0);
                    Toast.makeText(PostDetailActivity.this, "评论成功！", Toast.LENGTH_SHORT).show();
                    // Reload comments
                    loadComments(true);
                });
            }
            @Override
            public void onError(String msg) {
                runOnUiThread(() -> {
                    btnSendComment.setEnabled(true);
                    Toast.makeText(PostDetailActivity.this, "评论失败: " + msg, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    // ==================== Helpers ====================

    private String formatNum(long num) {
        if (num >= 10000) return String.format(Locale.US, "%.1fw", num / 10000.0);
        return String.valueOf(num);
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private String getJsonString(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return "";
    }

    private int getJsonInt(JsonObject obj, String key, int def) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            try { return obj.get(key).getAsInt(); } catch (Exception e) { return def; }
        }
        return def;
    }

    private long getJsonLong(JsonObject obj, String key, long def) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            try { return obj.get(key).getAsLong(); } catch (Exception e) { return def; }
        }
        return def;
    }

    private double getJsonDouble(JsonObject obj, String key, double def) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            try { return obj.get(key).getAsDouble(); } catch (Exception e) { return def; }
        }
        return def;
    }

    /**
     * isLiked / isCollected / isReward 有时是字符串"0"/"1"，有时是整数0/1，统一处理
     */
    private int getJsonIntOrString(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return 0;
        try {
            return obj.get(key).getAsInt();
        } catch (Exception e) {
            try {
                return Integer.parseInt(obj.get(key).getAsString());
            } catch (Exception e2) {
                return 0;
            }
        }
    }
}

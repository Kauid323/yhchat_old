package com.nago8.chat.old;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.nago8.chat.old.adapter.CommunityPostsAdapter;
import com.nago8.chat.old.model.CommunityBaModel;
import com.nago8.chat.old.model.CommunityPostModel;
import com.nago8.chat.old.repository.CommunityRepository;
import com.nago8.chat.old.utils.ImageUtils;
import com.nago8.chat.old.utils.LocaleHelper;
import com.nago8.chat.old.utils.PrefUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class SectionDetailActivity extends AppCompatActivity {

    public static final String EXTRA_BA_ID = "ba_id";
    public static final String EXTRA_BA_NAME = "ba_name";

    private int baId;
    private String baName;

    private TextView tvTitle;
    private MaterialCardView cardSectionHeader;
    private ShapeableImageView ivBaAvatar;
    private TextView tvBaName;
    private TextView tvBaStats;
    private MaterialButton btnFollow;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView rvPosts;
    private ProgressBar progressBar;
    private FloatingActionButton fabFilter;

    private CommunityPostsAdapter postsAdapter;
    private CommunityRepository communityRepository;
    private CommunityBaModel currentBaModel;

    // Filter typ: 1-热门, 4-最新, 3-倒序
    private int currentTyp = 1;
    private int currentPage = 1;
    private final int pageSize = 20;
    private boolean isLoading = false;
    private boolean hasReachedEnd = false;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_section_detail);

        baId = getIntent().getIntExtra(EXTRA_BA_ID, 0);
        baName = getIntent().getStringExtra(EXTRA_BA_NAME);
        if (baName == null || baName.isEmpty()) {
            baName = getString(R.string.section_detail_title);
        }

        communityRepository = new CommunityRepository();

        AppCompatImageButton btnBack = findViewById(R.id.btnBack);
        AppCompatImageButton btnFilter = findViewById(R.id.btnFilter);
        tvTitle = findViewById(R.id.tvTitle);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        rvPosts = findViewById(R.id.rvPosts);
        progressBar = findViewById(R.id.progressBar);

        tvTitle.setText(baName);
        btnBack.setOnClickListener(v -> onBackPressed());
        if (btnFilter != null) {
            btnFilter.setOnClickListener(v -> showFilterDialog());
        }

        postsAdapter = new CommunityPostsAdapter(this);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setInitialPrefetchItemCount(6);
        rvPosts.setLayoutManager(layoutManager);

        // Inflate section header view to place at position 0 of the article list RecyclerView
        View headerView = LayoutInflater.from(this).inflate(R.layout.item_section_header, rvPosts, false);
        cardSectionHeader = headerView.findViewById(R.id.cardSectionHeader);
        ivBaAvatar = headerView.findViewById(R.id.ivBaAvatar);
        tvBaName = headerView.findViewById(R.id.tvBaName);
        tvBaStats = headerView.findViewById(R.id.tvBaStats);
        btnFollow = headerView.findViewById(R.id.btnFollow);

        tvBaName.setText(baName);

        postsAdapter.setHeaderView(headerView);
        rvPosts.setAdapter(postsAdapter);

        swipeRefresh.setOnRefreshListener(() -> {
            currentPage = 1;
            hasReachedEnd = false;
            fetchSectionInfo();
            loadPosts(false, false);
        });

        postsAdapter.setOnLoadMoreClickListener(this::loadMore);

        rvPosts.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy > 0 && !isLoading && !hasReachedEnd) {
                    if (!recyclerView.canScrollVertically(1)) {
                        loadMore();
                    }
                }
            }
        });

        cardSectionHeader.setOnClickListener(v -> {
            Intent intent = new Intent(SectionDetailActivity.this, SectionInfoActivity.class);
            intent.putExtra(SectionInfoActivity.EXTRA_BA_ID, baId);
            intent.putExtra(SectionInfoActivity.EXTRA_BA_NAME, baName);
            startActivity(intent);
        });
        btnFollow.setOnClickListener(v -> toggleFollowState());

        fetchSectionInfo();
        loadPosts(true, false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchSectionInfo();
    }

    private void loadMore() {
        if (isLoading || hasReachedEnd) return;
        currentPage++;
        loadPosts(false, true);
    }

    private void fetchSectionInfo() {
        String token = PrefUtils.getToken(this);
        if (token == null || token.isEmpty() || baId <= 0) return;

        communityRepository.getBaInfo(token, baId, new CommunityRepository.StringCallback() {
            @Override
            public void onSuccess(String responseBody) {
                runOnUiThread(() -> {
                    try {
                        JSONObject root = new JSONObject(responseBody);
                        if (root.optInt("code", 0) == 1 && root.has("data")) {
                            JSONObject data = root.getJSONObject("data");
                            if (data.has("ba")) {
                                currentBaModel = CommunityBaModel.fromJson(data.getJSONObject("ba"));
                                if (currentBaModel != null) {
                                    if (currentBaModel.getName() != null && !currentBaModel.getName().isEmpty()) {
                                        tvTitle.setText(currentBaModel.getName());
                                        tvBaName.setText(currentBaModel.getName());
                                    }
                                    tvBaStats.setText(currentBaModel.getStatsText(SectionDetailActivity.this));
                                    ImageUtils.loadAvatar(SectionDetailActivity.this, currentBaModel.getAvatar(), ivBaAvatar);
                                    updateFollowButtonUI();
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }
                });
            }

            @Override
            public void onError(String msg) {
            }
        });
    }

    private void updateFollowButtonUI() {
        if (btnFollow == null || currentBaModel == null) return;
        btnFollow.setEnabled(true);
        boolean isFollowed = "1".equals(currentBaModel.getIsFollowed());
        int strokeWidth1dp = (int) (1 * getResources().getDisplayMetrics().density);
        if (isFollowed) {
            btnFollow.setText(R.string.action_unfollow);
            btnFollow.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
            btnFollow.setTextColor(getResources().getColor(R.color.text_secondary));
            btnFollow.setStrokeColor(ColorStateList.valueOf(0xFFCCCCCC));
            btnFollow.setStrokeWidth(strokeWidth1dp);
        } else {
            btnFollow.setText(R.string.action_follow);
            btnFollow.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.app_primary)));
            btnFollow.setTextColor(Color.WHITE);
            btnFollow.setStrokeWidth(0);
        }
    }

    private void toggleFollowState() {
        if (currentBaModel == null) return;
        String token = PrefUtils.getToken(this);
        if (token == null || token.isEmpty()) return;

        btnFollow.setEnabled(false);
        boolean isCurrentlyFollowed = "1".equals(currentBaModel.getIsFollowed());

        if (isCurrentlyFollowed) {
            communityRepository.unfollowBa(token, baId, new CommunityRepository.SimpleCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {
                        currentBaModel.setIsFollowed("0");
                        currentBaModel.setMemberNum(Math.max(0, currentBaModel.getMemberNum() - 1));
                        tvBaStats.setText(currentBaModel.getStatsText(SectionDetailActivity.this));
                        updateFollowButtonUI();
                        Toast.makeText(SectionDetailActivity.this, R.string.unfollow_success, Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onError(String msg) {
                    runOnUiThread(() -> {
                        btnFollow.setEnabled(true);
                        Toast.makeText(SectionDetailActivity.this, getString(R.string.follow_failed, msg), Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } else {
            communityRepository.followBa(token, baId, new CommunityRepository.SimpleCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {
                        currentBaModel.setIsFollowed("1");
                        currentBaModel.setMemberNum(currentBaModel.getMemberNum() + 1);
                        tvBaStats.setText(currentBaModel.getStatsText(SectionDetailActivity.this));
                        updateFollowButtonUI();
                        Toast.makeText(SectionDetailActivity.this, R.string.follow_success, Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onError(String msg) {
                    runOnUiThread(() -> {
                        btnFollow.setEnabled(true);
                        Toast.makeText(SectionDetailActivity.this, getString(R.string.follow_failed, msg), Toast.LENGTH_SHORT).show();
                    });
                }
            });
        }
    }

    private void showFilterDialog() {
        String[] options = new String[]{
                getString(R.string.community_filter_recommend), // 热门 typ = 1
                getString(R.string.community_filter_latest),    // 最新 typ = 4
                getString(R.string.community_filter_reverse)     // 倒序 typ = 3
        };

        int selectedIndex;
        if (currentTyp == 4) {
            selectedIndex = 1;
        } else if (currentTyp == 3) {
            selectedIndex = 2;
        } else {
            selectedIndex = 0;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.community_filter_title)
                .setSingleChoiceItems(options, selectedIndex, (dialog, which) -> {
                    int newTyp;
                    if (which == 1) {
                        newTyp = 4;
                    } else if (which == 2) {
                        newTyp = 3;
                    } else {
                        newTyp = 1;
                    }
                    if (currentTyp != newTyp) {
                        currentTyp = newTyp;
                        currentPage = 1;
                        hasReachedEnd = false;
                        loadPosts(true, false);
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.dialog_cancel, (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void loadPosts(boolean showProgress, boolean isLoadMore) {
        String token = PrefUtils.getToken(this);
        if (token == null || token.isEmpty() || baId <= 0) {
            if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
            return;
        }

        isLoading = true;
        if (showProgress && progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }

        if (isLoadMore && postsAdapter != null) {
            postsAdapter.setFooterState(CommunityPostsAdapter.STATE_LOADING);
        }

        communityRepository.getPostList(token, currentTyp, baId, currentPage, pageSize, new CommunityRepository.StringCallback() {
            @Override
            public void onSuccess(String responseBody) {
                runOnUiThread(() -> {
                    isLoading = false;
                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                    if (progressBar != null) progressBar.setVisibility(View.GONE);

                    List<CommunityPostModel> newPosts = parsePostsJson(responseBody);
                    if (isLoadMore) {
                        if (newPosts.isEmpty()) {
                            hasReachedEnd = true;
                            if (postsAdapter != null) postsAdapter.setFooterState(CommunityPostsAdapter.STATE_NO_MORE);
                        } else {
                            if (postsAdapter != null) {
                                postsAdapter.addPosts(newPosts);
                                postsAdapter.setFooterState(CommunityPostsAdapter.STATE_CLICK_TO_LOAD);
                            }
                        }
                    } else {
                        if (postsAdapter != null) {
                            postsAdapter.setPosts(newPosts);
                            if (rvPosts != null) rvPosts.scrollToPosition(0);
                            if (newPosts.isEmpty()) {
                                hasReachedEnd = true;
                                postsAdapter.setFooterState(CommunityPostsAdapter.STATE_NO_MORE);
                            } else {
                                postsAdapter.setFooterState(CommunityPostsAdapter.STATE_CLICK_TO_LOAD);
                            }
                        }
                    }
                });
            }

            @Override
            public void onError(String msg) {
                runOnUiThread(() -> {
                    isLoading = false;
                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    Toast.makeText(SectionDetailActivity.this, R.string.post_load_failed, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private List<CommunityPostModel> parsePostsJson(String jsonStr) {
        List<CommunityPostModel> list = new ArrayList<>();
        if (jsonStr == null || jsonStr.isEmpty()) return list;

        try {
            JSONObject root = new JSONObject(jsonStr);
            if (root.optInt("code", 0) == 1 && root.has("data")) {
                JSONObject data = root.getJSONObject("data");
                if (data.has("posts")) {
                    JSONArray postsArr = data.getJSONArray("posts");
                    for (int i = 0; i < postsArr.length(); i++) {
                        JSONObject postObj = postsArr.optJSONObject(i);
                        if (postObj != null) {
                            CommunityPostModel model = CommunityPostModel.fromJson(postObj);
                            if (model != null) list.add(model);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }
}

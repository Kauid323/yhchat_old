package com.nago8.chat.old;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.tabs.TabLayout;
import com.nago8.chat.old.adapter.SectionGroupsAdapter;
import com.nago8.chat.old.model.BaUserModel;
import com.nago8.chat.old.model.CommunityBaModel;
import com.nago8.chat.old.model.SectionGroupModel;
import com.nago8.chat.old.repository.CommunityRepository;
import com.nago8.chat.old.utils.ImageUtils;
import com.nago8.chat.old.utils.LocaleHelper;
import com.nago8.chat.old.utils.PrefUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class SectionInfoActivity extends AppCompatActivity {

    public static final String EXTRA_BA_ID = "ba_id";
    public static final String EXTRA_BA_NAME = "ba_name";

    private int baId;
    private String baName;

    private TextView tvTitle;
    private TabLayout tabLayout;
    private ScrollView layoutTabDetails;
    private View layoutTabGroups;
    private ProgressBar progressBar;

    // Tab 0 Views (Details)
    private ShapeableImageView ivBaAvatar;
    private TextView tvBaName;
    private TextView tvBaStats;
    private MaterialButton btnFollow;

    private TextView tvFieldId;
    private TextView tvFieldMembers;
    private TextView tvFieldPosts;
    private TextView tvFieldGroups;
    private TextView tvFieldCreateTime;
    private TextView tvFieldFollowed;

    private View cardCreator;
    private ShapeableImageView ivCreatorAvatar;
    private TextView tvCreatorName;
    private View cardManagers;
    private LinearLayout containerManagers;

    // Tab 1 Views (Bound Groups)
    private SwipeRefreshLayout swipeRefreshGroups;
    private RecyclerView rvGroups;
    private TextView tvNoGroups;
    private SectionGroupsAdapter groupsAdapter;

    private CommunityRepository communityRepository;
    private CommunityBaModel currentBaModel;
    private BaUserModel baCreator;
    private final List<BaUserModel> baManagers = new ArrayList<>();

    private boolean isGroupsLoaded = false;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_section_info);

        baId = getIntent().getIntExtra(EXTRA_BA_ID, 0);
        baName = getIntent().getStringExtra(EXTRA_BA_NAME);

        communityRepository = new CommunityRepository();

        AppCompatImageButton btnBack = findViewById(R.id.btnBack);
        tvTitle = findViewById(R.id.tvTitle);
        tabLayout = findViewById(R.id.tabLayout);
        layoutTabDetails = findViewById(R.id.layoutTabDetails);
        layoutTabGroups = findViewById(R.id.layoutTabGroups);
        progressBar = findViewById(R.id.progressBar);

        // Tab 0 UI
        ivBaAvatar = findViewById(R.id.ivBaAvatar);
        tvBaName = findViewById(R.id.tvBaName);
        tvBaStats = findViewById(R.id.tvBaStats);
        btnFollow = findViewById(R.id.btnFollow);

        ivBaAvatar.setOnClickListener(v -> {
            if (currentBaModel != null && currentBaModel.getAvatar() != null && !currentBaModel.getAvatar().trim().isEmpty()) {
                android.content.Intent intent = new android.content.Intent(this, ImagePreviewActivity.class);
                intent.putExtra(ImagePreviewActivity.EXTRA_IMAGE_URL, currentBaModel.getAvatar());
                startActivity(intent);
            }
        });

        tvFieldId = findViewById(R.id.tvFieldId);
        tvFieldMembers = findViewById(R.id.tvFieldMembers);
        tvFieldPosts = findViewById(R.id.tvFieldPosts);
        tvFieldGroups = findViewById(R.id.tvFieldGroups);
        tvFieldCreateTime = findViewById(R.id.tvFieldCreateTime);
        tvFieldFollowed = findViewById(R.id.tvFieldFollowed);

        cardCreator = findViewById(R.id.cardCreator);
        ivCreatorAvatar = findViewById(R.id.ivCreatorAvatar);
        tvCreatorName = findViewById(R.id.tvCreatorName);
        cardManagers = findViewById(R.id.cardManagers);
        containerManagers = findViewById(R.id.containerManagers);

        // Tab 1 UI
        swipeRefreshGroups = findViewById(R.id.swipeRefreshGroups);
        rvGroups = findViewById(R.id.rvGroups);
        tvNoGroups = findViewById(R.id.tvNoGroups);

        if (baName != null && !baName.isEmpty()) {
            tvTitle.setText(baName);
            tvBaName.setText(baName);
        } else {
            tvTitle.setText(R.string.section_info_title);
        }

        btnBack.setOnClickListener(v -> onBackPressed());
        btnFollow.setOnClickListener(v -> toggleFollowState());

        // Setup Tabs
        TabLayout.Tab tabDetails = tabLayout.newTab().setText(R.string.tab_section_details);
        TabLayout.Tab tabGroups = tabLayout.newTab().setText(R.string.tab_section_groups);
        tabLayout.addTab(tabDetails);
        tabLayout.addTab(tabGroups);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    layoutTabDetails.setVisibility(View.VISIBLE);
                    layoutTabGroups.setVisibility(View.GONE);
                } else {
                    layoutTabDetails.setVisibility(View.GONE);
                    layoutTabGroups.setVisibility(View.VISIBLE);
                    if (!isGroupsLoaded) {
                        fetchBoundGroups();
                    }
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        // Setup Groups RecyclerView
        groupsAdapter = new SectionGroupsAdapter(this);
        rvGroups.setLayoutManager(new LinearLayoutManager(this));
        rvGroups.setAdapter(groupsAdapter);

        swipeRefreshGroups.setOnRefreshListener(this::fetchBoundGroups);

        fetchSectionInfo();
    }

    private void fetchSectionInfo() {
        String token = PrefUtils.getToken(this);
        if (token == null || token.isEmpty() || baId <= 0) return;

        progressBar.setVisibility(View.VISIBLE);

        communityRepository.getBaInfo(token, baId, new CommunityRepository.StringCallback() {
            @Override
            public void onSuccess(String responseBody) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    try {
                        JSONObject root = new JSONObject(responseBody);
                        if (root.optInt("code", 0) == 1 && root.has("data")) {
                            JSONObject data = root.getJSONObject("data");
                            if (data.has("ba")) {
                                currentBaModel = CommunityBaModel.fromJson(data.getJSONObject("ba"));
                            }
                            if (data.has("baCreater") && !data.isNull("baCreater")) {
                                baCreator = BaUserModel.fromJson(data.getJSONObject("baCreater"));
                            }
                            if (data.has("baMangers") && !data.isNull("baMangers")) {
                                baManagers.clear();
                                JSONArray mgrArr = data.getJSONArray("baMangers");
                                for (int i = 0; i < mgrArr.length(); i++) {
                                    JSONObject obj = mgrArr.optJSONObject(i);
                                    if (obj != null) {
                                        BaUserModel mgr = BaUserModel.fromJson(obj);
                                        if (mgr != null) baManagers.add(mgr);
                                    }
                                }
                            }
                            updateSectionUI();
                        }
                    } catch (Exception ignored) {
                    }
                });
            }

            @Override
            public void onError(String msg) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(SectionInfoActivity.this, R.string.section_info_load_failed, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void updateSectionUI() {
        if (currentBaModel == null) return;

        if (currentBaModel.getName() != null && !currentBaModel.getName().isEmpty()) {
            tvTitle.setText(currentBaModel.getName());
            tvBaName.setText(currentBaModel.getName());
        }

        tvBaStats.setText(currentBaModel.getStatsText(this));
        ImageUtils.loadAvatar(this, currentBaModel.getAvatar(), ivBaAvatar);

        tvFieldId.setText(getString(R.string.section_id_format, currentBaModel.getId()));
        tvFieldMembers.setText(getString(R.string.section_members_format, currentBaModel.getMemberNum()));
        tvFieldPosts.setText(getString(R.string.section_posts_format, currentBaModel.getPostNum()));
        tvFieldGroups.setText(getString(R.string.section_groups_format, currentBaModel.getGroupNum()));

        if (!currentBaModel.getCreateTimeText().isEmpty()) {
            tvFieldCreateTime.setText(getString(R.string.section_create_time_label, currentBaModel.getCreateTimeText()));
            tvFieldCreateTime.setVisibility(View.VISIBLE);
        } else {
            tvFieldCreateTime.setVisibility(View.GONE);
        }

        String followedStr = "1".equals(currentBaModel.getIsFollowed())
                ? getString(R.string.section_followed_yes) : getString(R.string.section_followed_no);
        tvFieldFollowed.setText(getString(R.string.section_followed_label, followedStr));

        // Creator Card
        if (baCreator != null) {
            cardCreator.setVisibility(View.VISIBLE);
            tvCreatorName.setText(baCreator.getNickname());
            ImageUtils.loadAvatar(this, baCreator.getAvatarUrl(), ivCreatorAvatar);

            ivCreatorAvatar.setOnClickListener(v -> {
                if (baCreator != null && baCreator.getAvatarUrl() != null && !baCreator.getAvatarUrl().isEmpty()) {
                    android.content.Intent intent = new android.content.Intent(this, ImagePreviewActivity.class);
                    intent.putExtra(ImagePreviewActivity.EXTRA_IMAGE_URL, baCreator.getAvatarUrl());
                    startActivity(intent);
                }
            });

            View layoutCreatorItem = findViewById(R.id.layoutCreatorItem);
            if (layoutCreatorItem != null) {
                final String creatorUserId = baCreator.getUserId();
                layoutCreatorItem.setOnClickListener(v -> {
                    if (!android.text.TextUtils.isEmpty(creatorUserId)) {
                        android.content.Intent intent = new android.content.Intent(SectionInfoActivity.this, UserProfileActivity.class);
                        intent.putExtra(UserProfileActivity.EXTRA_USER_ID, creatorUserId);
                        startActivity(intent);
                    }
                });
            }
        } else {
            cardCreator.setVisibility(View.GONE);
        }

        // Managers Card
        if (baManagers != null && !baManagers.isEmpty()) {
            cardManagers.setVisibility(View.VISIBLE);
            containerManagers.removeAllViews();
            for (BaUserModel mgr : baManagers) {
                addManagerItemView(mgr);
            }
        } else {
            cardManagers.setVisibility(View.GONE);
        }

        updateFollowButtonUI();
    }

    private void addManagerItemView(BaUserModel mgr) {
        if (mgr == null) return;
        android.widget.LinearLayout row = new android.widget.LinearLayout(this);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));
        row.setClickable(true);
        row.setFocusable(true);

        ShapeableImageView iv = new ShapeableImageView(this);
        android.widget.LinearLayout.LayoutParams ivParams = new android.widget.LinearLayout.LayoutParams(dp(40), dp(40));
        iv.setLayoutParams(ivParams);
        iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        iv.setShapeAppearanceModel(iv.getShapeAppearanceModel().toBuilder()
                .setAllCornerSizes(com.google.android.material.shape.CornerFamily.ROUNDED)
                .build());

        ImageUtils.loadAvatar(this, mgr.getAvatarUrl(), iv);
        iv.setOnClickListener(v -> {
            if (mgr.getAvatarUrl() != null && !mgr.getAvatarUrl().isEmpty()) {
                android.content.Intent intent = new android.content.Intent(this, ImagePreviewActivity.class);
                intent.putExtra(ImagePreviewActivity.EXTRA_IMAGE_URL, mgr.getAvatarUrl());
                startActivity(intent);
            }
        });

        TextView tvName = new TextView(this);
        tvName.setText(mgr.getNickname());
        tvName.setTextSize(15f);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvName.setTextColor(getResources().getColor(R.color.text_primary));
        android.widget.LinearLayout.LayoutParams tvParams = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        tvParams.leftMargin = dp(12);
        tvName.setLayoutParams(tvParams);

        row.addView(iv);
        row.addView(tvName);

        final String userId = mgr.getUserId();
        row.setOnClickListener(v -> {
            if (!android.text.TextUtils.isEmpty(userId)) {
                android.content.Intent intent = new android.content.Intent(SectionInfoActivity.this, UserProfileActivity.class);
                intent.putExtra(UserProfileActivity.EXTRA_USER_ID, userId);
                startActivity(intent);
            }
        });

        containerManagers.addView(row);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
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
                        updateSectionUI();
                        Toast.makeText(SectionInfoActivity.this, R.string.unfollow_success, Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onError(String msg) {
                    runOnUiThread(() -> {
                        btnFollow.setEnabled(true);
                        Toast.makeText(SectionInfoActivity.this, getString(R.string.follow_failed, msg), Toast.LENGTH_SHORT).show();
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
                        updateSectionUI();
                        Toast.makeText(SectionInfoActivity.this, R.string.follow_success, Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onError(String msg) {
                    runOnUiThread(() -> {
                        btnFollow.setEnabled(true);
                        Toast.makeText(SectionInfoActivity.this, getString(R.string.follow_failed, msg), Toast.LENGTH_SHORT).show();
                    });
                }
            });
        }
    }

    private void fetchBoundGroups() {
        String token = PrefUtils.getToken(this);
        if (token == null || token.isEmpty() || baId <= 0) {
            if (swipeRefreshGroups != null) swipeRefreshGroups.setRefreshing(false);
            return;
        }

        if (swipeRefreshGroups != null && !swipeRefreshGroups.isRefreshing()) {
            swipeRefreshGroups.setRefreshing(true);
        }

        communityRepository.getBaGroupList(token, baId, 1, 50, new CommunityRepository.StringCallback() {
            @Override
            public void onSuccess(String responseBody) {
                runOnUiThread(() -> {
                    isGroupsLoaded = true;
                    if (swipeRefreshGroups != null) swipeRefreshGroups.setRefreshing(false);
                    List<SectionGroupModel> groups = parseGroupsJson(responseBody);
                    if (groups.isEmpty()) {
                        tvNoGroups.setVisibility(View.VISIBLE);
                        rvGroups.setVisibility(View.GONE);
                    } else {
                        tvNoGroups.setVisibility(View.GONE);
                        rvGroups.setVisibility(View.VISIBLE);
                        groupsAdapter.setGroups(groups);
                    }
                });
            }

            @Override
            public void onError(String msg) {
                runOnUiThread(() -> {
                    if (swipeRefreshGroups != null) swipeRefreshGroups.setRefreshing(false);
                    tvNoGroups.setVisibility(View.VISIBLE);
                    rvGroups.setVisibility(View.GONE);
                });
            }
        });
    }

    private List<SectionGroupModel> parseGroupsJson(String jsonStr) {
        List<SectionGroupModel> list = new ArrayList<>();
        if (jsonStr == null || jsonStr.isEmpty()) return list;

        try {
            JSONObject root = new JSONObject(jsonStr);
            if (root.optInt("code", 0) == 1 && root.has("data")) {
                JSONObject data = root.getJSONObject("data");
                if (data.has("groups")) {
                    JSONArray arr = data.getJSONArray("groups");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.optJSONObject(i);
                        if (obj != null) {
                            SectionGroupModel model = SectionGroupModel.fromJson(obj);
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

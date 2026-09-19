package com.nago8.chat.old;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
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

import com.nago8.chat.old.adapter.CommunitySectionsAdapter;
import com.nago8.chat.old.model.CommunityBaModel;
import com.nago8.chat.old.repository.CommunityRepository;
import com.nago8.chat.old.utils.LocaleHelper;
import com.nago8.chat.old.utils.PrefUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;

public class MovePostActivity extends AppCompatActivity {

    public static final String EXTRA_POST_ID = "post_id";
    public static final String EXTRA_CURRENT_BA_ID = "current_ba_id";
    public static final String EXTRA_CURRENT_BA_NAME = "current_ba_name";

    public static final String EXTRA_NEW_BA_ID = "new_ba_id";
    public static final String EXTRA_NEW_BA_NAME = "new_ba_name";

    private long postId;
    private int currentBaId;
    private String currentBaName;

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView rvSections;
    private TextView tvEmpty;
    private ProgressBar progressBar;
    private TextView tvCurrentSection;

    private CommunitySectionsAdapter sectionsAdapter;
    private CommunityRepository communityRepository;
    private Call fetchCall;

    private int currentPage = 1;
    private static final int PAGE_SIZE = 50;
    private boolean isLoading = false;
    private boolean hasReachedEnd = false;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_move_post);

        postId = getIntent().getLongExtra(EXTRA_POST_ID, 0);
        currentBaId = getIntent().getIntExtra(EXTRA_CURRENT_BA_ID, 0);
        currentBaName = getIntent().getStringExtra(EXTRA_CURRENT_BA_NAME);

        if (postId <= 0) {
            finish();
            return;
        }

        communityRepository = new CommunityRepository();

        AppCompatImageButton btnBack = findViewById(R.id.btnBack);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        rvSections = findViewById(R.id.rvSections);
        tvEmpty = findViewById(R.id.tvEmpty);
        progressBar = findViewById(R.id.progressBar);
        tvCurrentSection = findViewById(R.id.tvCurrentSection);

        btnBack.setOnClickListener(v -> onBackPressed());

        if (!TextUtils.isEmpty(currentBaName)) {
            tvCurrentSection.setText(getString(R.string.move_post_current_section, currentBaName));
        } else if (currentBaId > 0) {
            tvCurrentSection.setText(getString(R.string.move_post_current_section, "ID: " + currentBaId));
        } else {
            tvCurrentSection.setText(getString(R.string.move_post_current_section, "-"));
        }

        sectionsAdapter = new CommunitySectionsAdapter(this);
        sectionsAdapter.setOnSectionClickListener(this::onTargetSectionSelected);
        sectionsAdapter.setOnLoadMoreClickListener(this::loadMore);

        rvSections.setLayoutManager(new LinearLayoutManager(this));
        rvSections.setAdapter(sectionsAdapter);

        swipeRefresh.setOnRefreshListener(() -> {
            currentPage = 1;
            hasReachedEnd = false;
            loadFollowedSections(false);
        });

        rvSections.addOnScrollListener(new RecyclerView.OnScrollListener() {
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

        loadFollowedSections(true);
    }

    @Override
    protected void onDestroy() {
        if (fetchCall != null) fetchCall.cancel();
        super.onDestroy();
    }

    private void loadFollowedSections(boolean showProgress) {
        if (isLoading) return;
        String token = PrefUtils.getToken(this);
        if (TextUtils.isEmpty(token)) {
            Toast.makeText(this, R.string.not_logged_in, Toast.LENGTH_SHORT).show();
            return;
        }

        isLoading = true;
        if (showProgress && currentPage == 1) {
            progressBar.setVisibility(View.VISIBLE);
        }

        if (fetchCall != null) fetchCall.cancel();
        // typ = 1: 获取已关注板块列表
        fetchCall = communityRepository.getBaList(token, 1, currentPage, PAGE_SIZE, new CommunityRepository.StringCallback() {
            @Override
            public void onSuccess(String responseBody) {
                runOnUiThread(() -> {
                    isLoading = false;
                    swipeRefresh.setRefreshing(false);
                    progressBar.setVisibility(View.GONE);
                    parseAndBindSections(responseBody);
                });
            }

            @Override
            public void onError(String msg) {
                runOnUiThread(() -> {
                    isLoading = false;
                    swipeRefresh.setRefreshing(false);
                    progressBar.setVisibility(View.GONE);
                    String errorText = !TextUtils.isEmpty(msg) ? msg : getString(R.string.network_error);
                    Toast.makeText(MovePostActivity.this, errorText, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void loadMore() {
        if (isLoading || hasReachedEnd) return;
        sectionsAdapter.setFooterState(CommunitySectionsAdapter.STATE_LOADING);
        currentPage++;
        loadFollowedSections(false);
    }

    private void parseAndBindSections(String json) {
        List<CommunityBaModel> newSections = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(json);
            if (root.optInt("code", 0) == 1 && root.has("data")) {
                JSONObject data = root.getJSONObject("data");
                JSONArray array = data.optJSONArray("ba");
                if (array != null) {
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject obj = array.optJSONObject(i);
                        if (obj != null) {
                            CommunityBaModel model = CommunityBaModel.fromJson(obj);
                            if (model != null) newSections.add(model);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        if (currentPage == 1) {
            sectionsAdapter.setSections(newSections);
            if (newSections.isEmpty()) {
                tvEmpty.setVisibility(View.VISIBLE);
            } else {
                tvEmpty.setVisibility(View.GONE);
            }
        } else {
            sectionsAdapter.addSections(newSections);
        }

        if (newSections.size() < PAGE_SIZE) {
            hasReachedEnd = true;
            sectionsAdapter.setFooterState(CommunitySectionsAdapter.STATE_NO_MORE);
        } else {
            sectionsAdapter.setFooterState(CommunitySectionsAdapter.STATE_CLICK_TO_LOAD);
        }
    }

    private void onTargetSectionSelected(CommunityBaModel targetBa) {
        if (targetBa == null) return;

        if (targetBa.getId() == currentBaId) {
            Toast.makeText(this, R.string.move_post_already_in_section, Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.move_post_confirm_title)
                .setMessage(getString(R.string.move_post_confirm_msg, targetBa.getName()))
                .setPositiveButton(R.string.confirm, (dialog, which) -> executeMovePost(targetBa))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void executeMovePost(CommunityBaModel targetBa) {
        String token = PrefUtils.getToken(this);
        if (TextUtils.isEmpty(token)) {
            Toast.makeText(this, R.string.not_logged_in, Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        communityRepository.movePost(token, postId, targetBa.getId(), new CommunityRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(MovePostActivity.this, getString(R.string.move_post_success, targetBa.getName()), Toast.LENGTH_SHORT).show();
                    Intent result = new Intent();
                    result.putExtra(EXTRA_NEW_BA_ID, targetBa.getId());
                    result.putExtra(EXTRA_NEW_BA_NAME, targetBa.getName());
                    setResult(RESULT_OK, result);
                    finish();
                });
            }

            @Override
            public void onError(String msg) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    String err = !TextUtils.isEmpty(msg) ? msg : getString(R.string.operation_failed);
                    Toast.makeText(MovePostActivity.this, getString(R.string.move_post_failed, err), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
}

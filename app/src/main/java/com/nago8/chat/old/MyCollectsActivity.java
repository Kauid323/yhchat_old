package com.nago8.chat.old;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.nago8.chat.old.adapter.CommunityPostsAdapter;
import com.nago8.chat.old.model.CommunityPostModel;
import com.nago8.chat.old.repository.CommunityRepository;
import com.nago8.chat.old.utils.LocaleHelper;
import com.nago8.chat.old.utils.PrefUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;

public class MyCollectsActivity extends AppCompatActivity {

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView rvPosts;
    private TextView tvEmpty;
    private ProgressBar progressBar;

    private CommunityPostsAdapter postsAdapter;
    private CommunityRepository communityRepository;

    private Call fetchCall;
    private int currentPage = 1;
    private static final int PAGE_SIZE = 20;
    private boolean isLoading = false;
    private boolean hasReachedEnd = false;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_posts);

        communityRepository = new CommunityRepository();

        AppCompatImageButton btnBack = findViewById(R.id.btnBack);
        TextView tvTitle = findViewById(R.id.tvTitle);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        rvPosts = findViewById(R.id.rvPosts);
        tvEmpty = findViewById(R.id.tvEmpty);
        progressBar = findViewById(R.id.progressBar);

        tvTitle.setText(R.string.my_collects_title);
        tvEmpty.setText(R.string.my_collects_empty);

        btnBack.setOnClickListener(v -> onBackPressed());

        postsAdapter = new CommunityPostsAdapter(this);
        rvPosts.setLayoutManager(new LinearLayoutManager(this));
        rvPosts.setAdapter(postsAdapter);

        swipeRefresh.setOnRefreshListener(() -> {
            currentPage = 1;
            hasReachedEnd = false;
            loadPosts(false);
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

        loadPosts(true);
    }

    @Override
    protected void onDestroy() {
        if (fetchCall != null) fetchCall.cancel();
        super.onDestroy();
    }

    private void loadPosts(boolean showLoadingProgress) {
        if (isLoading) return;
        String token = PrefUtils.getToken(this);
        if (token == null || token.isEmpty()) return;

        isLoading = true;
        if (showLoadingProgress && currentPage == 1) {
            progressBar.setVisibility(View.VISIBLE);
        }

        if (fetchCall != null) fetchCall.cancel();
        fetchCall = communityRepository.getMyCollectList(token, currentPage, PAGE_SIZE, new CommunityRepository.StringCallback() {
            @Override
            public void onSuccess(String responseBody) {
                runOnUiThread(() -> {
                    isLoading = false;
                    swipeRefresh.setRefreshing(false);
                    progressBar.setVisibility(View.GONE);
                    parseAndBindPosts(responseBody);
                });
            }

            @Override
            public void onError(String msg) {
                runOnUiThread(() -> {
                    isLoading = false;
                    swipeRefresh.setRefreshing(false);
                    progressBar.setVisibility(View.GONE);
                    String errorText = (msg != null && !msg.isEmpty()) ? msg : getString(R.string.network_error);
                    Toast.makeText(MyCollectsActivity.this, errorText, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void loadMore() {
        if (isLoading || hasReachedEnd) return;
        postsAdapter.setFooterState(CommunityPostsAdapter.STATE_LOADING);
        currentPage++;
        loadPosts(false);
    }

    private void parseAndBindPosts(String json) {
        List<CommunityPostModel> newPosts = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(json);
            if (root.optInt("code", 0) == 1 && root.has("data")) {
                JSONObject data = root.getJSONObject("data");
                if (data.has("posts")) {
                    JSONArray array = data.getJSONArray("posts");
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject obj = array.optJSONObject(i);
                        if (obj != null) {
                            CommunityPostModel model = CommunityPostModel.fromJson(obj);
                            if (model != null) newPosts.add(model);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        if (currentPage == 1) {
            postsAdapter.setPosts(newPosts);
            if (newPosts.isEmpty()) {
                tvEmpty.setVisibility(View.VISIBLE);
            } else {
                tvEmpty.setVisibility(View.GONE);
            }
        } else {
            postsAdapter.addPosts(newPosts);
        }

        if (newPosts.size() < PAGE_SIZE) {
            hasReachedEnd = true;
            postsAdapter.setFooterState(CommunityPostsAdapter.STATE_NO_MORE);
        } else {
            postsAdapter.setFooterState(CommunityPostsAdapter.STATE_CLICK_TO_LOAD);
        }
    }
}

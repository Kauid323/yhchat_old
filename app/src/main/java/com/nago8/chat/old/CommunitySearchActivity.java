package com.nago8.chat.old;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.nago8.chat.old.adapter.CommunitySearchAdapter;
import com.nago8.chat.old.model.CommunityBaModel;
import com.nago8.chat.old.model.CommunityPostModel;
import com.nago8.chat.old.repository.CommunityRepository;
import com.nago8.chat.old.utils.LocaleHelper;
import com.nago8.chat.old.utils.PrefUtils;
import com.nago8.chat.old.utils.ThemeUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;

public class CommunitySearchActivity extends AppCompatActivity {

    private ImageView btnBack;
    private EditText etSearch;
    private ImageView ivClearSearch;
    private TextView btnSearch;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView rvResults;
    private View layoutEmpty;
    private TextView tvEmptyHint;
    private ProgressBar progressBar;

    private CommunitySearchAdapter adapter;
    private final CommunityRepository communityRepository = new CommunityRepository();
    private Call searchCall;

    private String currentKeyword = "";
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private final Runnable searchRunnable = this::performSearch;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_community_search);

        initViews();
        setupRecyclerView();
        setupListeners();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        etSearch = findViewById(R.id.etSearch);
        ivClearSearch = findViewById(R.id.ivClearSearch);
        btnSearch = findViewById(R.id.btnSearch);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        rvResults = findViewById(R.id.rvResults);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        tvEmptyHint = findViewById(R.id.tvEmptyHint);
        progressBar = findViewById(R.id.progressBar);

        int primaryColor = ThemeUtils.getThemeColor(this);
        btnSearch.setTextColor(primaryColor);
        swipeRefreshLayout.setColorSchemeColors(primaryColor);

        layoutEmpty.setVisibility(View.VISIBLE);
        tvEmptyHint.setText("输入关键词搜索社区分区和文章");
        rvResults.setVisibility(View.GONE);
    }

    private void setupRecyclerView() {
        adapter = new CommunitySearchAdapter(this);
        adapter.setOnItemClickListener(new CommunitySearchAdapter.OnItemClickListener() {
            @Override
            public void onBoardClick(CommunityBaModel board) {
                if (board == null) return;
                Intent intent = new Intent(CommunitySearchActivity.this, SectionDetailActivity.class);
                intent.putExtra(SectionDetailActivity.EXTRA_BA_ID, board.getId());
                intent.putExtra(SectionDetailActivity.EXTRA_BA_NAME, board.getName());
                startActivity(intent);
            }

            @Override
            public void onPostClick(CommunityPostModel post) {
                if (post == null) return;
                Intent intent = new Intent(CommunitySearchActivity.this, PostDetailActivity.class);
                intent.putExtra(PostDetailActivity.EXTRA_POST_ID, String.valueOf(post.getId()));
                intent.putExtra(PostDetailActivity.EXTRA_POST_TITLE, post.getTitle());
                startActivity(intent);
            }
        });

        rvResults.setLayoutManager(new LinearLayoutManager(this));
        rvResults.setAdapter(adapter);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnSearch.setOnClickListener(v -> {
            performSearch();
            hideKeyboard();
        });

        swipeRefreshLayout.setOnRefreshListener(this::performSearch);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s != null ? s.toString().trim() : "";
                ivClearSearch.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                currentKeyword = query;
                searchHandler.removeCallbacks(searchRunnable);
                if (query.isEmpty()) {
                    adapter.setData(new ArrayList<>(), new ArrayList<>());
                    rvResults.setVisibility(View.GONE);
                    layoutEmpty.setVisibility(View.VISIBLE);
                    tvEmptyHint.setText("输入关键词搜索社区分区和文章");
                } else {
                    searchHandler.postDelayed(searchRunnable, 350);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        ivClearSearch.setOnClickListener(v -> {
            etSearch.setText("");
            ivClearSearch.setVisibility(View.GONE);
            currentKeyword = "";
            adapter.setData(new ArrayList<>(), new ArrayList<>());
            rvResults.setVisibility(View.GONE);
            layoutEmpty.setVisibility(View.VISIBLE);
            tvEmptyHint.setText("输入关键词搜索社区分区和文章");
        });

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch();
                hideKeyboard();
                return true;
            }
            return false;
        });
    }

    private void performSearch() {
        if (TextUtils.isEmpty(currentKeyword)) {
            swipeRefreshLayout.setRefreshing(false);
            return;
        }

        String token = PrefUtils.getToken(this);
        if (token == null || token.isEmpty()) {
            swipeRefreshLayout.setRefreshing(false);
            return;
        }

        if (searchCall != null) {
            searchCall.cancel();
        }

        progressBar.setVisibility(View.VISIBLE);
        layoutEmpty.setVisibility(View.GONE);

        searchCall = communityRepository.searchCommunity(token, currentKeyword, 1, 50, new CommunityRepository.StringCallback() {
            @Override
            public void onSuccess(String responseBody) {
                runOnUiThread(() -> {
                    swipeRefreshLayout.setRefreshing(false);
                    progressBar.setVisibility(View.GONE);
                    try {
                        JSONObject root = new JSONObject(responseBody);
                        if (root.optInt("code", 0) == 1) {
                            JSONObject data = root.optJSONObject("data");
                            List<CommunityBaModel> boards = new ArrayList<>();
                            List<CommunityPostModel> posts = new ArrayList<>();

                            if (data != null) {
                                JSONArray baArr = data.optJSONArray("boards");
                                if (baArr != null) {
                                    for (int i = 0; i < baArr.length(); i++) {
                                        CommunityBaModel ba = CommunityBaModel.fromJson(baArr.optJSONObject(i));
                                        if (ba != null) boards.add(ba);
                                    }
                                }
                                JSONArray postArr = data.optJSONArray("posts");
                                if (postArr != null) {
                                    for (int i = 0; i < postArr.length(); i++) {
                                        CommunityPostModel post = CommunityPostModel.fromJson(postArr.optJSONObject(i));
                                        if (post != null) posts.add(post);
                                    }
                                }
                            }

                            adapter.setData(boards, posts);
                            if (boards.isEmpty() && posts.isEmpty()) {
                                rvResults.setVisibility(View.GONE);
                                layoutEmpty.setVisibility(View.VISIBLE);
                                tvEmptyHint.setText("未找到与 \"" + currentKeyword + "\" 相关的分区或文章");
                            } else {
                                rvResults.setVisibility(View.VISIBLE);
                                layoutEmpty.setVisibility(View.GONE);
                            }
                        } else {
                            rvResults.setVisibility(View.GONE);
                            layoutEmpty.setVisibility(View.VISIBLE);
                            tvEmptyHint.setText(root.optString("msg", "搜索失败"));
                        }
                    } catch (Exception e) {
                        rvResults.setVisibility(View.GONE);
                        layoutEmpty.setVisibility(View.VISIBLE);
                        tvEmptyHint.setText("解析搜索结果失败");
                    }
                });
            }

            @Override
            public void onError(String msg) {
                runOnUiThread(() -> {
                    swipeRefreshLayout.setRefreshing(false);
                    progressBar.setVisibility(View.GONE);
                    if (adapter.isEmpty()) {
                        rvResults.setVisibility(View.GONE);
                        layoutEmpty.setVisibility(View.VISIBLE);
                        tvEmptyHint.setText("搜索请求失败，请稍后重试");
                    } else {
                        Toast.makeText(CommunitySearchActivity.this, "搜索失败: " + msg, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && etSearch != null) {
                imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        searchHandler.removeCallbacks(searchRunnable);
        if (searchCall != null) searchCall.cancel();
    }
}

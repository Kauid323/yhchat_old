package com.nago8.chat.old;

import android.content.Context;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.tabs.TabLayout;
import com.nago8.chat.old.adapter.DiscoveryGroupAdapter;
import com.nago8.chat.old.cache.AddressBookCache;
import com.nago8.chat.old.model.DiscoveryModels;
import com.nago8.chat.old.proto.user.address_book_list;
import com.nago8.chat.old.repository.DiscoveryRepository;
import com.nago8.chat.old.repository.FriendRepository;
import com.nago8.chat.old.utils.LocaleHelper;
import com.nago8.chat.old.utils.PrefUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import okhttp3.Call;

public class GroupDiscoveryActivity extends AppCompatActivity {

    private Toolbar toolbar;
    private androidx.appcompat.widget.SearchView searchView;
    private TabLayout tabLayout;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView rvGroups;
    private View layoutEmpty;
    private TextView tvEmptyHint;
    private ProgressBar progressBar;

    private DiscoveryGroupAdapter adapter;
    private final DiscoveryRepository discoveryRepository = new DiscoveryRepository();
    private final FriendRepository friendRepository = new FriendRepository();

    private final List<String> categoryList = new ArrayList<>();
    private final List<DiscoveryModels.GroupItem> groupList = new ArrayList<>();
    private final Set<String> joinedChatIds = new HashSet<>();

    private String currentCategory = "";
    private String currentKeyword = "";

    private int currentPage = 1;
    private boolean isLoadingMore = false;
    private boolean hasMore = true;
    private static final int PAGE_SIZE = 30;

    private Call categoryCall;
    private Call groupCall;
    private Call addressBookCall;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private final Runnable searchRunnable = this::resetAndLoadGroups;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_discovery);

        initViews();
        setupRecyclerView();
        setupListeners();
        initJoinedChatIds();

        loadCategories();
        resetAndLoadGroups();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        searchView = findViewById(R.id.searchView);
        tabLayout = findViewById(R.id.tabLayout);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        rvGroups = findViewById(R.id.rvGroups);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        tvEmptyHint = findViewById(R.id.tvEmptyHint);
        progressBar = findViewById(R.id.progressBar);

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
        int primaryColor = com.nago8.chat.old.utils.ThemeUtils.getThemeColor(this);
        swipeRefreshLayout.setColorSchemeColors(primaryColor);
        tabLayout.setSelectedTabIndicatorColor(primaryColor);
        tabLayout.setTabTextColors(
                androidx.core.content.ContextCompat.getColor(this, R.color.text_secondary),
                primaryColor
        );
    }

    private void setupRecyclerView() {
        adapter = new DiscoveryGroupAdapter(this, true); // 使用瀑布流错位卡片
        adapter.setOnGroupActionListener(this::handleApplyGroup);

        com.nago8.chat.old.widget.SafeStaggeredGridLayoutManager staggeredManager = new com.nago8.chat.old.widget.SafeStaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        staggeredManager.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_NONE);
        rvGroups.setLayoutManager(staggeredManager);
        rvGroups.setAdapter(adapter);
    }

    private void setupListeners() {
        swipeRefreshLayout.setOnRefreshListener(() -> {
            syncAddressBook();
            resetAndLoadGroups();
        });

        rvGroups.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy <= 0) return;
                StaggeredGridLayoutManager lm = (StaggeredGridLayoutManager) recyclerView.getLayoutManager();
                if (lm == null) return;
                int[] lastPositions = lm.findLastVisibleItemPositions(null);
                int maxPos = 0;
                for (int pos : lastPositions) {
                    if (pos > maxPos) maxPos = pos;
                }
                int totalCount = adapter.getItemCount();
                if (maxPos >= totalCount - 4 && !isLoadingMore && hasMore && !swipeRefreshLayout.isRefreshing()) {
                    currentPage++;
                    loadGroups(true);
                }
            }
        });

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int position = tab.getPosition();
                if (position == 0) {
                    currentCategory = "";
                } else if (position - 1 < categoryList.size()) {
                    currentCategory = categoryList.get(position - 1);
                }
                resetAndLoadGroups();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        if (searchView != null) {
            searchView.setOnQueryTextListener(new androidx.appcompat.widget.SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    currentKeyword = query != null ? query.trim() : "";
                    searchHandler.removeCallbacks(searchRunnable);
                    resetAndLoadGroups();
                    searchView.clearFocus();
                    return true;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    currentKeyword = newText != null ? newText.trim() : "";
                    searchHandler.removeCallbacks(searchRunnable);
                    searchHandler.postDelayed(searchRunnable, 400);
                    return true;
                }
            });
        }
    }

    private void initJoinedChatIds() {
        Set<String> cachedIds = AddressBookCache.getAllChatIds(this);
        if (cachedIds != null && !cachedIds.isEmpty()) {
            joinedChatIds.clear();
            joinedChatIds.addAll(cachedIds);
            adapter.setJoinedChatIds(joinedChatIds);
        }
        syncAddressBook();
    }

    private void syncAddressBook() {
        String token = PrefUtils.getToken(this);
        if (token == null || token.isEmpty()) return;

        if (addressBookCall != null) addressBookCall.cancel();
        addressBookCall = friendRepository.getAddressBook(token, "", new FriendRepository.AddressBookCallback() {
            @Override
            public void onSuccess(address_book_list response) {
                if (response != null && response.data != null) {
                    AddressBookCache.saveCache(GroupDiscoveryActivity.this, response.data);
                    Set<String> updatedSet = AddressBookCache.getAllChatIds(GroupDiscoveryActivity.this);
                    if (updatedSet != null) {
                        joinedChatIds.clear();
                        joinedChatIds.addAll(updatedSet);
                        runOnUiThread(() -> adapter.setJoinedChatIds(joinedChatIds));
                    }
                }
            }

            @Override
            public void onError(Exception e) {}
        });
    }

    private void loadCategories() {
        String token = PrefUtils.getToken(this);
        if (token == null || token.isEmpty()) return;

        if (categoryCall != null) categoryCall.cancel();
        categoryCall = discoveryRepository.getRecommendCategoryList(token, new DiscoveryRepository.CallbackResult<List<String>>() {
            @Override
            public void onSuccess(List<String> result) {
                runOnUiThread(() -> {
                    categoryList.clear();
                    tabLayout.removeAllTabs();
                    tabLayout.addTab(tabLayout.newTab().setText("全部"));
                    if (result != null && !result.isEmpty()) {
                        categoryList.addAll(result);
                        for (String catName : result) {
                            tabLayout.addTab(tabLayout.newTab().setText(catName));
                        }
                    }
                });
            }

            @Override
            public void onError(Exception e) {}
        });
    }

    private void resetAndLoadGroups() {
        currentPage = 1;
        hasMore = true;
        isLoadingMore = false;
        adapter.setHasMore(true);
        adapter.setLoadingMore(false);
        loadGroups(false);
    }

    private void loadGroups(boolean isLoadMore) {
        String token = PrefUtils.getToken(this);
        if (token == null || token.isEmpty()) {
            swipeRefreshLayout.setRefreshing(false);
            return;
        }

        if (isLoadMore) {
            if (isLoadingMore || !hasMore) return;
            isLoadingMore = true;
            adapter.setLoadingMore(true);
        } else {
            progressBar.setVisibility(View.VISIBLE);
            layoutEmpty.setVisibility(View.GONE);
        }

        if (groupCall != null) groupCall.cancel();
        groupCall = discoveryRepository.getRecommendGroups(token, currentCategory, currentKeyword, PAGE_SIZE, currentPage, new DiscoveryRepository.CallbackResult<List<DiscoveryModels.GroupItem>>() {
            @Override
            public void onSuccess(List<DiscoveryModels.GroupItem> result) {
                runOnUiThread(() -> {
                    if (isLoadMore) {
                        isLoadingMore = false;
                        adapter.setLoadingMore(false);
                        if (result != null && !result.isEmpty()) {
                            groupList.addAll(result);
                            adapter.addData(result);
                            hasMore = result.size() >= PAGE_SIZE;
                            adapter.setHasMore(hasMore);
                        } else {
                            hasMore = false;
                            adapter.setHasMore(false);
                        }
                    } else {
                        swipeRefreshLayout.setRefreshing(false);
                        progressBar.setVisibility(View.GONE);
                        groupList.clear();
                        if (result != null && !result.isEmpty()) {
                            groupList.addAll(result);
                            adapter.setData(groupList);
                            hasMore = result.size() >= PAGE_SIZE;
                            adapter.setHasMore(hasMore);
                            layoutEmpty.setVisibility(View.GONE);
                            rvGroups.setVisibility(View.VISIBLE);
                        } else {
                            hasMore = false;
                            adapter.setHasMore(false);
                            adapter.setData(new ArrayList<>());
                            rvGroups.setVisibility(View.GONE);
                            layoutEmpty.setVisibility(View.VISIBLE);
                            tvEmptyHint.setText(TextUtils.isEmpty(currentKeyword) ? "当前分类下暂无公开群聊" : "未搜索到匹配的群聊");
                        }
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    if (isLoadMore) {
                        isLoadingMore = false;
                        adapter.setLoadingMore(false);
                        Toast.makeText(GroupDiscoveryActivity.this, "加载更多失败", Toast.LENGTH_SHORT).show();
                    } else {
                        swipeRefreshLayout.setRefreshing(false);
                        progressBar.setVisibility(View.GONE);
                        if (groupList.isEmpty()) {
                            rvGroups.setVisibility(View.GONE);
                            layoutEmpty.setVisibility(View.VISIBLE);
                            tvEmptyHint.setText("网络请求失败，请稍后重试");
                        }
                    }
                });
            }
        });
    }

    private void handleApplyGroup(DiscoveryModels.GroupItem group) {
        if (group == null || TextUtils.isEmpty(group.groupId)) return;
        String token = PrefUtils.getToken(this);
        if (token == null || token.isEmpty()) return;

        Toast.makeText(this, "正在申请加入群聊...", Toast.LENGTH_SHORT).show();
        friendRepository.applyFriend(token, group.groupId, 2, "", new FriendRepository.ApplyFriendCallback() {
            @Override
            public void onSuccess(int code, String msg) {
                runOnUiThread(() -> {
                    if (code == 0) {
                        Toast.makeText(GroupDiscoveryActivity.this, group.alwaysAgree == 1 ? "已成功加入群聊" : "申请已提交，等待审核", Toast.LENGTH_SHORT).show();
                        joinedChatIds.add(group.groupId);
                        adapter.addJoinedChatId(group.groupId);
                        syncAddressBook();
                    } else {
                        Toast.makeText(GroupDiscoveryActivity.this, !TextUtils.isEmpty(msg) ? msg : "加入失败(" + code + ")", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(GroupDiscoveryActivity.this, "网络请求失败，请稍后重试", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && searchView != null) {
                imm.hideSoftInputFromWindow(searchView.getWindowToken(), 0);
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        searchHandler.removeCallbacks(searchRunnable);
        if (categoryCall != null) categoryCall.cancel();
        if (groupCall != null) groupCall.cancel();
        if (addressBookCall != null) addressBookCall.cancel();
    }
}

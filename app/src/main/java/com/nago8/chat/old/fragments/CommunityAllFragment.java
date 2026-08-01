package com.nago8.chat.old.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.nago8.chat.old.R;
import com.nago8.chat.old.adapter.CommunityPostsAdapter;
import com.nago8.chat.old.model.CommunityPostModel;
import com.nago8.chat.old.repository.CommunityRepository;
import com.nago8.chat.old.utils.PrefUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class CommunityAllFragment extends Fragment {

    public static final int FILTER_RECOMMEND = 0;
    public static final int FILTER_LATEST = 1;
    public static final int FILTER_REVERSE = 2;

    private SwipeRefreshLayout swipeRefreshAll;
    private RecyclerView rvCommunityAll;
    private ProgressBar progressBarAll;
    private FloatingActionButton fabFilter;

    private CommunityPostsAdapter postsAdapter;
    private CommunityRepository communityRepository;
    private int currentFilter = FILTER_RECOMMEND; // 默认推荐
    private int currentPage = 1;
    private final int pageSize = 20;
    private boolean isLoading = false;
    private boolean hasReachedEnd = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_community_all, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        swipeRefreshAll = view.findViewById(R.id.swipeRefreshAll);
        rvCommunityAll = view.findViewById(R.id.rvCommunityAll);
        progressBarAll = view.findViewById(R.id.progressBarAll);
        fabFilter = view.findViewById(R.id.fabFilter);

        communityRepository = new CommunityRepository();
        postsAdapter = new CommunityPostsAdapter(requireContext());

        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setInitialPrefetchItemCount(6);
        rvCommunityAll.setLayoutManager(layoutManager);
        rvCommunityAll.setHasFixedSize(false);
        rvCommunityAll.setItemViewCacheSize(15);
        rvCommunityAll.getRecycledViewPool().setMaxRecycledViews(0, 20);
        rvCommunityAll.setAdapter(postsAdapter);

        swipeRefreshAll.setOnRefreshListener(() -> {
            currentPage = 1;
            hasReachedEnd = false;
            loadPosts(false, false);
        });

        postsAdapter.setOnLoadMoreClickListener(this::loadMore);

        rvCommunityAll.addOnScrollListener(new RecyclerView.OnScrollListener() {
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

        fabFilter.setOnClickListener(v -> showFilterDialog());

        // 初始加载推荐文章
        loadPosts(true, false);
    }

    private void loadMore() {
        if (isLoading || hasReachedEnd) return;
        currentPage++;
        loadPosts(false, true);
    }

    private void showFilterDialog() {
        if (getContext() == null) return;
        String[] options = new String[]{
                getString(R.string.community_filter_recommend),
                getString(R.string.community_filter_latest),
                getString(R.string.community_filter_reverse)
        };

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.community_filter_title)
                .setSingleChoiceItems(options, currentFilter, (dialog, which) -> {
                    if (currentFilter != which) {
                        currentFilter = which;
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
        if (getContext() == null) return;
        String token = PrefUtils.getToken(requireContext());
        if (token == null || token.isEmpty()) {
            if (swipeRefreshAll != null) swipeRefreshAll.setRefreshing(false);
            return;
        }

        isLoading = true;
        if (showProgress && progressBarAll != null) {
            progressBarAll.setVisibility(View.VISIBLE);
        }

        if (isLoadMore && postsAdapter != null) {
            postsAdapter.setFooterState(CommunityPostsAdapter.STATE_LOADING);
        }

        CommunityRepository.StringCallback callback = new CommunityRepository.StringCallback() {
            @Override
            public void onSuccess(String responseBody) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    isLoading = false;
                    if (progressBarAll != null) progressBarAll.setVisibility(View.GONE);
                    if (swipeRefreshAll != null) swipeRefreshAll.setRefreshing(false);

                    List<CommunityPostModel> newPosts = parsePostsJson(responseBody);
                    if (newPosts.isEmpty() || newPosts.size() < pageSize) {
                        hasReachedEnd = true;
                    }

                    if (postsAdapter != null) {
                        if (isLoadMore) {
                            postsAdapter.addPosts(newPosts);
                        } else {
                            postsAdapter.setPosts(newPosts);
                        }

                        if (hasReachedEnd) {
                            postsAdapter.setFooterState(CommunityPostsAdapter.STATE_NO_MORE);
                        } else {
                            postsAdapter.setFooterState(CommunityPostsAdapter.STATE_LOADING);
                        }
                    }
                });
            }

            @Override
            public void onError(String msg) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    isLoading = false;
                    if (progressBarAll != null) progressBarAll.setVisibility(View.GONE);
                    if (swipeRefreshAll != null) swipeRefreshAll.setRefreshing(false);

                    if (isLoadMore && postsAdapter != null) {
                        postsAdapter.setFooterState(CommunityPostsAdapter.STATE_CLICK_TO_LOAD);
                    }
                    Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
                });
            }
        };

        if (currentFilter == FILTER_RECOMMEND) {
            communityRepository.getRecommendPostList(token, currentPage, pageSize, callback);
        } else if (currentFilter == FILTER_REVERSE) {
            communityRepository.getPostList(token, 3, 0, currentPage, pageSize, callback);
        } else {
            communityRepository.getPostList(token, 4, 0, currentPage, pageSize, callback);
        }
    }

    private List<CommunityPostModel> parsePostsJson(String jsonStr) {
        List<CommunityPostModel> list = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(jsonStr);
            if (root.optInt("code", 0) == 1) {
                JSONObject dataObj = root.optJSONObject("data");
                if (dataObj != null) {
                    JSONArray postsArray = dataObj.optJSONArray("posts");
                    if (postsArray != null) {
                        for (int i = 0; i < postsArray.length(); i++) {
                            JSONObject postObj = postsArray.optJSONObject(i);
                            if (postObj != null) {
                                CommunityPostModel model = CommunityPostModel.fromJson(postObj);
                                if (model != null) {
                                    list.add(model);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }
}

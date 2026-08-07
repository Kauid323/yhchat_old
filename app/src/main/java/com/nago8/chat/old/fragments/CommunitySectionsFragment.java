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

import com.nago8.chat.old.R;
import com.nago8.chat.old.adapter.CommunitySectionsAdapter;
import com.nago8.chat.old.adapter.CommunitySidebarAdapter;
import com.nago8.chat.old.model.CommunityBaModel;
import com.nago8.chat.old.repository.CommunityRepository;
import com.nago8.chat.old.utils.PrefUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class CommunitySectionsFragment extends Fragment {

    private RecyclerView rvSidebar;
    private SwipeRefreshLayout swipeRefreshSections;
    private RecyclerView rvSectionsList;
    private ProgressBar progressBarSections;

    private CommunitySidebarAdapter sidebarAdapter;
    private CommunitySectionsAdapter sectionsAdapter;
    private CommunityRepository communityRepository;

    private int currentTyp = 2; // 默认 2-热门
    private int currentPage = 1;
    private final int pageSize = 20;
    private boolean isLoading = false;
    private boolean hasReachedEnd = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_community_sections, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rvSidebar = view.findViewById(R.id.rvSidebar);
        swipeRefreshSections = view.findViewById(R.id.swipeRefreshSections);
        rvSectionsList = view.findViewById(R.id.rvSectionsList);
        progressBarSections = view.findViewById(R.id.progressBarSections);

        communityRepository = new CommunityRepository();

        // 1. 初始化左侧短侧边栏
        List<CommunitySidebarAdapter.SidebarItem> sidebarItems = new ArrayList<>();
        sidebarItems.add(new CommunitySidebarAdapter.SidebarItem(2, "热门"));
        sidebarItems.add(new CommunitySidebarAdapter.SidebarItem(1, "关注"));
        sidebarItems.add(new CommunitySidebarAdapter.SidebarItem(3, "我的"));
        sidebarItems.add(new CommunitySidebarAdapter.SidebarItem(4, "全部"));

        sidebarAdapter = new CommunitySidebarAdapter(requireContext(), sidebarItems);
        LinearLayoutManager sidebarLayoutManager = new LinearLayoutManager(getContext());
        sidebarLayoutManager.setInitialPrefetchItemCount(4);
        rvSidebar.setLayoutManager(sidebarLayoutManager);
        rvSidebar.setHasFixedSize(true);
        rvSidebar.setItemViewCacheSize(10);
        rvSidebar.setAdapter(sidebarAdapter);

        sidebarAdapter.setOnSidebarItemSelectedListener((item, position) -> {
            if (currentTyp != item.typ) {
                currentTyp = item.typ;
                currentPage = 1;
                hasReachedEnd = false;
                loadSections(true, false);
            }
        });

        // 2. 初始化右侧板块列表
        sectionsAdapter = new CommunitySectionsAdapter(requireContext());
        LinearLayoutManager sectionsLayoutManager = new LinearLayoutManager(getContext());
        sectionsLayoutManager.setInitialPrefetchItemCount(6);
        rvSectionsList.setLayoutManager(sectionsLayoutManager);
        rvSectionsList.setItemViewCacheSize(15);
        rvSectionsList.getRecycledViewPool().setMaxRecycledViews(0, 20);
        rvSectionsList.setAdapter(sectionsAdapter);

        swipeRefreshSections.setOnRefreshListener(() -> {
            currentPage = 1;
            hasReachedEnd = false;
            loadSections(false, false);
        });

        sectionsAdapter.setOnLoadMoreClickListener(this::loadMore);

        sectionsAdapter.setOnSectionClickListener(item -> {
            if (item == null) return;
            if (getParentFragment() instanceof CommunityFragment) {
                ((CommunityFragment) getParentFragment()).openSectionDetail(item.getId(), item.getName());
            } else {
                android.content.Intent intent = new android.content.Intent(requireContext(), com.nago8.chat.old.SectionDetailActivity.class);
                intent.putExtra(com.nago8.chat.old.SectionDetailActivity.EXTRA_BA_ID, item.getId());
                intent.putExtra(com.nago8.chat.old.SectionDetailActivity.EXTRA_BA_NAME, item.getName());
                startActivity(intent);
            }
        });

        rvSectionsList.addOnScrollListener(new RecyclerView.OnScrollListener() {
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

        // 3. 初始加载热门板块
        loadSections(true, false);
    }

    private void loadMore() {
        if (isLoading || hasReachedEnd) return;
        currentPage++;
        loadSections(false, true);
    }

    private void loadSections(boolean showProgress, boolean isLoadMore) {
        if (getContext() == null) return;
        String token = PrefUtils.getToken(requireContext());
        if (token == null || token.isEmpty()) {
            if (swipeRefreshSections != null) swipeRefreshSections.setRefreshing(false);
            return;
        }

        isLoading = true;
        if (showProgress && progressBarSections != null) {
            progressBarSections.setVisibility(View.VISIBLE);
        }

        if (isLoadMore && sectionsAdapter != null) {
            sectionsAdapter.setFooterState(CommunitySectionsAdapter.STATE_LOADING);
        }

        communityRepository.getBaList(token, currentTyp, currentPage, pageSize, new CommunityRepository.StringCallback() {
            @Override
            public void onSuccess(String responseBody) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    isLoading = false;
                    if (progressBarSections != null) progressBarSections.setVisibility(View.GONE);
                    if (swipeRefreshSections != null) swipeRefreshSections.setRefreshing(false);

                    List<CommunityBaModel> newSections = parseSectionsJson(responseBody);
                    if (newSections.isEmpty() || newSections.size() < pageSize) {
                        hasReachedEnd = true;
                    }

                    if (sectionsAdapter != null) {
                        if (isLoadMore) {
                            sectionsAdapter.addSections(newSections);
                        } else {
                            sectionsAdapter.setSections(newSections);
                        }

                        if (hasReachedEnd) {
                            sectionsAdapter.setFooterState(CommunitySectionsAdapter.STATE_NO_MORE);
                        } else {
                            sectionsAdapter.setFooterState(CommunitySectionsAdapter.STATE_LOADING);
                        }
                    }
                });
            }

            @Override
            public void onError(String msg) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    isLoading = false;
                    if (progressBarSections != null) progressBarSections.setVisibility(View.GONE);
                    if (swipeRefreshSections != null) swipeRefreshSections.setRefreshing(false);

                    if (isLoadMore && sectionsAdapter != null) {
                        sectionsAdapter.setFooterState(CommunitySectionsAdapter.STATE_CLICK_TO_LOAD);
                    }
                    Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private List<CommunityBaModel> parseSectionsJson(String jsonStr) {
        List<CommunityBaModel> list = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(jsonStr);
            if (root.optInt("code", 0) == 1) {
                JSONObject dataObj = root.optJSONObject("data");
                if (dataObj != null) {
                    JSONArray baArray = dataObj.optJSONArray("ba");
                    if (baArray != null) {
                        for (int i = 0; i < baArray.length(); i++) {
                            JSONObject baObj = baArray.optJSONObject(i);
                            if (baObj != null) {
                                CommunityBaModel model = CommunityBaModel.fromJson(baObj);
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

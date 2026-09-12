package com.nago8.chat.old.fragments;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.widget.ViewPager2;

import com.nago8.chat.old.R;
import com.nago8.chat.old.adapter.DiscoveryMainAdapter;
import com.nago8.chat.old.cache.AddressBookCache;
import com.nago8.chat.old.model.DiscoveryModels;
import com.nago8.chat.old.proto.user.address_book_list;
import com.nago8.chat.old.repository.DiscoveryRepository;
import com.nago8.chat.old.repository.FriendRepository;
import com.nago8.chat.old.utils.PrefUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import okhttp3.Call;

public class DiscoveryFragment extends Fragment {

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView rvMainDiscovery;
    private DiscoveryMainAdapter mainAdapter;

    private final DiscoveryRepository discoveryRepository = new DiscoveryRepository();
    private final FriendRepository friendRepository = new FriendRepository();

    private final List<DiscoveryModels.BannerItem> bannerList = new ArrayList<>();
    private final List<DiscoveryModels.BotItem> allBotsList = new ArrayList<>();
    private final List<DiscoveryModels.GroupItem> currentGroupsList = new ArrayList<>();
    private final Set<String> joinedChatIds = new HashSet<>();

    private int currentGroupPage = 1;
    private boolean isLoadingMoreGroups = false;
    private boolean hasMoreGroups = true;
    private static final int PAGE_SIZE = 30;

    private Call bannerCall;
    private Call botCall;
    private Call groupCall;
    private Call addressBookCall;

    private final Handler bannerHandler = new Handler(Looper.getMainLooper());
    private static final long BANNER_AUTO_SCROLL_DELAY = 4500L;
    private boolean isBannerAutoScrollActive = false;

    private final Runnable bannerScrollRunnable = new Runnable() {
        @Override
        public void run() {
            if (mainAdapter != null && mainAdapter.getHeaderViewHolder() != null) {
                ViewPager2 vp = mainAdapter.getHeaderViewHolder().vpBanners;
                if (vp != null && mainAdapter.getBannerAdapter() != null && mainAdapter.getBannerAdapter().getItemCount() > 1) {
                    int currentItem = vp.getCurrentItem();
                    int nextItem = (currentItem + 1) % mainAdapter.getBannerAdapter().getItemCount();
                    vp.setCurrentItem(nextItem, true);
                    bannerHandler.postDelayed(this, BANNER_AUTO_SCROLL_DELAY);
                }
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_discovery, container, false);
        initViews(view);
        setupAdapter();
        initJoinedChatIds();
        loadAllData(false);
        return view;
    }

    private void initViews(View view) {
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        rvMainDiscovery = view.findViewById(R.id.rvMainDiscovery);
        int primaryColor = com.nago8.chat.old.utils.ThemeUtils.getThemeColor(view.getContext());
        swipeRefreshLayout.setColorSchemeColors(primaryColor);
    }

    private void setupAdapter() {
        Context context = requireContext();
        mainAdapter = new DiscoveryMainAdapter(context);

        mainAdapter.getBotAdapter().setOnBotActionListener(this::handleApplyBot);
        mainAdapter.setGroupActionListener(this::handleApplyGroup);

        StaggeredGridLayoutManager staggeredLayoutManager = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        staggeredLayoutManager.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_NONE);
        rvMainDiscovery.setLayoutManager(staggeredLayoutManager);
        rvMainDiscovery.setAdapter(mainAdapter);

        rvMainDiscovery.addOnScrollListener(new RecyclerView.OnScrollListener() {
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
                int totalCount = mainAdapter.getItemCount();
                if (maxPos >= totalCount - 4 && !isLoadingMoreGroups && hasMoreGroups && !swipeRefreshLayout.isRefreshing()) {
                    currentGroupPage++;
                    loadGroups(true);
                }
            }
        });

        swipeRefreshLayout.setOnRefreshListener(() -> loadAllData(true));
    }

    private void initJoinedChatIds() {
        Context ctx = getContext();
        if (ctx == null) return;
        Set<String> cachedIds = AddressBookCache.getAllChatIds(ctx);
        if (cachedIds != null && !cachedIds.isEmpty()) {
            joinedChatIds.clear();
            joinedChatIds.addAll(cachedIds);
            mainAdapter.setJoinedChatIds(joinedChatIds);
        }
    }

    private void loadAllData(boolean isRefresh) {
        String token = PrefUtils.getToken(getContext());
        if (token == null || token.isEmpty()) {
            swipeRefreshLayout.setRefreshing(false);
            return;
        }

        // 1. 同步通讯录
        syncAddressBook(token);

        // 2. 加载 Banner
        loadBanners(token);

        // 3. 加载推荐机器人
        loadBots(token);

        // 4. 重置并加载推荐群聊 (第一页)
        currentGroupPage = 1;
        hasMoreGroups = true;
        isLoadingMoreGroups = false;
        mainAdapter.setHasMore(true);
        mainAdapter.setLoadingMore(false);
        loadGroups(false);
    }

    private void syncAddressBook(String token) {
        Context ctx = getContext();
        if (ctx == null) return;

        if (addressBookCall != null) addressBookCall.cancel();
        addressBookCall = friendRepository.getAddressBook(token, "", new FriendRepository.AddressBookCallback() {
            @Override
            public void onSuccess(address_book_list response) {
                if (!isAdded() || getContext() == null || response == null) return;
                if (response.data != null) {
                    AddressBookCache.saveCache(getContext(), response.data);
                    Set<String> updatedSet = AddressBookCache.getAllChatIds(getContext());
                    if (updatedSet != null) {
                        joinedChatIds.clear();
                        joinedChatIds.addAll(updatedSet);
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> mainAdapter.setJoinedChatIds(joinedChatIds));
                        }
                    }
                }
            }

            @Override
            public void onError(Exception e) {}
        });
    }

    private void loadBanners(String token) {
        if (bannerCall != null) bannerCall.cancel();
        bannerCall = discoveryRepository.getBanners(token, new DiscoveryRepository.CallbackResult<List<DiscoveryModels.BannerItem>>() {
            @Override
            public void onSuccess(List<DiscoveryModels.BannerItem> result) {
                if (!isAdded() || getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    bannerList.clear();
                    if (result != null && !result.isEmpty()) {
                        bannerList.addAll(result);
                        mainAdapter.getBannerAdapter().setData(bannerList);
                        if (mainAdapter.getHeaderViewHolder() != null) {
                            mainAdapter.getHeaderViewHolder().updateBannerVisibility(bannerList.size());
                        }
                        startBannerAutoScroll();
                    } else {
                        mainAdapter.getBannerAdapter().setData(new ArrayList<>());
                        if (mainAdapter.getHeaderViewHolder() != null) {
                            mainAdapter.getHeaderViewHolder().updateBannerVisibility(0);
                        }
                        stopBannerAutoScroll();
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                if (!isAdded() || getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (bannerList.isEmpty() && mainAdapter.getHeaderViewHolder() != null) {
                        mainAdapter.getHeaderViewHolder().updateBannerVisibility(0);
                    }
                });
            }
        });
    }

    private void loadBots(String token) {
        if (botCall != null) botCall.cancel();
        botCall = discoveryRepository.getRecommendedBots(token, new DiscoveryRepository.CallbackResult<List<DiscoveryModels.BotItem>>() {
            @Override
            public void onSuccess(List<DiscoveryModels.BotItem> result) {
                if (!isAdded() || getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    allBotsList.clear();
                    if (result != null && !result.isEmpty()) {
                        allBotsList.addAll(result);
                        if (mainAdapter.getHeaderViewHolder() != null) {
                            mainAdapter.getHeaderViewHolder().updateBotVisibility(allBotsList.size());
                        }
                        mainAdapter.getBotAdapter().setData(allBotsList);
                    } else {
                        if (mainAdapter.getHeaderViewHolder() != null) {
                            mainAdapter.getHeaderViewHolder().updateBotVisibility(0);
                        }
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                if (!isAdded() || getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (allBotsList.isEmpty() && mainAdapter.getHeaderViewHolder() != null) {
                        mainAdapter.getHeaderViewHolder().updateBotVisibility(0);
                    }
                });
            }
        });
    }

    private void loadGroups(boolean isLoadMore) {
        String token = PrefUtils.getToken(getContext());
        if (token == null || token.isEmpty()) {
            swipeRefreshLayout.setRefreshing(false);
            return;
        }

        if (isLoadMore) {
            if (isLoadingMoreGroups || !hasMoreGroups) return;
            isLoadingMoreGroups = true;
            mainAdapter.setLoadingMore(true);
        } else {
            mainAdapter.setLoading(true);
        }

        if (groupCall != null) groupCall.cancel();
        groupCall = discoveryRepository.getRecommendGroups(token, "", "", PAGE_SIZE, currentGroupPage, new DiscoveryRepository.CallbackResult<List<DiscoveryModels.GroupItem>>() {
            @Override
            public void onSuccess(List<DiscoveryModels.GroupItem> result) {
                if (!isAdded() || getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (isLoadMore) {
                        isLoadingMoreGroups = false;
                        mainAdapter.setLoadingMore(false);
                        if (result != null && !result.isEmpty()) {
                            currentGroupsList.addAll(result);
                            hasMoreGroups = result.size() >= PAGE_SIZE;
                            mainAdapter.addGroupsData(result, hasMoreGroups);
                        } else {
                            hasMoreGroups = false;
                            mainAdapter.setHasMore(false);
                        }
                    } else {
                        swipeRefreshLayout.setRefreshing(false);
                        currentGroupsList.clear();
                        if (result != null && !result.isEmpty()) {
                            currentGroupsList.addAll(result);
                            hasMoreGroups = result.size() >= PAGE_SIZE;
                            mainAdapter.setGroupsData(currentGroupsList, false, null);
                            mainAdapter.setHasMore(hasMoreGroups);
                        } else {
                            hasMoreGroups = false;
                            mainAdapter.setHasMore(false);
                            mainAdapter.setGroupsData(new ArrayList<>(), false, "暂无推荐群聊");
                        }
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                if (!isAdded() || getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (isLoadMore) {
                        isLoadingMoreGroups = false;
                        mainAdapter.setLoadingMore(false);
                        Toast.makeText(getContext(), "加载更多群聊失败", Toast.LENGTH_SHORT).show();
                    } else {
                        swipeRefreshLayout.setRefreshing(false);
                        if (currentGroupsList.isEmpty()) {
                            mainAdapter.setGroupsData(new ArrayList<>(), false, "网络请求失败，请下拉重试");
                        } else {
                            mainAdapter.setLoading(false);
                        }
                    }
                });
            }
        });
    }

    private void handleApplyBot(DiscoveryModels.BotItem bot) {
        if (bot == null || TextUtils.isEmpty(bot.chatId)) return;
        String token = PrefUtils.getToken(getContext());
        if (token == null || token.isEmpty()) return;

        Toast.makeText(getContext(), "正在添加机器人...", Toast.LENGTH_SHORT).show();
        friendRepository.applyFriend(token, bot.chatId, 3, "", new FriendRepository.ApplyFriendCallback() {
            @Override
            public void onSuccess(int code, String msg) {
                if (!isAdded() || getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (code == 0) {
                        Toast.makeText(getContext(), "已添加机器人", Toast.LENGTH_SHORT).show();
                        joinedChatIds.add(bot.chatId);
                        mainAdapter.addJoinedChatId(bot.chatId);
                        syncAddressBook(token);
                    } else {
                        Toast.makeText(getContext(), !TextUtils.isEmpty(msg) ? msg : "添加失败(" + code + ")", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                if (!isAdded() || getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "网络请求失败，请稍后重试", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void handleApplyGroup(DiscoveryModels.GroupItem group) {
        if (group == null || TextUtils.isEmpty(group.groupId)) return;
        String token = PrefUtils.getToken(getContext());
        if (token == null || token.isEmpty()) return;

        Toast.makeText(getContext(), "正在申请加入群聊...", Toast.LENGTH_SHORT).show();
        friendRepository.applyFriend(token, group.groupId, 2, "", new FriendRepository.ApplyFriendCallback() {
            @Override
            public void onSuccess(int code, String msg) {
                if (!isAdded() || getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (code == 0) {
                        Toast.makeText(getContext(), group.alwaysAgree == 1 ? "已成功加入群聊" : "申请已提交，等待审核", Toast.LENGTH_SHORT).show();
                        joinedChatIds.add(group.groupId);
                        mainAdapter.addJoinedChatId(group.groupId);
                        syncAddressBook(token);
                    } else {
                        Toast.makeText(getContext(), !TextUtils.isEmpty(msg) ? msg : "加入失败(" + code + ")", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                if (!isAdded() || getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "网络请求失败，请稍后重试", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void startBannerAutoScroll() {
        if (!isBannerAutoScrollActive && mainAdapter != null && mainAdapter.getBannerAdapter() != null && mainAdapter.getBannerAdapter().getItemCount() > 1) {
            isBannerAutoScrollActive = true;
            bannerHandler.removeCallbacks(bannerScrollRunnable);
            bannerHandler.postDelayed(bannerScrollRunnable, BANNER_AUTO_SCROLL_DELAY);
        }
    }

    private void stopBannerAutoScroll() {
        isBannerAutoScrollActive = false;
        bannerHandler.removeCallbacks(bannerScrollRunnable);
    }

    @Override
    public void onResume() {
        super.onResume();
        startBannerAutoScroll();
        initJoinedChatIds();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopBannerAutoScroll();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopBannerAutoScroll();
        if (bannerCall != null) bannerCall.cancel();
        if (botCall != null) botCall.cancel();
        if (groupCall != null) groupCall.cancel();
        if (addressBookCall != null) addressBookCall.cancel();
    }
}
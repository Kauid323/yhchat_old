package com.nago8.chat.old.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.tabs.TabLayout;
import com.nago8.chat.old.R;
import com.nago8.chat.old.adapter.AddressBookAdapter;
import com.nago8.chat.old.cache.AddressBookCache;
import com.nago8.chat.old.model.AddressBookItem;
import com.nago8.chat.old.repository.FriendRepository;
import com.nago8.chat.old.proto.user.ChatType;
import com.nago8.chat.old.proto.user.address_book_list;
import com.nago8.chat.old.utils.PinyinUtils;
import com.nago8.chat.old.utils.PrefUtils;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.Call;

public class AddressBookFragment extends Fragment {

    private static final String TAG = "AddressBookFragment";

    private TabLayout tabLayout;
    private RecyclerView recyclerView;
    private TextView tvStickyHeader;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private SwipeRefreshLayout swipeRefreshLayout;

    private View btnFilterMenu;
    private TextView tvFilterTitle;

    private LinearLayoutManager layoutManager;
    private AddressBookAdapter adapter;
    private FriendRepository friendRepository;
    private Call addressBookCall;

    // Raw parsed data lists for local filtering
    private final List<address_book_list.Data.Data_list> rawFriendsData = new ArrayList<>();
    private final List<address_book_list.Data.Data_list> rawGroupsData = new ArrayList<>();
    private final List<address_book_list.Data.Data_list> rawBotsData = new ArrayList<>();

    // Categorized display lists for the 3 tabs: 0: Friends, 1: Groups, 2: Bots
    private final List<AddressBookItem> friendsList = new ArrayList<>();
    private final List<AddressBookItem> groupsList = new ArrayList<>();
    private final List<AddressBookItem> botsList = new ArrayList<>();

    private int currentTabPosition = 0;

    // Filter states:
    // 群聊: 0 = 默认排序, 1 = 我创建的群聊, 2 = 我管理的群聊
    // 机器人: 0 = 默认排序, 1 = 我创建的机器人
    private int currentGroupFilter = 0;
    private int currentBotFilter = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_address_book, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tabLayout = view.findViewById(R.id.tabLayout);
        recyclerView = view.findViewById(R.id.recyclerView);
        tvStickyHeader = view.findViewById(R.id.tvStickyHeader);
        progressBar = view.findViewById(R.id.progressBar);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);

        btnFilterMenu = view.findViewById(R.id.btnFilterMenu);
        tvFilterTitle = view.findViewById(R.id.tvFilterTitle);

        if (btnFilterMenu != null) {
            btnFilterMenu.setOnClickListener(this::showFilterPopupMenu);
        }

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(this::refreshData);
        }

        friendRepository = new FriendRepository();
        adapter = new AddressBookAdapter(requireContext());
        layoutManager = new LinearLayoutManager(getContext());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(20);
        recyclerView.setItemAnimator(null);
        recyclerView.setAdapter(adapter);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                updateStickyHeader();
            }
        });

        setupTabs();

        // 1. First, load local cache for instant display
        boolean hasCache = loadLocalCache();

        // 2. Fetch fresh data from network
        fetchAddressBook(hasCache, false);
    }

    private void setupTabs() {
        tabLayout.removeAllTabs();
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_address_book_friends));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_address_book_groups));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_address_book_bots));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTabPosition = tab.getPosition();
                updateFilterButtonVisibility();
                updateDisplayList();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void showFilterPopupMenu(View anchorView) {
        if (getContext() == null) return;
        PopupMenu popup = new PopupMenu(requireContext(), anchorView);
        Menu menu = popup.getMenu();

        if (currentTabPosition == 1) { // 群聊
            menu.add(0, 0, 0, "默认排序");
            menu.add(0, 1, 1, "我创建的群聊");
            menu.add(0, 2, 2, "我管理的群聊");

            popup.setOnMenuItemClickListener(item -> {
                currentGroupFilter = item.getItemId();
                applyFiltersAndUpdateList();
                return true;
            });
        } else if (currentTabPosition == 2) { // 机器人
            menu.add(0, 0, 0, "默认排序");
            menu.add(0, 1, 1, "我创建的机器人");

            popup.setOnMenuItemClickListener(item -> {
                currentBotFilter = item.getItemId();
                applyFiltersAndUpdateList();
                return true;
            });
        }
        popup.show();
    }

    private void updateFilterButtonVisibility() {
        if (btnFilterMenu == null) return;
        if (currentTabPosition == 1) { // 群聊
            btnFilterMenu.setVisibility(View.VISIBLE);
            switch (currentGroupFilter) {
                case 1:
                    if (tvFilterTitle != null) tvFilterTitle.setText("我创建的群聊");
                    break;
                case 2:
                    if (tvFilterTitle != null) tvFilterTitle.setText("我管理的群聊");
                    break;
                case 0:
                default:
                    if (tvFilterTitle != null) tvFilterTitle.setText("默认排序");
                    break;
            }
        } else if (currentTabPosition == 2) { // 机器人
            btnFilterMenu.setVisibility(View.VISIBLE);
            switch (currentBotFilter) {
                case 1:
                    if (tvFilterTitle != null) tvFilterTitle.setText("我创建的机器人");
                    break;
                case 0:
                default:
                    if (tvFilterTitle != null) tvFilterTitle.setText("默认排序");
                    break;
            }
        } else {
            btnFilterMenu.setVisibility(View.GONE);
        }
    }

    public void refreshData() {
        if (getContext() == null) return;
        progressBar.setVisibility(View.VISIBLE);
        fetchAddressBook(false, true);
    }

    private void fetchAddressBook(boolean isSilent, boolean isManualRefresh) {
        String token = PrefUtils.getToken(getContext());
        if (token == null || token.isEmpty()) {
            progressBar.setVisibility(View.GONE);
            showEmpty(getString(R.string.address_book_not_logged_in));
            return;
        }

        if (!isSilent && friendsList.isEmpty() && groupsList.isEmpty() && botsList.isEmpty()) {
            progressBar.setVisibility(View.VISIBLE);
        }

        if (addressBookCall != null && !addressBookCall.isCanceled()) {
            addressBookCall.cancel();
        }

        addressBookCall = friendRepository.getAddressBook(token, "", new FriendRepository.AddressBookCallback() {
            @Override
            public void onSuccess(address_book_list response) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);

                    if (response != null && response.data != null) {
                        saveLocalCache(response.data);
                        processAddressBookData(response.data);
                        if (isManualRefresh) {
                            Toast.makeText(getContext(), R.string.address_book_refreshed, Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        boolean isSuccessStatus = response != null && response.status != null &&
                                ("success".equalsIgnoreCase(response.status.msg) || response.status.code == 0 || response.status.code == 1 || response.status.code == 200);

                        if (!isSuccessStatus) {
                            String msg = response != null && response.status != null && response.status.msg != null ?
                                    response.status.msg : getString(R.string.address_book_fetch_failed);
                            Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
                        }
                        updateDisplayList();
                    }
                });
            }

            @Override
            public void onError(Exception error) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                    if (error != null && error.getMessage() != null && !error.getMessage().contains("Canceled")) {
                        Log.e(TAG, "onError: ", error);
                        if (!isSilent) {
                            Toast.makeText(getContext(), getString(R.string.address_book_load_failed, error.getMessage()), Toast.LENGTH_SHORT).show();
                        }
                    }
                    updateDisplayList();
                });
            }
        });
    }

    private void processAddressBookData(List<address_book_list.Data> dataList) {
        rawFriendsData.clear();
        rawGroupsData.clear();
        rawBotsData.clear();

        for (address_book_list.Data categoryData : dataList) {
            if (categoryData == null || categoryData.data == null) continue;

            ChatType cType = categoryData.chat_type;
            String listName = categoryData.list_name != null ? categoryData.list_name : "";

            if (cType == ChatType.group || (cType != null && cType.getValue() == 2) || listName.contains("群")) {
                rawGroupsData.addAll(categoryData.data);
            } else if (cType == ChatType.bot || (cType != null && cType.getValue() == 3) || listName.contains("机器人") || listName.toLowerCase(Locale.US).contains("bot")) {
                rawBotsData.addAll(categoryData.data);
            } else {
                rawFriendsData.addAll(categoryData.data);
            }
        }

        applyFiltersAndUpdateList();
    }

    private void applyFiltersAndUpdateList() {
        // Filter Groups
        List<address_book_list.Data.Data_list> filteredGroups = new ArrayList<>();
        for (address_book_list.Data.Data_list item : rawGroupsData) {
            if (item == null) continue;
            int level = item.permisson_level;
            if (currentGroupFilter == 1) { // 我创建的群聊 (permisson_level == 100)
                if (level == 100) filteredGroups.add(item);
            } else if (currentGroupFilter == 2) { // 我管理的群聊 (permisson_level > 0)
                if (level > 0) filteredGroups.add(item);
            } else {
                filteredGroups.add(item);
            }
        }

        // Filter Bots
        List<address_book_list.Data.Data_list> filteredBots = new ArrayList<>();
        for (address_book_list.Data.Data_list item : rawBotsData) {
            if (item == null) continue;
            int level = item.permisson_level;
            if (currentBotFilter == 1) { // 我创建的机器人 (permisson_level > 0)
                if (level > 0) filteredBots.add(item);
            } else {
                filteredBots.add(item);
            }
        }

        // Build sorted sections
        friendsList.clear();
        friendsList.addAll(buildSortedSections(rawFriendsData, 1));

        groupsList.clear();
        groupsList.addAll(buildSortedSections(filteredGroups, 2));

        botsList.clear();
        botsList.addAll(buildSortedSections(filteredBots, 3));

        updateFilterButtonVisibility();
        updateDisplayList();
    }

    private List<AddressBookItem> buildSortedSections(List<address_book_list.Data.Data_list> items, int chatType) {
        List<AddressBookItem> result = new ArrayList<>();
        if (items == null || items.isEmpty()) {
            return result;
        }

        Map<String, List<address_book_list.Data.Data_list>> letterMap = new HashMap<>();

        for (address_book_list.Data.Data_list item : items) {
            String displayName = getDisplayName(item);
            String letter = PinyinUtils.getSortLetter(displayName);

            List<address_book_list.Data.Data_list> list = letterMap.get(letter);
            if (list == null) {
                list = new ArrayList<>();
                letterMap.put(letter, list);
            }
            list.add(item);
        }

        List<String> sortedLetters = new ArrayList<>(letterMap.keySet());
        Collator collator = Collator.getInstance(Locale.CHINA);

        Collections.sort(sortedLetters, (l1, l2) -> {
            if ("#".equals(l1)) return 1;
            if ("#".equals(l2)) return -1;
            return l1.compareTo(l2);
        });

        for (String letter : sortedLetters) {
            List<address_book_list.Data.Data_list> groupItems = letterMap.get(letter);
            if (groupItems == null || groupItems.isEmpty()) continue;

            Collections.sort(groupItems, (o1, o2) -> collator.compare(getDisplayName(o1), getDisplayName(o2)));

            result.add(new AddressBookItem(letter));
            for (address_book_list.Data.Data_list dataItem : groupItems) {
                result.add(new AddressBookItem(dataItem, chatType));
            }
        }

        return result;
    }

    private String getDisplayName(address_book_list.Data.Data_list item) {
        if (item == null) return "";
        if (item.remark != null && !item.remark.trim().isEmpty()) {
            return item.remark;
        }
        if (item.name != null && !item.name.trim().isEmpty()) {
            return item.name;
        }
        return item.chat_id != null ? item.chat_id : "";
    }

    private void updateDisplayList() {
        List<AddressBookItem> currentList;
        switch (currentTabPosition) {
            case 1:
                currentList = groupsList;
                break;
            case 2:
                currentList = botsList;
                break;
            case 0:
            default:
                currentList = friendsList;
                break;
        }

        if (currentList.isEmpty()) {
            showEmpty(getString(R.string.address_book_empty));
        } else {
            tvEmpty.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            adapter.setData(currentList);
            recyclerView.post(this::updateStickyHeader);
        }
    }

    private void showEmpty(String text) {
        adapter.setData(new ArrayList<>());
        tvEmpty.setText(text);
        tvEmpty.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        if (tvStickyHeader != null) {
            tvStickyHeader.setVisibility(View.GONE);
        }
    }

    private void updateStickyHeader() {
        if (layoutManager == null || adapter == null || tvStickyHeader == null) return;
        List<AddressBookItem> items = adapter.getItems();
        if (items == null || items.isEmpty()) {
            tvStickyHeader.setVisibility(View.GONE);
            return;
        }

        int firstVisiblePos = layoutManager.findFirstVisibleItemPosition();
        if (firstVisiblePos < 0) {
            tvStickyHeader.setVisibility(View.GONE);
            return;
        }

        String headerTitle = null;
        for (int i = firstVisiblePos; i >= 0; i--) {
            if (i < items.size() && items.get(i).getViewType() == AddressBookItem.TYPE_HEADER) {
                headerTitle = items.get(i).getHeaderTitle();
                break;
            }
        }

        if (headerTitle == null) {
            tvStickyHeader.setVisibility(View.GONE);
            return;
        }

        tvStickyHeader.setText(headerTitle);
        tvStickyHeader.setVisibility(View.VISIBLE);

        int nextPos = firstVisiblePos + 1;
        if (nextPos < items.size() && items.get(nextPos).getViewType() == AddressBookItem.TYPE_HEADER) {
            View nextView = layoutManager.findViewByPosition(nextPos);
            if (nextView != null) {
                int headerHeight = tvStickyHeader.getHeight();
                if (headerHeight == 0) {
                    tvStickyHeader.measure(
                            View.MeasureSpec.makeMeasureSpec(recyclerView.getWidth(), View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                    );
                    headerHeight = tvStickyHeader.getMeasuredHeight();
                }
                if (nextView.getTop() < headerHeight) {
                    tvStickyHeader.setTranslationY(nextView.getTop() - headerHeight);
                } else {
                    tvStickyHeader.setTranslationY(0);
                }
            } else {
                tvStickyHeader.setTranslationY(0);
            }
        } else {
            tvStickyHeader.setTranslationY(0);
        }
    }

    private void saveLocalCache(List<address_book_list.Data> dataList) {
        AddressBookCache.saveCache(getContext(), dataList);
    }

    private boolean loadLocalCache() {
        List<address_book_list.Data> cachedData = AddressBookCache.loadCache(getContext());
        if (cachedData != null && !cachedData.isEmpty()) {
            processAddressBookData(cachedData);
            return true;
        }
        return false;
    }

    @Override
    public void onDestroyView() {
        if (addressBookCall != null && !addressBookCall.isCanceled()) {
            addressBookCall.cancel();
        }
        super.onDestroyView();
    }
}
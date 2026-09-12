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

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.nago8.chat.old.adapter.DiscoveryBotAdapter;
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

public class BotDiscoveryActivity extends AppCompatActivity {

    private Toolbar toolbar;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView rvBots;
    private View layoutEmpty;
    private TextView tvEmptyHint;
    private ProgressBar progressBar;

    private DiscoveryBotAdapter adapter;
    private final DiscoveryRepository discoveryRepository = new DiscoveryRepository();
    private final FriendRepository friendRepository = new FriendRepository();

    private final List<DiscoveryModels.BotItem> allBotsList = new ArrayList<>();
    private final Set<String> joinedChatIds = new HashSet<>();

    private Call botCall;
    private Call addressBookCall;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bot_discovery);

        initViews();
        setupRecyclerView();
        setupListeners();
        initJoinedChatIds();

        loadBots();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        rvBots = findViewById(R.id.rvBots);
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
    }

    private void setupRecyclerView() {
        adapter = new DiscoveryBotAdapter(this, true);
        adapter.setOnBotActionListener(this::handleApplyBot);

        rvBots.setLayoutManager(new LinearLayoutManager(this));
        rvBots.setAdapter(adapter);
    }

    private void setupListeners() {
        swipeRefreshLayout.setOnRefreshListener(() -> {
            syncAddressBook();
            loadBots();
        });
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
                    AddressBookCache.saveCache(BotDiscoveryActivity.this, response.data);
                    Set<String> updatedSet = AddressBookCache.getAllChatIds(BotDiscoveryActivity.this);
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

    private void loadBots() {
        String token = PrefUtils.getToken(this);
        if (token == null || token.isEmpty()) {
            swipeRefreshLayout.setRefreshing(false);
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        layoutEmpty.setVisibility(View.GONE);

        if (botCall != null) botCall.cancel();
        botCall = discoveryRepository.getRecommendedBots(token, new DiscoveryRepository.CallbackResult<List<DiscoveryModels.BotItem>>() {
            @Override
            public void onSuccess(List<DiscoveryModels.BotItem> result) {
                runOnUiThread(() -> {
                    swipeRefreshLayout.setRefreshing(false);
                    progressBar.setVisibility(View.GONE);
                    allBotsList.clear();
                    if (result != null && !result.isEmpty()) {
                        allBotsList.addAll(result);
                        adapter.setData(allBotsList);
                        rvBots.setVisibility(View.VISIBLE);
                        layoutEmpty.setVisibility(View.GONE);
                    } else {
                        adapter.setData(new ArrayList<>());
                        rvBots.setVisibility(View.GONE);
                        layoutEmpty.setVisibility(View.VISIBLE);
                        tvEmptyHint.setText("暂无推荐机器人");
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    swipeRefreshLayout.setRefreshing(false);
                    progressBar.setVisibility(View.GONE);
                    if (allBotsList.isEmpty()) {
                        rvBots.setVisibility(View.GONE);
                        layoutEmpty.setVisibility(View.VISIBLE);
                        tvEmptyHint.setText("网络请求失败，请稍后重试");
                    }
                });
            }
        });
    }

    private void handleApplyBot(DiscoveryModels.BotItem bot) {
        if (bot == null || TextUtils.isEmpty(bot.chatId)) return;
        String token = PrefUtils.getToken(this);
        if (token == null || token.isEmpty()) return;

        Toast.makeText(this, "正在添加机器人...", Toast.LENGTH_SHORT).show();
        friendRepository.applyFriend(token, bot.chatId, 3, "", new FriendRepository.ApplyFriendCallback() {
            @Override
            public void onSuccess(int code, String msg) {
                runOnUiThread(() -> {
                    if (code == 0) {
                        Toast.makeText(BotDiscoveryActivity.this, "已添加机器人", Toast.LENGTH_SHORT).show();
                        joinedChatIds.add(bot.chatId);
                        adapter.addJoinedChatId(bot.chatId);
                        syncAddressBook();
                    } else {
                        Toast.makeText(BotDiscoveryActivity.this, !TextUtils.isEmpty(msg) ? msg : "添加失败(" + code + ")", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(BotDiscoveryActivity.this, "网络请求失败，请稍后重试", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (botCall != null) botCall.cancel();
        if (addressBookCall != null) addressBookCall.cancel();
    }
}

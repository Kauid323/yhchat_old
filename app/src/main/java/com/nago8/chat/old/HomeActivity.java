package com.nago8.chat.old;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nago8.chat.old.cache.ConversationCache;
import com.nago8.chat.old.fragments.AddressBookFragment;
import com.nago8.chat.old.fragments.CommunityFragment;
import com.nago8.chat.old.fragments.ConversationsFragment;
import com.nago8.chat.old.fragments.DiscoveryFragment;
import com.nago8.chat.old.fragments.StickyConversationsFragment;
import com.nago8.chat.old.model.UserModels;
import com.nago8.chat.old.net.ApiClient;
import com.nago8.chat.old.proto.chat_ws_go.WsMsg;
import com.nago8.chat.old.proto.conversation.ConversationList;
import com.nago8.chat.old.proto.user.info;
import com.nago8.chat.old.utils.ImageUtils;
import com.nago8.chat.old.utils.LocaleHelper;
import com.nago8.chat.old.utils.NotificationHelper;
import com.nago8.chat.old.utils.PrefUtils;
import com.nago8.chat.old.utils.WsMsgConverter;
import com.nago8.chat.old.ws.WsClient;
import com.nago8.chat.old.ws.WsLogManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HomeActivity extends AppCompatActivity {

    private static final String TAG = "HomeActivity";
    private static final int REQUEST_STORAGE_PERMISSION = 1001;
    private static final int REQUEST_NOTIFICATION_PERMISSION = 3001;

    private DrawerLayout drawerLayout;
    private ImageView ivAvatar;
    private TextView tvUsername;
    private TextView tvUserId;
    private Fragment currentFragment;

    private View tabContainer;
    private View searchContainer;
    private TextView tabConversations;
    private TextView tabSticky;
    private EditText etSearch;

    private boolean searchMode = false;
    private boolean showingSticky = false;
    private int conversationCount = 0;
    private int stickyCount = 0;

    private final Set<String> doNotDisturbChatIds = new HashSet<>();
    private final Map<String, String[]> convInfoCache = new HashMap<>();

    public interface SearchHost {
        void onSearch(String keyword);
        void onSearchClosed();
    }

    @Override
    protected void attachBaseContext(@NonNull Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        super.onCreate(savedInstanceState);

        // Android 6.0+ (API 23+) Dynamic Storage Permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
            }
        }
        // Android 13+ (API 33+) Notification Permission
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{"android.permission.POST_NOTIFICATIONS"}, REQUEST_NOTIFICATION_PERMISSION);
            }
        }

        try {
            setContentView(R.layout.activity_home);
        } catch (Exception e) {
            Log.e(TAG, "Error in setContentView", e);
            finish();
            return;
        }

        drawerLayout = findViewById(R.id.drawer_layout);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ivAvatar = findViewById(R.id.ivAvatar);
        tvUsername = findViewById(R.id.tvUsername);
        tvUserId = findViewById(R.id.tvUserId);

        hideStatusBarFiller(findViewById(R.id.contentStatusBarFiller));
        hideStatusBarFiller(findViewById(R.id.sidebarStatusBarFiller));

        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.openDrawer(GravityCompat.START);
            });
        }

        setupSpeedDialFab();

        ConversationCache.getInstance().setOnUnreadCountChangeListener((totalUnread, stickyUnread) -> {
            runOnUiThread(() -> {
                this.conversationCount = totalUnread;
                this.stickyCount = stickyUnread;
                updateTabTexts();
            });
        });

        setupMenuClickListeners();
        initConversationTabs();
        WsClient.getInstance().setAppContext(this);
        WsClient.getInstance().setDndChecker(this::isDoNotDisturb);
        WsClient.getInstance().setConvInfoProvider(new WsClient.ConvInfoProvider() {
            @Override
            public String getConvName(String chatId) { return HomeActivity.this.getConvName(chatId); }
            @Override
            public String getConvAvatar(String chatId) { return HomeActivity.this.getConvAvatar(chatId); }
        });
        NotificationHelper.createChannel(this);
        fetchUserInfo();
        fetchStickyCount();

        if (savedInstanceState == null) {
            switchFragment(new ConversationsFragment(), R.string.menu_conversations);
            updateTabSelection();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchUserInfo();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!WsClient.getInstance().isConnected()) {
            String userId = PrefUtils.getUserId(this);
            String token = PrefUtils.getToken(this);
            if (userId != null && !userId.isEmpty() && token != null && !token.isEmpty()) {
                WsLogManager.getInstance().logInfo("resuming: reconnecting WebSocket");
                WsClient.getInstance().reconnect();
            }
        }
    }

    private void hideStatusBarFiller(View filler) {
        if (filler == null) return;
        filler.setVisibility(View.GONE);
    }

    private void setupMenuClickListeners() {
        findViewById(R.id.menu_conversations).setOnClickListener(v -> {
            showingSticky = false;
            switchFragment(new ConversationsFragment(), R.string.menu_conversations);
            updateTabSelection();
        });
        findViewById(R.id.menu_address_book).setOnClickListener(v -> switchFragment(new AddressBookFragment(), R.string.menu_address_book));
        findViewById(R.id.menu_community).setOnClickListener(v -> switchFragment(new CommunityFragment(), R.string.menu_community));
        findViewById(R.id.menu_discovery).setOnClickListener(v -> switchFragment(new DiscoveryFragment(), R.string.menu_discovery));

        findViewById(R.id.menu_settings).setOnClickListener(v -> {
            if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
            startActivity(new Intent(this, SettingsActivity.class));
        });
        findViewById(R.id.menu_language).setOnClickListener(v -> showLanguageDialog());
        findViewById(R.id.menu_logout).setOnClickListener(v -> performLogout());
    }

    private void switchFragment(Fragment fragment, int titleRes) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction().replace(R.id.content_frame, fragment).commit();
        currentFragment = fragment;
        if (getSupportActionBar() != null) getSupportActionBar().setTitle(titleRes);
        if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);

        invalidateOptionsMenu();

        boolean isConversationTab = fragment instanceof ConversationsFragment
                || fragment instanceof StickyConversationsFragment;
        if (tabContainer != null && !searchMode) {
            tabContainer.setVisibility(isConversationTab ? View.VISIBLE : View.GONE);
        }
    }

    private void initConversationTabs() {
        tabContainer = findViewById(R.id.tabContainer);
        searchContainer = findViewById(R.id.searchContainer);
        tabConversations = findViewById(R.id.tabConversations);
        tabSticky = findViewById(R.id.tabSticky);
        etSearch = findViewById(R.id.etSearch);
        AppCompatImageView btnSearch = findViewById(R.id.btnSearch);
        AppCompatImageView btnSearchBack = findViewById(R.id.btnSearchBack);

        if (tabConversations != null) tabConversations.setOnClickListener(v -> switchConversationTab(false));
        if (tabSticky != null) tabSticky.setOnClickListener(v -> switchConversationTab(true));
        if (btnSearch != null) btnSearch.setOnClickListener(v -> doSearch());
        if (btnSearchBack != null) btnSearchBack.setOnClickListener(v -> hideSearch());
        if (etSearch != null) {
            etSearch.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    doSearch();
                    return true;
                }
                return false;
            });
        }

        updateTabTexts();
    }

    private void switchConversationTab(boolean toSticky) {
        if (showingSticky == toSticky) return;
        showingSticky = toSticky;

        if (toSticky) {
            switchFragment(new StickyConversationsFragment(), R.string.tab_sticky_title);
        } else {
            switchFragment(new ConversationsFragment(), R.string.menu_conversations);
        }
        updateTabSelection();
    }

    private void updateTabSelection() {
        if (tabConversations == null || tabSticky == null) return;
        int whiteColor = ContextCompat.getColor(this, android.R.color.white);
        if (showingSticky) {
            tabConversations.setTextColor(0xCCFFFFFF);
            tabConversations.setTypeface(null, Typeface.NORMAL);
            tabSticky.setTextColor(whiteColor);
            tabSticky.setTypeface(null, Typeface.BOLD);
        } else {
            tabConversations.setTextColor(whiteColor);
            tabConversations.setTypeface(null, Typeface.BOLD);
            tabSticky.setTextColor(0xCCFFFFFF);
            tabSticky.setTypeface(null, Typeface.NORMAL);
        }
    }

    private void updateTabTexts() {
        if (tabConversations == null || tabSticky == null) return;
        tabConversations.setText(getString(R.string.tab_conversations_format, conversationCount));
        tabSticky.setText(getString(R.string.tab_sticky_format, stickyCount));

        boolean isConversationTab = currentFragment instanceof ConversationsFragment
                || currentFragment instanceof StickyConversationsFragment;
        if (tabContainer != null && !searchMode) {
            tabContainer.setVisibility(isConversationTab ? View.VISIBLE : View.GONE);
        }
    }

    public void updateConversationCount(int count) {
        conversationCount = count;
        updateTabTexts();
    }

    // ==================== Top Toolbar Search ====================

    public void showSearch() {
        if (searchMode) return;
        if (showingSticky) {
            showingSticky = false;
            switchFragment(new ConversationsFragment(), R.string.menu_conversations);
            updateTabSelection();
        }
        searchMode = true;
        if (tabContainer != null) tabContainer.setVisibility(View.GONE);
        if (searchContainer != null) searchContainer.setVisibility(View.VISIBLE);
        if (etSearch != null) {
            etSearch.setText("");
            etSearch.requestFocus();
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && etSearch != null) imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT);
    }

    public void hideSearch() {
        if (!searchMode) return;
        searchMode = false;
        if (searchContainer != null) searchContainer.setVisibility(View.GONE);
        if (etSearch != null) etSearch.setText("");
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && etSearch != null) imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);

        boolean isConversationTab = currentFragment instanceof ConversationsFragment
                || currentFragment instanceof StickyConversationsFragment;
        if (tabContainer != null) {
            tabContainer.setVisibility(isConversationTab ? View.VISIBLE : View.GONE);
        }
        if (currentFragment instanceof SearchHost) {
            ((SearchHost) currentFragment).onSearchClosed();
        }
    }

    private void doSearch() {
        if (etSearch == null) return;
        String word = etSearch.getText().toString().trim();
        if (word.isEmpty()) return;

        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);

        if (currentFragment instanceof SearchHost) {
            ((SearchHost) currentFragment).onSearch(word);
        }
    }

    public void fetchStickyCount() {
        String token = PrefUtils.getToken(this);
        if (token == null || token.isEmpty()) return;

        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/sticky/list")
                .header("token", token)
                .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), "{}"))
                .build();

        ApiClient.getClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "fetchStickyCount failed", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String respStr = response.body().string();
                        JsonObject root = JsonParser.parseString(respStr).getAsJsonObject();
                        List<ConversationCache.StickyInfo> list = new ArrayList<>();
                        if (root.has("data") && !root.get("data").isJsonNull()) {
                            JsonObject data = root.getAsJsonObject("data");
                            if (data.has("sticky") && !data.get("sticky").isJsonNull()) {
                                JsonArray arr = data.getAsJsonArray("sticky");
                                for (JsonElement elem : arr) {
                                    JsonObject item = elem.getAsJsonObject();
                                    ConversationCache.StickyInfo info = new ConversationCache.StickyInfo();
                                    info.chatId = item.has("chatId") && !item.get("chatId").isJsonNull() ? item.get("chatId").getAsString() : "";
                                    info.chatType = item.has("chatType") && !item.get("chatType").isJsonNull() ? item.get("chatType").getAsInt() : 1;
                                    info.chatName = item.has("chatName") && !item.get("chatName").isJsonNull() ? item.get("chatName").getAsString() : "";
                                    info.avatarUrl = item.has("avatarUrl") && !item.get("avatarUrl").isJsonNull() ? item.get("avatarUrl").getAsString() : "";
                                    info.sort = item.has("sort") && !item.get("sort").isJsonNull() ? item.get("sort").getAsLong() : 0;
                                    list.add(info);
                                }
                            }
                        }
                        runOnUiThread(() -> ConversationCache.getInstance().updateStickyList(list));
                    } catch (Exception e) {
                        Log.e(TAG, "fetchStickyCount response parse error", e);
                    } finally {
                        response.body().close();
                    }
                }
            }
        });
    }

    public void updateConversationDataList(List<ConversationList.ConversationData> list) {
        ConversationCache.getInstance().updateConversationList(list);
    }

    public void updateStickyList(List<ConversationCache.StickyInfo> stickyList) {
        ConversationCache.getInstance().updateStickyList(stickyList);
    }

    public List<ConversationList.ConversationData> getCachedConversationList() {
        return ConversationCache.getInstance().getConversationList();
    }

    public List<ConversationList.ConversationData> getStickyConversationDataList() {
        return ConversationCache.getInstance().getStickyConversationDataList();
    }

    public void markConversationReadInMemory(String chatId) {
        ConversationCache.getInstance().markAsRead(chatId);
    }

    public void onPushMessageInMemory(WsMsg wsMsg, Context ctx) {
        ConversationCache.getInstance().onPushMessage(wsMsg, ctx);
    }

    private void showLanguageDialog() {
        if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);

        String current = PrefUtils.getLanguage(this);
        String[] codes = {PrefUtils.LANG_SYSTEM, PrefUtils.LANG_ZH, PrefUtils.LANG_EN};
        String[] names = {getString(R.string.lang_system), getString(R.string.lang_chinese), getString(R.string.lang_english)};

        int checked = 0;
        for (int i = 0; i < codes.length; i++) {
            if (codes[i].equals(current)) {
                checked = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_language)
                .setSingleChoiceItems(names, checked, (dialog, which) -> {
                    String selected = codes[which];
                    if (!selected.equals(current)) {
                        PrefUtils.setLanguage(this, selected);
                        dialog.dismiss();
                        LocaleHelper.applyToApplication(getApplicationContext());
                        Intent intent = new Intent(this, HomeActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        finish();
                        startActivity(intent);
                    } else {
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.home_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem actionItem = menu.findItem(R.id.action_search);
        if (actionItem != null) {
            if (currentFragment instanceof AddressBookFragment) {
                actionItem.setIcon(R.drawable.ic_refresh);
                actionItem.setTitle(R.string.action_refresh);
            } else {
                actionItem.setIcon(R.drawable.ic_search);
                actionItem.setTitle(R.string.action_search);
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_search) {
            if (currentFragment instanceof AddressBookFragment) {
                ((AddressBookFragment) currentFragment).refreshData();
            } else {
                showSearch();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private com.google.android.material.floatingactionbutton.FloatingActionButton fabAdd;
    private View fabOverlay;
    private View layoutSubNewGroup;
    private View layoutSubNewChat;
    private com.google.android.material.floatingactionbutton.FloatingActionButton fabSubNewGroup;
    private com.google.android.material.floatingactionbutton.FloatingActionButton fabSubNewChat;
    private boolean isFabExpanded = false;

    private void setupSpeedDialFab() {
        fabAdd = findViewById(R.id.fabAdd);
        fabOverlay = findViewById(R.id.fabOverlay);
        layoutSubNewGroup = findViewById(R.id.layoutSubNewGroup);
        layoutSubNewChat = findViewById(R.id.layoutSubNewChat);
        fabSubNewGroup = findViewById(R.id.fabSubNewGroup);
        fabSubNewChat = findViewById(R.id.fabSubNewChat);

        if (fabAdd != null) {
            fabAdd.setOnClickListener(v -> toggleFabMenu());
        }
        if (fabOverlay != null) {
            fabOverlay.setOnClickListener(v -> collapseFabMenu());
        }
        if (fabSubNewChat != null) {
            fabSubNewChat.setOnClickListener(v -> {
                collapseFabMenu();
                Toast.makeText(this, R.string.fab_new_chat, Toast.LENGTH_SHORT).show();
            });
        }
        View tvSubNewChatLabel = findViewById(R.id.tvSubNewChatLabel);
        if (tvSubNewChatLabel != null) {
            tvSubNewChatLabel.setOnClickListener(v -> {
                collapseFabMenu();
                Toast.makeText(this, R.string.fab_new_chat, Toast.LENGTH_SHORT).show();
            });
        }

        if (fabSubNewGroup != null) {
            fabSubNewGroup.setOnClickListener(v -> {
                collapseFabMenu();
                Toast.makeText(this, R.string.fab_new_group, Toast.LENGTH_SHORT).show();
            });
        }
        View tvSubNewGroupLabel = findViewById(R.id.tvSubNewGroupLabel);
        if (tvSubNewGroupLabel != null) {
            tvSubNewGroupLabel.setOnClickListener(v -> {
                collapseFabMenu();
                Toast.makeText(this, R.string.fab_new_group, Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void toggleFabMenu() {
        if (isFabExpanded) {
            collapseFabMenu();
        } else {
            expandFabMenu();
        }
    }

    private void expandFabMenu() {
        isFabExpanded = true;
        if (fabAdd != null) fabAdd.animate().rotation(45f).setDuration(200).start();
        if (fabOverlay != null) fabOverlay.setVisibility(View.VISIBLE);
        if (layoutSubNewGroup != null) {
            layoutSubNewGroup.setVisibility(View.VISIBLE);
            layoutSubNewGroup.setAlpha(0f);
            layoutSubNewGroup.setTranslationY(20f);
            layoutSubNewGroup.animate().alpha(1f).translationY(0f).setDuration(200).start();
        }
        if (layoutSubNewChat != null) {
            layoutSubNewChat.setVisibility(View.VISIBLE);
            layoutSubNewChat.setAlpha(0f);
            layoutSubNewChat.setTranslationY(20f);
            layoutSubNewChat.animate().alpha(1f).translationY(0f).setDuration(200).start();
        }
    }

    private void collapseFabMenu() {
        if (!isFabExpanded) return;
        isFabExpanded = false;
        if (fabAdd != null) fabAdd.animate().rotation(0f).setDuration(200).start();
        if (fabOverlay != null) fabOverlay.setVisibility(View.GONE);
        if (layoutSubNewGroup != null) {
            layoutSubNewGroup.animate().alpha(0f).translationY(20f).setDuration(150).withEndAction(() -> layoutSubNewGroup.setVisibility(View.GONE)).start();
        }
        if (layoutSubNewChat != null) {
            layoutSubNewChat.animate().alpha(0f).translationY(20f).setDuration(150).withEndAction(() -> layoutSubNewChat.setVisibility(View.GONE)).start();
        }
    }

    @Override
    public void onBackPressed() {
        if (isFabExpanded) {
            collapseFabMenu();
        } else {
            super.onBackPressed();
        }
    }

    private void fetchUserInfo() {
        String token = PrefUtils.getToken(this);
        if (token == null || token.isEmpty()) return;

        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/user/info")
                .header("token", token)
                .get()
                .build();

        ApiClient.getClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "fetchUserInfo failed", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        final info userInfo = info.ADAPTER.decode(response.body().source());
                        if (userInfo != null && userInfo.data != null) {
                            runOnUiThread(() -> {
                                PrefUtils.saveUserId(HomeActivity.this, userInfo.data.id);
                                connectWebSocket();
                                fetchStickyCount();
                                if (tvUsername != null) tvUsername.setText(userInfo.data.name);
                                if (tvUserId != null) tvUserId.setText(getString(R.string.user_id_format, userInfo.data.id));
                                ImageUtils.loadAvatar(HomeActivity.this, userInfo.data.avatar_url, ivAvatar);
                            });
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "fetchUserInfo parse error", e);
                    } finally {
                        response.body().close();
                    }
                }
            }
        });
    }

    private void performLogout() {
        String token = PrefUtils.getToken(this);
        if (token == null || token.isEmpty()) {
            clearLocalDataAndGoToLogin();
            return;
        }

        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        UserModels.LogoutRequest logoutRequest = new UserModels.LogoutRequest(deviceId);
        RequestBody body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), ApiClient.getGson().toJson(logoutRequest));

        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/user/logout")
                .header("token", token)
                .post(body)
                .build();

        ApiClient.getClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(HomeActivity.this, R.string.logout_failed, Toast.LENGTH_SHORT).show();
                    clearLocalDataAndGoToLogin();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(HomeActivity.this, R.string.logout_success, Toast.LENGTH_SHORT).show();
                    }
                    clearLocalDataAndGoToLogin();
                });
            }
        });
    }

    private void clearLocalDataAndGoToLogin() {
        WsClient.getInstance().disconnect();
        PrefUtils.clearToken(this);
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void connectWebSocket() {
        String userId = PrefUtils.getUserId(this);
        String token = PrefUtils.getToken(this);
        if (userId != null && !userId.isEmpty() && token != null && !token.isEmpty()) {
            WsLogManager.getInstance().logInfo("starting WebSocket client");
            WsClient.getInstance().connect(userId, token);
        }
    }

    public void setDoNotDisturb(String chatId, boolean dnd) {
        if (dnd) {
            doNotDisturbChatIds.add(chatId);
        } else {
            doNotDisturbChatIds.remove(chatId);
        }
    }

    public void updateDoNotDisturbSet(List<String> dndIds) {
        doNotDisturbChatIds.clear();
        if (dndIds != null) {
            doNotDisturbChatIds.addAll(dndIds);
        }
    }

    public boolean isDoNotDisturb(String chatId) {
        return doNotDisturbChatIds.contains(chatId);
    }

    public void updateConvInfo(String chatId, String name, String avatarUrl) {
        if (chatId == null || chatId.isEmpty()) return;
        convInfoCache.put(chatId, new String[]{name, avatarUrl});
    }

    public void updateConvInfoCache(List<ConversationList.ConversationData> dataList) {
        if (dataList == null) return;
        for (ConversationList.ConversationData item : dataList) {
            if (item != null && item.chat_id != null && !item.chat_id.isEmpty()) {
                convInfoCache.put(item.chat_id, new String[]{
                        item.name != null ? item.name : "",
                        item.avatar_url != null ? item.avatar_url : ""
                });
            }
        }
    }

    public String getConvName(String chatId) {
        String[] info = convInfoCache.get(chatId);
        if (info != null && info.length > 0 && info[0] != null && !info[0].isEmpty()) {
            return info[0];
        }
        return null;
    }

    public String getConvAvatar(String chatId) {
        String[] info = convInfoCache.get(chatId);
        if (info != null && info.length > 1 && info[1] != null && !info[1].isEmpty()) {
            return info[1];
        }
        return null;
    }
}

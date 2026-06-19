package com.nago8.chat.old;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import android.Manifest;
import android.content.Context;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import androidx.appcompat.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.nago8.chat.old.fragments.AddressBookFragment;
import com.nago8.chat.old.fragments.CommunityFragment;
import com.nago8.chat.old.fragments.ConversationsFragment;
import com.nago8.chat.old.fragments.StickyConversationsFragment;
import com.nago8.chat.old.fragments.DiscoveryFragment;
import com.nago8.chat.old.model.UserModels;
import com.nago8.chat.old.net.ApiClient;
import com.nago8.chat.old.proto.user.info;
import com.nago8.chat.old.utils.ImageUtils;
import com.nago8.chat.old.utils.LocaleHelper;
import com.nago8.chat.old.utils.PrefUtils;
import com.nago8.chat.old.ws.WsClient;
import com.nago8.chat.old.ws.WsLogManager;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HomeActivity extends AppCompatActivity {

    private static final int REQUEST_STORAGE_PERMISSION = 1001;
    private DrawerLayout drawerLayout;
    private ImageView ivAvatar;
    private TextView tvUsername, tvUserId;
    private Fragment currentFragment;
    private View conversationTabBar;
    private View conversationTabDivider;
    private TextView tabConversations;
    private TextView tabSticky;
    private boolean showingSticky = false;
    private int conversationCount = 0;
    private int stickyCount = 0;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
            }
        }

        try {
            setContentView(R.layout.activity_home);
        } catch (Exception e) {
            e.printStackTrace();
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

        View fabAdd = findViewById(R.id.fabAdd);
        if (fabAdd != null) fabAdd.setOnClickListener(this::showFabMenu);

        setupMenuClickListeners();
        initConversationTabs();
        fetchUserInfo();

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

        // 只有会话/置顶 Fragment 时显示 Tab 栏
        boolean isConversationTab = fragment instanceof ConversationsFragment
                || fragment instanceof StickyConversationsFragment;
        if (conversationTabBar != null) {
            boolean visible = isConversationTab && stickyCount > 0;
            conversationTabBar.setVisibility(visible ? View.VISIBLE : View.GONE);
            conversationTabDivider.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void initConversationTabs() {
        conversationTabBar = findViewById(R.id.conversationTabBar);
        conversationTabDivider = findViewById(R.id.conversationTabDivider);
        tabConversations = findViewById(R.id.tabConversations);
        tabSticky = findViewById(R.id.tabSticky);

        tabConversations.setOnClickListener(v -> switchConversationTab(false));
        tabSticky.setOnClickListener(v -> switchConversationTab(true));

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
        if (showingSticky) {
            tabConversations.setTextColor(0xCCFFFFFF);
            tabConversations.setTypeface(null, android.graphics.Typeface.NORMAL);
            tabSticky.setTextColor(getResources().getColor(android.R.color.white));
            tabSticky.setTypeface(null, android.graphics.Typeface.BOLD);
        } else {
            tabConversations.setTextColor(getResources().getColor(android.R.color.white));
            tabConversations.setTypeface(null, android.graphics.Typeface.BOLD);
            tabSticky.setTextColor(0xCCFFFFFF);
            tabSticky.setTypeface(null, android.graphics.Typeface.NORMAL);
        }
    }

    private void updateTabTexts() {
        if (tabConversations == null || tabSticky == null) return;
        tabConversations.setText(getString(R.string.tab_conversations_format, conversationCount));
        tabSticky.setText(getString(R.string.tab_sticky_format, stickyCount));

        // 有置顶会话时显示 tab 栏
        if (stickyCount > 0) {
            conversationTabBar.setVisibility(View.VISIBLE);
            conversationTabDivider.setVisibility(View.VISIBLE);
        } else {
            conversationTabBar.setVisibility(View.GONE);
            conversationTabDivider.setVisibility(View.GONE);
            // 无置顶时切回会话列表
            if (showingSticky) {
                switchConversationTab(false);
            }
        }
    }

    /**
     * 供 ConversationsFragment 回调更新会话数量。
     */
    public void updateConversationCount(int count) {
        conversationCount = count;
        updateTabTexts();
    }

    /**
     * 供 ConversationsFragment 控制会话 Tab 栏显隐（搜索时隐藏）。
     */
    public void setConversationTabBarVisible(boolean visible) {
        if (conversationTabBar == null) return;
        // 仅在有置顶会话时才允许显示
        boolean show = visible && stickyCount > 0;
        conversationTabBar.setVisibility(show ? View.VISIBLE : View.GONE);
        conversationTabDivider.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void fetchStickyCount() {
        String token = PrefUtils.getToken(this);
        if (token == null) return;

        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/sticky/list")
                .header("token", token)
                .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), "{}"))
                .build();

        ApiClient.getClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {}

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String respStr = response.body().string();
                        JsonObject root = JsonParser.parseString(respStr).getAsJsonObject();
                        int count = 0;
                        if (root.has("data") && !root.get("data").isJsonNull()) {
                            JsonObject data = root.getAsJsonObject("data");
                            if (data.has("sticky") && !data.get("sticky").isJsonNull()) {
                                count = data.getAsJsonArray("sticky").size();
                            }
                        }
                        final int finalCount = count;
                        runOnUiThread(() -> {
                            stickyCount = finalCount;
                            updateTabTexts();
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void handleSimpleMenuClick(int stringRes) {
        Toast.makeText(this, stringRes, Toast.LENGTH_SHORT).show();
        if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
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
                        LocaleHelper.applyToApplication(getApplicationContext());
                        dialog.dismiss();
                        recreate();
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
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_search) {
            if (currentFragment instanceof ConversationsFragment) {
                ((ConversationsFragment) currentFragment).showSearch();
            } else {
                Toast.makeText(this, R.string.action_search, Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showFabMenu(View view) {
        PopupMenu popup = new PopupMenu(this, view);
        popup.getMenuInflater().inflate(R.menu.fab_menu, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.fab_chat) {
                Toast.makeText(this, R.string.fab_new_chat, Toast.LENGTH_SHORT).show();
                return true;
            } else if (item.getItemId() == R.id.fab_group) {
                Toast.makeText(this, R.string.fab_new_group, Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void fetchUserInfo() {
        String token = PrefUtils.getToken(this);
        if (token == null) return;

        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/user/info")
                .header("token", token)
                .get()
                .build();

        ApiClient.getClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {}

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        final info userInfo = info.ADAPTER.decode(response.body().source());
                        if (userInfo != null && userInfo.data != null) {
                            runOnUiThread(() -> {
                                PrefUtils.saveUserId(HomeActivity.this, userInfo.data.id);
                                connectWebSocket();
                               fetchStickyCount();
                                if (tvUsername != null) tvUsername.setText(userInfo.data.name);
                                if (tvUserId != null) tvUserId.setText("ID: " + userInfo.data.id);
                                ImageUtils.loadAvatar(HomeActivity.this, userInfo.data.avatar_url, ivAvatar);
                            });
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void performLogout() {
        String token = PrefUtils.getToken(this);
        if (token == null) {
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
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(HomeActivity.this, R.string.logout_failed, Toast.LENGTH_SHORT).show();
                    clearLocalDataAndGoToLogin();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
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
        PrefUtils.clearToken(this);
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void connectWebSocket() {
        String userId = PrefUtils.getUserId(this);
        String token = PrefUtils.getToken(this);
        if (userId != null && userId.length() > 0 && token != null && token.length() > 0) {
            WsLogManager.getInstance().logInfo("starting WebSocket client");
            WsClient.getInstance().connect(userId, token);
        }
    }

    @Override
    protected void onDestroy() {
        WsClient.getInstance().disconnect();
        super.onDestroy();
    }
}

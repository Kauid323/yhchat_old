package com.nago8.chat.old;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
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
import com.nago8.chat.old.fragments.DiscoveryFragment;
import com.nago8.chat.old.model.UserModels;
import com.nago8.chat.old.net.ApiClient;
import com.nago8.chat.old.proto.user.info;
import com.nago8.chat.old.utils.ImageUtils;
import com.nago8.chat.old.utils.PrefUtils;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
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

        applyStatusBarFiller(findViewById(R.id.contentStatusBarFiller));
        applyStatusBarFiller(findViewById(R.id.sidebarStatusBarFiller));

        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> {
                if (drawerLayout != null) drawerLayout.openDrawer(GravityCompat.START);
            });
        }

        View fabAdd = findViewById(R.id.fabAdd);
        if (fabAdd != null) fabAdd.setOnClickListener(this::showFabMenu);

        setupMenuClickListeners();
        fetchUserInfo();

        if (savedInstanceState == null) {
            switchFragment(new ConversationsFragment(), R.string.menu_conversations);
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

    private void applyStatusBarFiller(View filler) {
        if (filler == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (resourceId > 0) {
                int statusBarHeight = getResources().getDimensionPixelSize(resourceId);
                android.view.ViewGroup.LayoutParams lp = filler.getLayoutParams();
                if (lp instanceof LinearLayout.LayoutParams) {
                    LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) lp;
                    params.height = statusBarHeight;
                    filler.setLayoutParams(params);
                    filler.setVisibility(View.VISIBLE);
                }
            }
        } else {
            filler.setVisibility(View.GONE);
        }
    }

    private void setupMenuClickListeners() {
        findViewById(R.id.menu_conversations).setOnClickListener(v -> switchFragment(new ConversationsFragment(), R.string.menu_conversations));
        findViewById(R.id.menu_address_book).setOnClickListener(v -> switchFragment(new AddressBookFragment(), R.string.menu_address_book));
        findViewById(R.id.menu_community).setOnClickListener(v -> switchFragment(new CommunityFragment(), R.string.menu_community));
        findViewById(R.id.menu_discovery).setOnClickListener(v -> switchFragment(new DiscoveryFragment(), R.string.menu_discovery));

        findViewById(R.id.menu_settings).setOnClickListener(v -> handleSimpleMenuClick(R.string.menu_settings));
        findViewById(R.id.menu_language).setOnClickListener(v -> handleSimpleMenuClick(R.string.menu_language));
        findViewById(R.id.menu_logout).setOnClickListener(v -> performLogout());
    }

    private void switchFragment(Fragment fragment, int titleRes) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction().replace(R.id.content_frame, fragment).commit();
        if (getSupportActionBar() != null) getSupportActionBar().setTitle(titleRes);
        if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
    }

    private void handleSimpleMenuClick(int stringRes) {
        Toast.makeText(this, stringRes, Toast.LENGTH_SHORT).show();
        if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.home_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_search) {
            Toast.makeText(this, R.string.action_search, Toast.LENGTH_SHORT).show();
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
}
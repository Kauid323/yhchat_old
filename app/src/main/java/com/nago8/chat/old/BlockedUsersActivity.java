package com.nago8.chat.old;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.nago8.chat.old.adapter.BlockedUsersAdapter;
import com.nago8.chat.old.model.BlockedUserModel;
import com.nago8.chat.old.repository.CommunityRepository;
import com.nago8.chat.old.utils.LocaleHelper;
import com.nago8.chat.old.utils.PrefUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;

public class BlockedUsersActivity extends AppCompatActivity {

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView rvBlockedUsers;
    private TextView tvEmpty;
    private ProgressBar progressBar;

    private BlockedUsersAdapter adapter;
    private CommunityRepository communityRepository;
    private Call fetchCall;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blocked_users);

        communityRepository = new CommunityRepository();

        AppCompatImageButton btnBack = findViewById(R.id.btnBack);
        TextView tvTitle = findViewById(R.id.tvTitle);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        rvBlockedUsers = findViewById(R.id.rvBlockedUsers);
        tvEmpty = findViewById(R.id.tvEmpty);
        progressBar = findViewById(R.id.progressBar);

        tvTitle.setText(R.string.blocked_users_title);

        btnBack.setOnClickListener(v -> onBackPressed());

        adapter = new BlockedUsersAdapter(this);
        rvBlockedUsers.setLayoutManager(new LinearLayoutManager(this));
        rvBlockedUsers.setAdapter(adapter);

        adapter.setOnUnblockClickListener(this::unblockUser);

        swipeRefresh.setOnRefreshListener(() -> fetchBlockedUsers(false));

        fetchBlockedUsers(true);
    }

    @Override
    protected void onDestroy() {
        if (fetchCall != null) fetchCall.cancel();
        super.onDestroy();
    }

    private void fetchBlockedUsers(boolean showProgress) {
        String token = PrefUtils.getToken(this);
        if (token == null || token.isEmpty()) return;

        if (showProgress) progressBar.setVisibility(View.VISIBLE);

        if (fetchCall != null) fetchCall.cancel();
        fetchCall = communityRepository.getBlackList(token, 1, 50, new CommunityRepository.StringCallback() {
            @Override
            public void onSuccess(String responseBody) {
                runOnUiThread(() -> {
                    swipeRefresh.setRefreshing(false);
                    progressBar.setVisibility(View.GONE);
                    parseAndBindList(responseBody);
                });
            }

            @Override
            public void onError(String msg) {
                runOnUiThread(() -> {
                    swipeRefresh.setRefreshing(false);
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(BlockedUsersActivity.this, R.string.network_error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void parseAndBindList(String json) {
        List<BlockedUserModel> items = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(json);
            if (root.optInt("code", 0) == 1 && root.has("data")) {
                JSONObject data = root.getJSONObject("data");
                if (data.has("list")) {
                    JSONArray array = data.getJSONArray("list");
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject obj = array.optJSONObject(i);
                        if (obj != null) {
                            BlockedUserModel item = BlockedUserModel.fromJson(obj);
                            if (item != null) items.add(item);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        adapter.setData(items);
        if (items.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            tvEmpty.setVisibility(View.GONE);
        }
    }

    private void unblockUser(BlockedUserModel item, int position) {
        if (item == null || item.getUserId() == null) return;
        String token = PrefUtils.getToken(this);
        if (token == null || token.isEmpty()) return;

        communityRepository.setBlackList(token, item.getUserId(), 0, new CommunityRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(BlockedUsersActivity.this, R.string.unblock_success, Toast.LENGTH_SHORT).show();
                    adapter.removeItem(position);
                    if (adapter.getItemCountList() == 0) {
                        tvEmpty.setVisibility(View.VISIBLE);
                    }
                });
            }

            @Override
            public void onError(String msg) {
                runOnUiThread(() -> Toast.makeText(BlockedUsersActivity.this, R.string.network_error, Toast.LENGTH_SHORT).show());
            }
        });
    }
}

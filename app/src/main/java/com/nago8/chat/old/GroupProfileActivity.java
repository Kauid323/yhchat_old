package com.nago8.chat.old;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.SwitchCompat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nago8.chat.old.net.ApiClient;
import com.nago8.chat.old.proto.group.edit_group;
import com.nago8.chat.old.proto.group.edit_group_send;
import com.nago8.chat.old.proto.group.info;
import com.nago8.chat.old.proto.group.info_send;
import com.nago8.chat.old.utils.ImageUtils;
import com.nago8.chat.old.utils.LocaleHelper;
import com.nago8.chat.old.utils.PrefUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GroupProfileActivity extends AppCompatActivity {

    public static final String EXTRA_GROUP_ID = "group_id";

    private AppCompatImageView ivAvatar;
    private TextView tvName;
    private TextView tvGroupId;
    private TextView tvIntroduction;
    private TextView tvMemberCount;
    private TextView tvCreateBy;
    private TextView tvCategory;
    private TextView tvGroupCode;
    private TextView tvMyNickname;
    private TextView tvCommunity;
    private View rowCategory;
    private View rowGroupCode;
    private View rowMyNickname;
    private View rowCommunity;
    private View rowBanReason;
    private TextView tvBanReason;
    private SwitchCompat swDoNotDisturb;
    private SwitchCompat swTop;
    private ProgressBar progressBar;
    private Call runningCall;
    private Call toggleCall;
    private Call editCall;
    private Call categoryCall;

    private String groupId;
    private info.Group_data currentGroup;
    private boolean bindingSwitches;

    private static class CategoryItem {
        final long id;
        final String name;
        CategoryItem(long id, String name) { this.id = id; this.name = name; }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_profile);

        groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);

        AppCompatImageButton btnBack = findViewById(R.id.btnBack);
        ivAvatar = findViewById(R.id.ivAvatar);
        tvName = findViewById(R.id.tvName);
        tvGroupId = findViewById(R.id.tvGroupId);
        tvIntroduction = findViewById(R.id.tvIntroduction);
        tvMemberCount = findViewById(R.id.tvMemberCount);
        tvCreateBy = findViewById(R.id.tvCreateBy);
        tvCategory = findViewById(R.id.tvCategory);
        tvGroupCode = findViewById(R.id.tvGroupCode);
        tvMyNickname = findViewById(R.id.tvMyNickname);
        tvCommunity = findViewById(R.id.tvCommunity);
        rowCategory = findViewById(R.id.rowCategory);
        rowGroupCode = findViewById(R.id.rowGroupCode);
        rowMyNickname = findViewById(R.id.rowMyNickname);
        rowCommunity = findViewById(R.id.rowCommunity);
        rowBanReason = findViewById(R.id.rowBanReason);
        tvBanReason = findViewById(R.id.tvBanReason);
        swDoNotDisturb = findViewById(R.id.swDoNotDisturb);
        swTop = findViewById(R.id.swTop);
        progressBar = findViewById(R.id.progressBar);

        btnBack.setOnClickListener(v -> onBackPressed());

        // 群成员点击进入成员列表
        findViewById(R.id.rowMembers).setOnClickListener(v -> {
            if (groupId != null && groupId.length() > 0) {
                Intent intent = new Intent(this, GroupMembersActivity.class);
                intent.putExtra(GroupMembersActivity.EXTRA_GROUP_ID, groupId);
                startActivity(intent);
            }
        });

        rowGroupCode.setOnClickListener(v -> showEditGroupCodeDialog());
        rowMyNickname.setOnClickListener(v -> showEditMyNicknameDialog());
        rowCategory.setOnClickListener(v -> fetchAndShowCategoryDialog());
        rowCommunity.setOnClickListener(v -> Toast.makeText(this, R.string.group_profile_community_readonly, Toast.LENGTH_SHORT).show());
        setupSwitchListeners();

        fetchGroupInfo(groupId);
    }

    @Override
    protected void onDestroy() {
        if (runningCall != null) runningCall.cancel();
        if (toggleCall != null) toggleCall.cancel();
        if (editCall != null) editCall.cancel();
        if (categoryCall != null) categoryCall.cancel();
        super.onDestroy();
    }

    private void fetchGroupInfo(String groupId) {
        if (groupId == null || groupId.length() == 0) {
            Toast.makeText(this, R.string.group_profile_load_failed, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String token = PrefUtils.getToken(this);
        if (token == null) {
            finish();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        info_send requestProto = new info_send.Builder()
                .group_id(groupId)
                .build();

        RequestBody body = RequestBody.create(
                MediaType.parse("application/x-protobuf"),
                requestProto.encode()
        );

        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/group/info")
                .header("token", token)
                .post(body)
                .build();

        runningCall = ApiClient.getClient().newCall(request);
        runningCall.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(GroupProfileActivity.this, R.string.group_profile_load_failed, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        final info result = info.ADAPTER.decode(response.body().source());
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            if (result == null || result.data == null) {
                                Toast.makeText(GroupProfileActivity.this, R.string.group_profile_load_failed, Toast.LENGTH_SHORT).show();
                                return;
                            }
                            bindGroup(result.data);
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(GroupProfileActivity.this, R.string.group_profile_load_failed, Toast.LENGTH_SHORT).show();
                        });
                    }
                } else {
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(GroupProfileActivity.this, R.string.group_profile_load_failed, Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private void bindGroup(info.Group_data data) {
        currentGroup = data;
        tvName.setText(data.name != null && data.name.length() > 0 ? data.name : getString(R.string.unknown_user));
        tvGroupId.setText("ID: " + data.group_id);
        ImageUtils.loadAvatar(this, data.avatar_url, ivAvatar);

        String intro = data.introduction != null && data.introduction.length() > 0 ? data.introduction : getString(R.string.group_profile_no_ban);
        tvIntroduction.setText(intro);

        tvMemberCount.setText(getString(R.string.group_profile_members_format, data.member));

        String createBy = data.create_by != null && data.create_by.length() > 0 ? data.create_by : "";
        tvCreateBy.setText(createBy);

        String category = data.category_name != null && data.category_name.length() > 0 ? data.category_name : "";
        tvCategory.setText(category);

        // 群号码
        tvGroupCode.setText(data.group_code != null ? data.group_code : "");

        // 我的群昵称
        tvMyNickname.setText(data.my_group_nickname != null ? data.my_group_nickname : "");

        // 关联分区
        if (data.community_name != null && data.community_name.length() > 0) {
            tvCommunity.setText(data.community_name);
            rowCommunity.setVisibility(View.VISIBLE);
        } else {
            rowCommunity.setVisibility(View.GONE);
        }

        // 禁言原因
        if (data.ban_reason != null && data.ban_reason.length() > 0) {
            tvBanReason.setText(data.ban_reason);
            rowBanReason.setVisibility(View.VISIBLE);
        } else {
            rowBanReason.setVisibility(View.GONE);
        }

        // 开关项
        bindingSwitches = true;
        swDoNotDisturb.setChecked(data.do_not_disturb == 1);
        swTop.setChecked(data.top == 1);
        bindingSwitches = false;
    }

    private void setupSwitchListeners() {
        swDoNotDisturb.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (bindingSwitches || currentGroup == null) return;
            updateDoNotDisturb(isChecked);
        });
        swTop.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (bindingSwitches || currentGroup == null) return;
            updateSticky(isChecked);
        });
    }

    private void updateDoNotDisturb(boolean enabled) {
        String token = PrefUtils.getToken(this);
        if (token == null || groupId == null || groupId.length() == 0) return;

        JsonObject bodyJson = new JsonObject();
        bodyJson.addProperty("chatId", groupId);
        bodyJson.addProperty("noNotify", enabled ? 1 : 0);

        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/friend/no-notify")
                .header("token", token)
                .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), bodyJson.toString()))
                .build();

        if (toggleCall != null) toggleCall.cancel();
        toggleCall = ApiClient.getClient().newCall(request);
        toggleCall.enqueue(new SimpleJsonToggleCallback(() -> {
            if (currentGroup != null) {
                currentGroup = currentGroup.newBuilder().do_not_disturb(enabled ? 1 : 0).build();
            }
        }, () -> revertSwitch(swDoNotDisturb, !enabled)));
    }

    private void updateSticky(boolean enabled) {
        String token = PrefUtils.getToken(this);
        if (token == null || groupId == null || groupId.length() == 0) return;

        JsonObject bodyJson = new JsonObject();
        bodyJson.addProperty("chatId", groupId);
        bodyJson.addProperty("chatType", 2);

        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + (enabled ? "/v1/sticky/add" : "/v1/sticky/delete"))
                .header("token", token)
                .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), bodyJson.toString()))
                .build();

        if (toggleCall != null) toggleCall.cancel();
        toggleCall = ApiClient.getClient().newCall(request);
        toggleCall.enqueue(new SimpleJsonToggleCallback(() -> {
            if (currentGroup != null) {
                currentGroup = currentGroup.newBuilder().top(enabled ? 1 : 0).build();
            }
        }, () -> revertSwitch(swTop, !enabled)));
    }

    private void revertSwitch(SwitchCompat target, boolean checked) {
        runOnUiThread(() -> {
            bindingSwitches = true;
            target.setChecked(checked);
            bindingSwitches = false;
            Toast.makeText(GroupProfileActivity.this, R.string.group_profile_update_failed, Toast.LENGTH_SHORT).show();
        });
    }

    private void showEditGroupCodeDialog() {
        if (currentGroup == null) return;
        showSingleInputDialog(
                getString(R.string.group_profile_group_code),
                currentGroup.group_code,
                value -> updateGroupCode(value)
        );
    }

    private void showEditMyNicknameDialog() {
        if (currentGroup == null) return;
        showSingleInputDialog(
                getString(R.string.group_profile_my_nickname),
                currentGroup.my_group_nickname,
                value -> updateMyNickname(value)
        );
    }

    private void showSingleInputDialog(String title, String initialValue, OnTextSubmitListener listener) {
        final EditText editText = new EditText(this);
        editText.setInputType(InputType.TYPE_CLASS_TEXT);
        editText.setText(initialValue != null ? initialValue : "");
        editText.setSelection(editText.getText().length());

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(editText)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (listener != null) {
                        listener.onSubmit(editText.getText() != null ? editText.getText().toString().trim() : "");
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updateMyNickname(String nickname) {
        JsonObject bodyJson = new JsonObject();
        bodyJson.addProperty("groupId", groupId);
        bodyJson.addProperty("nickname", nickname);
        postSimpleJsonEdit(
                "/v1/group/edit-my-group-nickname",
                bodyJson,
                () -> {
                    if (currentGroup != null) {
                        currentGroup = currentGroup.newBuilder().my_group_nickname(nickname).build();
                        tvMyNickname.setText(nickname);
                    }
                }
        );
    }

    private void updateGroupCode(String groupCode) {
        JsonObject bodyJson = new JsonObject();
        bodyJson.addProperty("groupId", groupId);
        bodyJson.addProperty("keyword", groupCode);
        postSimpleJsonEdit(
                "/v1/group/edit-group-keyword",
                bodyJson,
                () -> {
                    if (currentGroup != null) {
                        currentGroup = currentGroup.newBuilder().group_code(groupCode).build();
                        tvGroupCode.setText(groupCode);
                    }
                }
        );
    }

    private void fetchAndShowCategoryDialog() {
        String token = PrefUtils.getToken(this);
        if (token == null) return;

        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/group/category")
                .get()
                .header("token", token)
                .build();

        if (categoryCall != null) categoryCall.cancel();
        categoryCall = ApiClient.getClient().newCall(request);
        categoryCall.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(GroupProfileActivity.this, R.string.group_profile_load_failed, Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    runOnUiThread(() -> Toast.makeText(GroupProfileActivity.this, R.string.group_profile_load_failed, Toast.LENGTH_SHORT).show());
                    return;
                }
                try {
                    String json = response.body().string();
                    final List<CategoryItem> items = parseCategoryItems(json);
                    runOnUiThread(() -> showCategoryDialog(items));
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(GroupProfileActivity.this, R.string.group_profile_load_failed, Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private List<CategoryItem> parseCategoryItems(String json) {
        List<CategoryItem> items = new ArrayList<>();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (!root.has("data") || root.get("data").isJsonNull()) return items;
        JsonObject data = root.getAsJsonObject("data");
        if (!data.has("category") || data.get("category").isJsonNull()) return items;
        JsonArray categoryArray = data.getAsJsonArray("category");
        for (JsonElement parentElem : categoryArray) {
            JsonObject parent = parentElem.getAsJsonObject();
            String parentName = getJsonString(parent, "name");
            JsonArray subItems = parent.has("subItems") && !parent.get("subItems").isJsonNull()
                    ? parent.getAsJsonArray("subItems") : null;
            if (subItems == null || subItems.size() == 0) {
                items.add(new CategoryItem(getJsonLong(parent, "id"), parentName));
                continue;
            }
            for (JsonElement childElem : subItems) {
                JsonObject child = childElem.getAsJsonObject();
                String childName = getJsonString(child, "name");
                String displayName = parentName.length() > 0 && childName.length() > 0
                        ? parentName + "-" + childName : childName;
                items.add(new CategoryItem(getJsonLong(child, "id"), displayName));
            }
        }
        return items;
    }

    private void showCategoryDialog(List<CategoryItem> items) {
        if (items == null || items.isEmpty()) {
            Toast.makeText(this, R.string.group_profile_load_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[items.size()];
        int checked = -1;
        long currentCategoryId = currentGroup != null ? currentGroup.category_id : 0;
        for (int i = 0; i < items.size(); i++) {
            names[i] = items.get(i).name;
            if (items.get(i).id == currentCategoryId) {
                checked = i;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.group_profile_category_label)
                .setSingleChoiceItems(names, checked, (dialog, which) -> {
                    dialog.dismiss();
                    updateCategory(items.get(which));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updateCategory(CategoryItem item) {
        if (currentGroup == null || item == null) return;
        String token = PrefUtils.getToken(this);
        if (token == null) return;

        edit_group_send requestProto = new edit_group_send.Builder()
                .group_id(groupId)
                .name(currentGroup.name)
                .introduction(currentGroup.introduction)
                .avatar_url(currentGroup.avatar_url)
                .direct_join(currentGroup.direct_join)
                .history_msg(currentGroup.history_msg)
                .category_name(item.name)
                .category_id(item.id)
                .private_(currentGroup.private_)
                .hide_group_members(currentGroup.hide_group_members)
                .build();

        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/group/edit-group")
                .header("token", token)
                .post(RequestBody.create(MediaType.parse("application/x-protobuf"), requestProto.encode()))
                .build();

        if (editCall != null) editCall.cancel();
        editCall = ApiClient.getClient().newCall(request);
        editCall.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(GroupProfileActivity.this, R.string.group_profile_update_failed, Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    runOnUiThread(() -> Toast.makeText(GroupProfileActivity.this, R.string.group_profile_update_failed, Toast.LENGTH_SHORT).show());
                    return;
                }
                try {
                    final edit_group result = edit_group.ADAPTER.decode(response.body().source());
                    boolean success = result != null && result.status != null && result.status.code == 1;
                    runOnUiThread(() -> {
                        if (!success) {
                            Toast.makeText(GroupProfileActivity.this, R.string.group_profile_update_failed, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        currentGroup = currentGroup.newBuilder().category_id(item.id).category_name(item.name).build();
                        tvCategory.setText(item.name);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(GroupProfileActivity.this, R.string.group_profile_update_failed, Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private void postSimpleJsonEdit(String path, JsonObject bodyJson, Runnable onSuccess) {
        String token = PrefUtils.getToken(this);
        if (token == null) return;
        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + path)
                .header("token", token)
                .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), bodyJson.toString()))
                .build();

        if (editCall != null) editCall.cancel();
        editCall = ApiClient.getClient().newCall(request);
        editCall.enqueue(new SimpleJsonToggleCallback(onSuccess, () -> runOnUiThread(() -> Toast.makeText(GroupProfileActivity.this, R.string.group_profile_update_failed, Toast.LENGTH_SHORT).show())));
    }

    private String getJsonString(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) return obj.get(key).getAsString();
        return "";
    }

    private long getJsonLong(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) return obj.get(key).getAsLong();
        return 0L;
    }

    private interface OnTextSubmitListener {
        void onSubmit(String value);
    }

    private class SimpleJsonToggleCallback implements Callback {
        private final Runnable successAction;
        private final Runnable failureAction;

        SimpleJsonToggleCallback(Runnable successAction, Runnable failureAction) {
            this.successAction = successAction;
            this.failureAction = failureAction;
        }

        @Override
        public void onFailure(Call call, IOException e) {
            if (failureAction != null) failureAction.run();
        }

        @Override
        public void onResponse(Call call, Response response) throws IOException {
            boolean success = false;
            if (response.isSuccessful() && response.body() != null) {
                try {
                    JsonObject root = JsonParser.parseString(response.body().string()).getAsJsonObject();
                    boolean codeSuccess = root.has("code") && !root.get("code").isJsonNull() && root.get("code").getAsInt() == 1;
                    boolean msgSuccess = root.has("msg")
                            && !root.get("msg").isJsonNull()
                            && "success".equalsIgnoreCase(root.get("msg").getAsString());
                    if (!root.has("code") && !root.has("msg")) {
                        success = response.isSuccessful();
                    } else {
                        success = codeSuccess || msgSuccess;
                    }
                } catch (Exception ignored) {
                    success = false;
                }
            }
            final boolean finalSuccess = success;
            runOnUiThread(() -> {
                if (finalSuccess) {
                    if (successAction != null) successAction.run();
                } else if (failureAction != null) {
                    failureAction.run();
                }
            });
        }
    }
}

package com.nago8.chat.old;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
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

    private static final String TAG = "GroupProfileActivity";
    public static final String EXTRA_GROUP_ID = "group_id";

    private AppCompatImageView ivAvatar;
    private TextView tvName;
    private TextView tvGroupId;
    private TextView tvIntroduction;
    private TextView tvMemberCount;
    private TextView tvCategory;
    private TextView tvGroupCode;
    private TextView tvMyNickname;
    private TextView tvCommunity;
    private TextView tvBanReason;
    private TextView tvAutoDelete;

    private View rowName;
    private View rowIntroduction;
    private View rowCategory;
    private View rowGroupCode;
    private View rowMyNickname;
    private View rowCommunity;
    private View rowBanReason;
    private View rowAutoDelete;

    private SwitchCompat swDoNotDisturb;
    private SwitchCompat swTop;
    private SwitchCompat swPrivate;
    private SwitchCompat swDirectJoin;
    private SwitchCompat swHistoryMsg;
    private SwitchCompat swHideMembers;
    private SwitchCompat swDenyUpload;

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
        if (ivAvatar != null) {
            ivAvatar.setOnClickListener(v -> {
                if (currentGroup != null && currentGroup.avatar_url != null && !currentGroup.avatar_url.isEmpty()) {
                    Intent intent = new Intent(this, ImagePreviewActivity.class);
                    intent.putExtra(ImagePreviewActivity.EXTRA_IMAGE_URL, currentGroup.avatar_url);
                    startActivity(intent);
                }
            });
        }
        tvName = findViewById(R.id.tvName);
        tvGroupId = findViewById(R.id.tvGroupId);
        tvIntroduction = findViewById(R.id.tvIntroduction);
        tvMemberCount = findViewById(R.id.tvMemberCount);
        tvCategory = findViewById(R.id.tvCategory);
        tvGroupCode = findViewById(R.id.tvGroupCode);
        tvMyNickname = findViewById(R.id.tvMyNickname);
        tvCommunity = findViewById(R.id.tvCommunity);
        tvBanReason = findViewById(R.id.tvBanReason);
        tvAutoDelete = findViewById(R.id.tvAutoDelete);

        rowName = findViewById(R.id.rowName);
        rowIntroduction = findViewById(R.id.rowIntroduction);
        rowCategory = findViewById(R.id.rowCategory);
        rowGroupCode = findViewById(R.id.rowGroupCode);
        rowMyNickname = findViewById(R.id.rowMyNickname);
        rowCommunity = findViewById(R.id.rowCommunity);
        rowBanReason = findViewById(R.id.rowBanReason);
        rowAutoDelete = findViewById(R.id.rowAutoDelete);

        swDoNotDisturb = findViewById(R.id.swDoNotDisturb);
        swTop = findViewById(R.id.swTop);
        swPrivate = findViewById(R.id.swPrivate);
        swDirectJoin = findViewById(R.id.swDirectJoin);
        swHistoryMsg = findViewById(R.id.swHistoryMsg);
        swHideMembers = findViewById(R.id.swHideMembers);
        swDenyUpload = findViewById(R.id.swDenyUpload);

        progressBar = findViewById(R.id.progressBar);

        btnBack.setOnClickListener(v -> onBackPressed());

        // 群成员点击进入成员列表
        findViewById(R.id.rowMembers).setOnClickListener(v -> {
            if (groupId != null && !groupId.isEmpty()) {
                Intent intent = new Intent(this, GroupMembersActivity.class);
                intent.putExtra(GroupMembersActivity.EXTRA_GROUP_ID, groupId);
                startActivity(intent);
            }
        });

        // 字符设置项点击事件 (可编辑字符)
        rowName.setOnClickListener(v -> showEditGroupNameDialog());
        rowIntroduction.setOnClickListener(v -> showEditGroupIntroDialog());
        rowGroupCode.setOnClickListener(v -> showEditGroupCodeDialog());
        rowMyNickname.setOnClickListener(v -> showEditMyNicknameDialog());
        rowCategory.setOnClickListener(v -> fetchAndShowCategoryDialog());
        rowAutoDelete.setOnClickListener(v -> showAutoDeleteDialog());
        rowCommunity.setOnClickListener(v -> Toast.makeText(this, R.string.group_profile_community_readonly, Toast.LENGTH_SHORT).show());

        // 按钮项 / 开关项事件
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
        if (groupId == null || groupId.isEmpty()) {
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
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(GroupProfileActivity.this, R.string.group_profile_load_failed, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
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
                        Log.e(TAG, "decode info parse error", e);
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(GroupProfileActivity.this, R.string.group_profile_load_failed, Toast.LENGTH_SHORT).show();
                        });
                    } finally {
                        response.body().close();
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
        this.currentGroup = data;
        String gName = data.name;
        if (gName == null || "未知用户".equals(gName) || "Unknown user".equals(gName)) {
            gName = "";
        }
        tvName.setText(gName);
        tvGroupId.setText(getString(R.string.user_id_format, data.group_id));
        ImageUtils.loadAvatar(this, data.avatar_url, ivAvatar);

        String intro = data.introduction != null && !data.introduction.isEmpty() ? data.introduction : getString(R.string.group_profile_no_ban);
        tvIntroduction.setText(intro);

        tvMemberCount.setText(getString(R.string.group_profile_members_format, data.member));

        String category = data.category_name != null && !data.category_name.isEmpty() ? data.category_name : "";
        tvCategory.setText(category);

        // 自动删除消息 (以天数为单位)
        tvAutoDelete.setText(formatAutoDelete(data.auto_delete_message));

        // 群号码与群昵称 (字符设置项)
        tvGroupCode.setText(data.group_code != null ? data.group_code : "");
        tvMyNickname.setText(data.my_group_nickname != null ? data.my_group_nickname : "");

        // 关联分区
        if (data.community_name != null && !data.community_name.isEmpty()) {
            tvCommunity.setText(data.community_name);
            rowCommunity.setVisibility(View.VISIBLE);
        } else {
            rowCommunity.setVisibility(View.GONE);
        }

        // 禁言原因
        if (data.ban_reason != null && !data.ban_reason.isEmpty()) {
            tvBanReason.setText(data.ban_reason);
            rowBanReason.setVisibility(View.VISIBLE);
        } else {
            rowBanReason.setVisibility(View.GONE);
        }

        // 按钮项 / 开关绑定
        bindingSwitches = true;
        swDoNotDisturb.setChecked(data.do_not_disturb == 1);
        swTop.setChecked(data.top == 1);
        swPrivate.setChecked(data.private_ == 1);
        swDirectJoin.setChecked(data.direct_join == 1);
        swHistoryMsg.setChecked(data.history_msg == 1);
        swHideMembers.setChecked(data.hide_group_members == 1);
        swDenyUpload.setChecked(data.deny_members_upload_to_group_disk == 1);
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
        swPrivate.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (bindingSwitches || currentGroup == null) return;
            updateGroupEditProto(currentGroup.name, currentGroup.introduction, currentGroup.direct_join,
                    currentGroup.history_msg, isChecked ? 1 : 0, currentGroup.hide_group_members,
                    () -> currentGroup = currentGroup.newBuilder().private_(isChecked ? 1 : 0).build(),
                    () -> revertSwitch(swPrivate, !isChecked));
        });
        swDirectJoin.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (bindingSwitches || currentGroup == null) return;
            updateGroupEditProto(currentGroup.name, currentGroup.introduction, isChecked ? 1 : 0,
                    currentGroup.history_msg, currentGroup.private_, currentGroup.hide_group_members,
                    () -> currentGroup = currentGroup.newBuilder().direct_join(isChecked ? 1 : 0).build(),
                    () -> revertSwitch(swDirectJoin, !isChecked));
        });
        swHistoryMsg.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (bindingSwitches || currentGroup == null) return;
            updateGroupEditProto(currentGroup.name, currentGroup.introduction, currentGroup.direct_join,
                    isChecked ? 1 : 0, currentGroup.private_, currentGroup.hide_group_members,
                    () -> currentGroup = currentGroup.newBuilder().history_msg(isChecked ? 1 : 0).build(),
                    () -> revertSwitch(swHistoryMsg, !isChecked));
        });
        swHideMembers.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (bindingSwitches || currentGroup == null) return;
            updateGroupEditProto(currentGroup.name, currentGroup.introduction, currentGroup.direct_join,
                    currentGroup.history_msg, currentGroup.private_, isChecked ? 1L : 0L,
                    () -> currentGroup = currentGroup.newBuilder().hide_group_members(isChecked ? 1L : 0L).build(),
                    () -> revertSwitch(swHideMembers, !isChecked));
        });
        swDenyUpload.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (bindingSwitches || currentGroup == null) return;
            JsonObject bodyJson = new JsonObject();
            bodyJson.addProperty("groupId", groupId);
            bodyJson.addProperty("denyMembersUploadToGroupDisk", isChecked ? 1 : 0);
            bodyJson.addProperty("deny_members_upload_to_group_disk", isChecked ? 1 : 0);
            postSimpleJsonEdit("/v1/group/edit-deny-upload", bodyJson,
                    () -> currentGroup = currentGroup.newBuilder().deny_members_upload_to_group_disk(isChecked ? 1L : 0L).build());
        });
    }

    private void updateDoNotDisturb(boolean enabled) {
        String token = PrefUtils.getToken(this);
        if (token == null || groupId == null || groupId.isEmpty()) return;

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
        if (token == null || groupId == null || groupId.isEmpty()) return;

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

    // 字符设置项 - 修改群名称
    private void showEditGroupNameDialog() {
        if (currentGroup == null) return;
        showSingleInputDialog(
                getString(R.string.edit_group_name_title),
                currentGroup.name,
                newName -> {
                    if (newName == null || newName.isEmpty()) return;
                    updateGroupEditProto(newName, currentGroup.introduction, currentGroup.direct_join,
                            currentGroup.history_msg, currentGroup.private_, currentGroup.hide_group_members,
                            () -> {
                                currentGroup = currentGroup.newBuilder().name(newName).build();
                                tvName.setText(newName);
                            }, null);
                }
        );
    }

    // 字符设置项 - 修改群简介
    private void showEditGroupIntroDialog() {
        if (currentGroup == null) return;
        showSingleInputDialog(
                getString(R.string.edit_group_intro_title),
                currentGroup.introduction,
                newIntro -> updateGroupEditProto(currentGroup.name, newIntro, currentGroup.direct_join,
                        currentGroup.history_msg, currentGroup.private_, currentGroup.hide_group_members,
                        () -> {
                            currentGroup = currentGroup.newBuilder().introduction(newIntro).build();
                            tvIntroduction.setText(newIntro != null && !newIntro.isEmpty() ? newIntro : getString(R.string.group_profile_no_ban));
                        }, null)
        );
    }

    private void showEditGroupCodeDialog() {
        if (currentGroup == null) return;
        showSingleInputDialog(
                getString(R.string.group_profile_group_code),
                currentGroup.group_code,
                this::updateGroupCode
        );
    }

    private void showEditMyNicknameDialog() {
        if (currentGroup == null) return;
        showSingleInputDialog(
                getString(R.string.group_profile_my_nickname),
                currentGroup.my_group_nickname,
                this::updateMyNickname
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

    // 自动删除消息 (以天数为单位)
    private String formatAutoDelete(long seconds) {
        if (seconds <= 0) return getString(R.string.group_profile_off);
        long days = seconds / 86400;
        if (days <= 0) days = 1;
        return getString(R.string.group_profile_auto_delete_days_format, days);
    }

    private void showAutoDeleteDialog() {
        if (currentGroup == null) return;
        final String[] options = new String[]{
                getString(R.string.group_profile_off) + " (0 天)",
                "1 天",
                "3 天",
                "7 天",
                "30 天",
                "90 天",
                getString(R.string.auto_delete_custom)
        };
        final long[] secondsMap = new long[]{
                0L,
                86400L,
                259200L,
                604800L,
                2592000L,
                7776000L,
                -1L
        };

        long currentSecs = currentGroup.auto_delete_message;
        int checked = 0;
        for (int i = 0; i < secondsMap.length - 1; i++) {
            if (currentSecs == secondsMap[i]) {
                checked = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.auto_delete_select_title)
                .setSingleChoiceItems(options, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == options.length - 1) {
                        showCustomAutoDeleteDaysDialog();
                    } else {
                        updateAutoDeleteMessage(secondsMap[which]);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showCustomAutoDeleteDaysDialog() {
        final EditText editText = new EditText(this);
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        editText.setHint(R.string.auto_delete_custom_hint);

        new AlertDialog.Builder(this)
                .setTitle(R.string.auto_delete_custom)
                .setView(editText)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String input = editText.getText() != null ? editText.getText().toString().trim() : "";
                    if (!input.isEmpty()) {
                        try {
                            long days = Long.parseLong(input);
                            if (days < 0) days = 0;
                            updateAutoDeleteMessage(days * 86400L);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updateAutoDeleteMessage(long seconds) {
        long days = seconds / 86400;
        JsonObject bodyJson = new JsonObject();
        bodyJson.addProperty("groupId", groupId);
        bodyJson.addProperty("autoDeleteMessage", seconds);
        bodyJson.addProperty("time", seconds);
        bodyJson.addProperty("day", days);
        bodyJson.addProperty("auto_delete_message", seconds);

        postSimpleJsonEdit("/v1/group/edit-auto-delete-message", bodyJson, () -> {
            if (currentGroup != null) {
                currentGroup = currentGroup.newBuilder().auto_delete_message(seconds).build();
                tvAutoDelete.setText(formatAutoDelete(seconds));
            }
        });
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
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(GroupProfileActivity.this, R.string.group_profile_load_failed, Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (!response.isSuccessful() || response.body() == null) {
                    runOnUiThread(() -> Toast.makeText(GroupProfileActivity.this, R.string.group_profile_load_failed, Toast.LENGTH_SHORT).show());
                    return;
                }
                try {
                    String json = response.body().string();
                    final List<CategoryItem> items = parseCategoryItems(json);
                    runOnUiThread(() -> showCategoryDialog(items));
                } catch (Exception e) {
                    Log.e(TAG, "parse category error", e);
                    runOnUiThread(() -> Toast.makeText(GroupProfileActivity.this, R.string.group_profile_load_failed, Toast.LENGTH_SHORT).show());
                } finally {
                    response.body().close();
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
            if (subItems == null || subItems.isEmpty()) {
                items.add(new CategoryItem(getJsonLong(parent, "id"), parentName));
                continue;
            }
            for (JsonElement childElem : subItems) {
                JsonObject child = childElem.getAsJsonObject();
                String childName = getJsonString(child, "name");
                String displayName = !parentName.isEmpty() && !childName.isEmpty()
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
        updateGroupEditProto(currentGroup.name, currentGroup.introduction, currentGroup.direct_join,
                currentGroup.history_msg, currentGroup.private_, currentGroup.hide_group_members,
                () -> {
                    currentGroup = currentGroup.newBuilder().category_id(item.id).category_name(item.name).build();
                    tvCategory.setText(item.name);
                }, null);
    }

    private void updateGroupEditProto(String name, String intro, int directJoin, int historyMsg, int privateVal, long hideMembers, Runnable onSuccess, Runnable onFailure) {
        String token = PrefUtils.getToken(this);
        if (token == null || currentGroup == null) return;

        edit_group_send requestProto = new edit_group_send.Builder()
                .group_id(groupId)
                .name(name != null ? name : currentGroup.name)
                .introduction(intro != null ? intro : currentGroup.introduction)
                .avatar_url(currentGroup.avatar_url)
                .direct_join(directJoin)
                .history_msg(historyMsg)
                .category_name(currentGroup.category_name)
                .category_id(currentGroup.category_id)
                .private_(privateVal)
                .hide_group_members(hideMembers)
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
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    if (onFailure != null) onFailure.run();
                    Toast.makeText(GroupProfileActivity.this, R.string.group_profile_update_failed, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        final edit_group result = edit_group.ADAPTER.decode(response.body().source());
                        boolean success = result != null && result.status != null && result.status.code == 1;
                        runOnUiThread(() -> {
                            if (success) {
                                if (onSuccess != null) onSuccess.run();
                            } else {
                                if (onFailure != null) onFailure.run();
                                Toast.makeText(GroupProfileActivity.this, R.string.group_profile_update_failed, Toast.LENGTH_SHORT).show();
                            }
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            if (onFailure != null) onFailure.run();
                            Toast.makeText(GroupProfileActivity.this, R.string.group_profile_update_failed, Toast.LENGTH_SHORT).show();
                        });
                    } finally {
                        response.body().close();
                    }
                } else {
                    runOnUiThread(() -> {
                        if (onFailure != null) onFailure.run();
                        Toast.makeText(GroupProfileActivity.this, R.string.group_profile_update_failed, Toast.LENGTH_SHORT).show();
                    });
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

    @SuppressWarnings("SameParameterValue")
    private String getJsonString(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) return obj.get(key).getAsString();
        return "";
    }

    @SuppressWarnings("SameParameterValue")
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
        public void onFailure(@NonNull Call call, @NonNull IOException e) {
            if (failureAction != null) failureAction.run();
        }

        @Override
        public void onResponse(@NonNull Call call, @NonNull Response response) {
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
                } finally {
                    response.body().close();
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

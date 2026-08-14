package com.nago8.chat.old;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nago8.chat.old.net.ApiClient;
import com.nago8.chat.old.proto.bot.bot_info;
import com.nago8.chat.old.proto.bot.bot_info_send;
import com.nago8.chat.old.proto.group.info;
import com.nago8.chat.old.proto.group.info_send;
import com.nago8.chat.old.proto.user.get_user;
import com.nago8.chat.old.proto.user.get_user_send;
import com.nago8.chat.old.utils.ImageUtils;
import com.nago8.chat.old.utils.LocaleHelper;
import com.nago8.chat.old.utils.PrefUtils;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AddContactActivity extends AppCompatActivity {

    public static final int TYPE_USER = 0;
    public static final int TYPE_GROUP = 1;
    public static final int TYPE_BOT = 2;

    private TabLayout tabLayout;
    private EditText etSearchId;
    private AppCompatImageView btnClear;
    private ProgressBar progressBar;
    private CardView cardResult;
    private AppCompatImageView ivResultAvatar;
    private TextView tvResultName;
    private TextView tvResultId;
    private TextView tvResultDesc;
    private MaterialButton btnAction;
    private TextView tvEmpty;

    private int currentType = TYPE_USER;
    private Call runningCall;

    @Override
    protected void attachBaseContext(@NonNull Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_contact);

        initViews();
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        tabLayout = findViewById(R.id.tabLayout);
        etSearchId = findViewById(R.id.etSearchId);
        btnClear = findViewById(R.id.btnClear);
        progressBar = findViewById(R.id.progressBar);
        cardResult = findViewById(R.id.cardResult);
        ivResultAvatar = findViewById(R.id.ivResultAvatar);
        tvResultName = findViewById(R.id.tvResultName);
        tvResultId = findViewById(R.id.tvResultId);
        tvResultDesc = findViewById(R.id.tvResultDesc);
        btnAction = findViewById(R.id.btnAction);
        tvEmpty = findViewById(R.id.tvEmpty);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentType = tab.getPosition();
                updateSearchHint();
                clearResult();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        findViewById(R.id.btnSearch).setOnClickListener(v -> performSearch());
        etSearchId.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch();
                return true;
            }
            return false;
        });

        etSearchId.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnClear.setOnClickListener(v -> {
            etSearchId.setText("");
            clearResult();
        });
    }

    private void updateSearchHint() {
        if (currentType == TYPE_USER) {
            etSearchId.setHint(R.string.add_search_user_hint);
        } else if (currentType == TYPE_GROUP) {
            etSearchId.setHint(R.string.add_search_group_hint);
        } else {
            etSearchId.setHint(R.string.add_search_bot_hint);
        }
    }

    private void clearResult() {
        if (runningCall != null) {
            runningCall.cancel();
            runningCall = null;
        }
        progressBar.setVisibility(View.GONE);
        cardResult.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.GONE);
    }

    private void performSearch() {
        String id = etSearchId.getText().toString().trim();
        if (TextUtils.isEmpty(id)) {
            Toast.makeText(this, R.string.add_search_empty_hint, Toast.LENGTH_SHORT).show();
            return;
        }

        hideKeyboard();
        clearResult();
        progressBar.setVisibility(View.VISIBLE);

        String token = PrefUtils.getToken(this);
        if (token == null || token.isEmpty()) {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(this, R.string.address_book_not_logged_in, Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentType == TYPE_USER) {
            searchUser(token, id);
        } else if (currentType == TYPE_GROUP) {
            searchGroup(token, id);
        } else {
            searchBot(token, id);
        }
    }

    private void searchUser(String token, String userId) {
        get_user_send req = new get_user_send.Builder()
                .id(userId)
                .build();

        RequestBody body = RequestBody.create(
                MediaType.parse("application/x-protobuf"),
                req.encode()
        );

        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/user/get-user")
                .header("token", token)
                .post(body)
                .build();

        runningCall = ApiClient.getClient().newCall(request);
        runningCall.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (call.isCanceled()) return;
                runOnUiThread(() -> showNotFound());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (call.isCanceled()) return;
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        final get_user result = get_user.ADAPTER.decode(response.body().source());
                        runOnUiThread(() -> {
                            if (result != null && result.data != null && !TextUtils.isEmpty(result.data.name)) {
                                showUserResult(result.data, userId);
                            } else {
                                showNotFound();
                            }
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> showNotFound());
                    } finally {
                        response.body().close();
                    }
                } else {
                    runOnUiThread(() -> showNotFound());
                }
            }
        });
    }

    private void showUserResult(get_user.Data data, String userId) {
        progressBar.setVisibility(View.GONE);
        cardResult.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        String name = data.name != null ? data.name : userId;
        tvResultName.setText(name);
        tvResultId.setText(getString(R.string.user_id_format, userId));
        ImageUtils.loadAvatar(this, data.avatar_url, ivResultAvatar);

        String sig = (data.profile_info != null && !TextUtils.isEmpty(data.profile_info.introduction))
                ? data.profile_info.introduction : getString(R.string.user_no_signature);
        tvResultDesc.setText(sig);

        btnAction.setText(R.string.action_view_profile);
        btnAction.setOnClickListener(v -> {
            Intent intent = new Intent(this, UserProfileActivity.class);
            intent.putExtra(UserProfileActivity.EXTRA_USER_ID, userId);
            startActivity(intent);
        });
    }

    private static class MergedGroupData {
        String realGroupId;
        String name;
        String avatarUrl;
        String introduction;
        long member = 0;
        String category;
        boolean hasData = false;
    }

    private void searchGroup(String token, String query) {
        final MergedGroupData merged = new MergedGroupData();
        merged.realGroupId = query;
        final int[] finishedCount = new int[]{0};
        final int totalRequests = 3;

        // 1. 请求 /v1/group/info-add-friend (以 JSON 传递 keyword, groupId, groupCode)
        JsonObject jsonReq = new JsonObject();
        jsonReq.addProperty("keyword", query);
        jsonReq.addProperty("groupId", query);
        jsonReq.addProperty("groupCode", query);
        Request reqAddFriendJson = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/group/info-add-friend")
                .header("token", token)
                .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), jsonReq.toString()))
                .build();

        // 2. 请求 /v1/group/info (以 Proto 传递 group_id)
        info_send protoReq = new info_send.Builder()
                .group_id(query)
                .build();
        Request reqInfoProto = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/group/info")
                .header("token", token)
                .post(RequestBody.create(MediaType.parse("application/x-protobuf"), protoReq.encode()))
                .build();

        // 3. 请求 /v1/search/home-search (全局搜索群口令与群关键词)
        JsonObject searchReq = new JsonObject();
        searchReq.addProperty("word", query);
        Request reqHomeSearch = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/search/home-search")
                .header("token", token)
                .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), searchReq.toString()))
                .build();

        Callback mergeCallback = new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                android.util.Log.e("AddContactActivity", "searchGroup onFailure: " + call.request().url(), e);
                checkFinish();
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        byte[] bodyBytes = response.body().bytes();
                        android.util.Log.d("AddContactActivity", "searchGroup url=" + call.request().url() + " len=" + bodyBytes.length);

                        // 1. 优先尝试标准的 Protobuf 解码
                        try {
                            okio.Buffer buffer = new okio.Buffer().write(bodyBytes);
                            final info result = info.ADAPTER.decode(buffer);
                            if (result != null && result.data != null && !TextUtils.isEmpty(result.data.name)) {
                                synchronized (merged) {
                                    if (result.data.group_id != null && !result.data.group_id.isEmpty()) {
                                        merged.realGroupId = result.data.group_id;
                                    }
                                    merged.name = result.data.name;
                                    if (result.data.avatar_url != null && !result.data.avatar_url.isEmpty()) {
                                        merged.avatarUrl = result.data.avatar_url;
                                    }
                                    if (result.data.introduction != null && !result.data.introduction.isEmpty()) {
                                        merged.introduction = result.data.introduction;
                                    }
                                    if (result.data.member > 0) {
                                        merged.member = result.data.member;
                                    }
                                    if (result.data.category_name != null && !result.data.category_name.isEmpty()) {
                                        merged.category = result.data.category_name;
                                    }
                                    merged.hasData = true;
                                }
                                android.util.Log.d("AddContactActivity", "Proto parse SUCCESS: " + merged.name);
                            }
                        } catch (Exception ignored) {}

                        // 2. 如果 Proto 未成功，尝试 JSON 解析
                        if (!merged.hasData) {
                            try {
                                String respStr = new String(bodyBytes, java.nio.charset.StandardCharsets.UTF_8);
                                android.util.Log.d("AddContactActivity", "Trying JSON parse: " + respStr);
                                JsonObject root = JsonParser.parseString(respStr).getAsJsonObject();
                                if (root.has("data")) {
                                    JsonElement dataElem = root.get("data");
                                    if (dataElem.isJsonObject()) {
                                        JsonObject dataObj = dataElem.getAsJsonObject();
                                        if (dataObj.has("list") && dataObj.get("list").isJsonArray()) {
                                            parseHomeSearchList(dataObj.getAsJsonArray("list"));
                                        }
                                        if (dataObj.has("groups") && dataObj.get("groups").isJsonArray()) {
                                            JsonArray groupsArr = dataObj.getAsJsonArray("groups");
                                            if (groupsArr.size() > 0 && groupsArr.get(0).isJsonObject()) {
                                                parseAndMergeGroupJson(groupsArr.get(0).getAsJsonObject());
                                            }
                                        }
                                        if (dataObj.has("group") && dataObj.get("group").isJsonObject()) {
                                            parseAndMergeGroupJson(dataObj.getAsJsonObject("group"));
                                        }
                                        parseAndMergeGroupJson(dataObj);
                                    } else if (dataElem.isJsonArray()) {
                                        parseHomeSearchList(dataElem.getAsJsonArray());
                                    }
                                }
                            } catch (Exception ignored) {}
                        }

                        if (merged.hasData) {
                            runOnUiThread(() -> showMergedGroupResult(merged));
                        }
                    } catch (Exception e) {
                        android.util.Log.e("AddContactActivity", "onResponse error", e);
                    } finally {
                        response.body().close();
                    }
                }
                checkFinish();
            }

            private void parseAndMergeGroupJson(JsonObject gObj) {
                synchronized (merged) {
                    String gId = "";
                    if (gObj.has("groupId") && !gObj.get("groupId").isJsonNull()) {
                        gId = gObj.get("groupId").getAsString();
                    } else if (gObj.has("id") && !gObj.get("id").isJsonNull()) {
                        gId = gObj.get("id").getAsString();
                    } else if (gObj.has("friendId") && !gObj.get("friendId").isJsonNull()) {
                        gId = gObj.get("friendId").getAsString();
                    }
                    if (!TextUtils.isEmpty(gId)) {
                        merged.realGroupId = gId;
                    }

                    String gName = "";
                    if (gObj.has("name") && !gObj.get("name").isJsonNull()) {
                        gName = gObj.get("name").getAsString();
                    } else if (gObj.has("nickname") && !gObj.get("nickname").isJsonNull()) {
                        gName = gObj.get("nickname").getAsString();
                    }
                    if (!TextUtils.isEmpty(gName)) {
                        merged.name = gName;
                        merged.hasData = true;
                    }

                    if (gObj.has("avatarUrl") && !gObj.get("avatarUrl").isJsonNull()) {
                        merged.avatarUrl = gObj.get("avatarUrl").getAsString();
                    }
                    if (gObj.has("introduction") && !gObj.get("introduction").isJsonNull()) {
                        merged.introduction = gObj.get("introduction").getAsString();
                    }
                    if (gObj.has("member") && !gObj.get("member").isJsonNull()) {
                        merged.member = gObj.get("member").getAsLong();
                    } else if (gObj.has("headcount") && !gObj.get("headcount").isJsonNull()) {
                        merged.member = gObj.get("headcount").getAsLong();
                    }
                    if (gObj.has("categoryName") && !gObj.get("categoryName").isJsonNull()) {
                        merged.category = gObj.get("categoryName").getAsString();
                    } else if (gObj.has("category") && !gObj.get("category").isJsonNull()) {
                        merged.category = gObj.get("category").getAsString();
                    }

                    // 如果简介为空，使用真实的群ID自动补充拉取群完整资料
                    if (!TextUtils.isEmpty(merged.realGroupId) && TextUtils.isEmpty(merged.introduction)) {
                        fetchGroupDetailInfo(token, merged.realGroupId, merged);
                    }
                }
            }

            private void parseHomeSearchList(JsonArray listArray) {
                for (JsonElement item : listArray) {
                    if (!item.isJsonObject()) continue;
                    JsonObject categoryObj = item.getAsJsonObject();
                    String title = categoryObj.has("title") && !categoryObj.get("title").isJsonNull() 
                            ? categoryObj.get("title").getAsString() : "";
                    
                    // 如果这组是群组分类，或者直接有 list
                    if (categoryObj.has("list") && !categoryObj.get("list").isJsonNull() && categoryObj.get("list").isJsonArray()) {
                        JsonArray subList = categoryObj.getAsJsonArray("list");
                        for (JsonElement subItem : subList) {
                            if (!subItem.isJsonObject()) continue;
                            JsonObject gObj = subItem.getAsJsonObject();
                            int friendType = gObj.has("friendType") && !gObj.get("friendType").isJsonNull() 
                                    ? gObj.get("friendType").getAsInt() : 0;
                            // 只要 title 包含群、或者 friendType == 2、或者没有类型限制
                            if ("群组".equals(title) || "群聊".equals(title) || friendType == 2 || title.isEmpty()) {
                                parseAndMergeGroupJson(gObj);
                                if (merged.hasData) return;
                            }
                        }
                    } else {
                        // item 本身可能就是一个群对象
                        int friendType = categoryObj.has("friendType") && !categoryObj.get("friendType").isJsonNull() 
                                ? categoryObj.get("friendType").getAsInt() : 0;
                        if (friendType == 2 || categoryObj.has("groupId")) {
                            parseAndMergeGroupJson(categoryObj);
                            if (merged.hasData) return;
                        }
                    }
                }
            }

            private void checkFinish() {
                synchronized (finishedCount) {
                    finishedCount[0]++;
                    if (finishedCount[0] >= totalRequests) {
                        runOnUiThread(() -> {
                            if (!merged.hasData) {
                                showNotFound();
                            }
                        });
                    }
                }
            }
        };

        ApiClient.getClient().newCall(reqAddFriendJson).enqueue(mergeCallback);
        ApiClient.getClient().newCall(reqInfoProto).enqueue(mergeCallback);
        ApiClient.getClient().newCall(reqHomeSearch).enqueue(mergeCallback);
    }

    private void fetchGroupDetailInfo(String token, String realGroupId, MergedGroupData merged) {
        if (TextUtils.isEmpty(realGroupId)) return;
        info_send req = new info_send.Builder().group_id(realGroupId).build();
        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/group/info")
                .header("token", token)
                .post(RequestBody.create(MediaType.parse("application/x-protobuf"), req.encode()))
                .build();

        ApiClient.getClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {}

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        final info result = info.ADAPTER.decode(response.body().source());
                        if (result != null && result.data != null) {
                            synchronized (merged) {
                                if (result.data.introduction != null && !result.data.introduction.isEmpty()) {
                                    merged.introduction = result.data.introduction;
                                }
                                if (result.data.name != null && !result.data.name.isEmpty()) {
                                    merged.name = result.data.name;
                                }
                                if (result.data.avatar_url != null && !result.data.avatar_url.isEmpty()) {
                                    merged.avatarUrl = result.data.avatar_url;
                                }
                                if (result.data.member > 0) {
                                    merged.member = result.data.member;
                                }
                                if (result.data.category_name != null && !result.data.category_name.isEmpty()) {
                                    merged.category = result.data.category_name;
                                }
                            }
                            runOnUiThread(() -> showMergedGroupResult(merged));
                        }
                    } catch (Exception ignored) {
                    } finally {
                        response.body().close();
                    }
                }
            }
        });
    }

    private void showMergedGroupResult(MergedGroupData data) {
        progressBar.setVisibility(View.GONE);
        cardResult.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        String groupId = data.realGroupId != null ? data.realGroupId : "";
        String name = data.name != null && !data.name.isEmpty() ? data.name : groupId;
        tvResultName.setText(name);
        tvResultId.setText(getString(R.string.user_id_format, groupId));
        if (data.avatarUrl != null) {
            ImageUtils.loadAvatar(this, data.avatarUrl, ivResultAvatar);
        }

        String intro = data.introduction != null && !data.introduction.isEmpty() ? data.introduction : getString(R.string.group_no_intro);
        if (data.member > 0) {
            intro = getString(R.string.group_members_count_format, data.member) + "\n" + intro;
        }
        if (data.category != null && !data.category.isEmpty()) {
            intro = getString(R.string.group_category_format, data.category) + "\n" + intro;
        }
        tvResultDesc.setText(intro);

        btnAction.setText(R.string.action_view_detail);
        btnAction.setOnClickListener(v -> {
            Intent intent = new Intent(this, GroupProfileActivity.class);
            intent.putExtra(GroupProfileActivity.EXTRA_GROUP_ID, groupId);
            startActivity(intent);
        });
    }



    private void searchBot(String token, String botId) {
        bot_info_send req = new bot_info_send.Builder()
                .id(botId)
                .build();

        RequestBody body = RequestBody.create(
                MediaType.parse("application/x-protobuf"),
                req.encode()
        );

        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/bot/bot-info")
                .header("token", token)
                .post(body)
                .build();

        runningCall = ApiClient.getClient().newCall(request);
        runningCall.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (call.isCanceled()) return;
                runOnUiThread(() -> showNotFound());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (call.isCanceled()) return;
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        final bot_info result = bot_info.ADAPTER.decode(response.body().source());
                        runOnUiThread(() -> {
                            if (result != null && result.data != null && !TextUtils.isEmpty(result.data.name)) {
                                showBotResult(result.data, botId);
                            } else {
                                showNotFound();
                            }
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> showNotFound());
                    } finally {
                        response.body().close();
                    }
                } else {
                    runOnUiThread(() -> showNotFound());
                }
            }
        });
    }

    private void showBotResult(bot_info.Bot_data data, String botId) {
        progressBar.setVisibility(View.GONE);
        cardResult.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        String name = data.name != null ? data.name : botId;
        tvResultName.setText(name);
        tvResultId.setText(getString(R.string.user_id_format, botId));
        ImageUtils.loadAvatar(this, data.avatar_url, ivResultAvatar);

        String intro = data.introduction != null && !data.introduction.isEmpty() ? data.introduction : getString(R.string.bot_no_intro);
        tvResultDesc.setText(intro);

        btnAction.setText(R.string.action_view_detail);
        btnAction.setOnClickListener(v -> {
            Intent intent = new Intent(this, BotProfileActivity.class);
            intent.putExtra(BotProfileActivity.EXTRA_BOT_ID, botId);
            startActivity(intent);
        });
    }

    private void showNotFound() {
        progressBar.setVisibility(View.GONE);
        cardResult.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.VISIBLE);
        tvEmpty.setText(R.string.add_search_not_found);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && etSearchId != null) {
            imm.hideSoftInputFromWindow(etSearchId.getWindowToken(), 0);
        }
    }
}

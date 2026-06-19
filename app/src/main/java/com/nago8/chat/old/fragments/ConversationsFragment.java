package com.nago8.chat.old.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nago8.chat.old.ChatActivity;
import com.nago8.chat.old.R;
import com.nago8.chat.old.net.ApiClient;
import com.nago8.chat.old.proto.chat_ws_go.WsMsg;
import com.nago8.chat.old.proto.conversation.ConversationList;
import com.nago8.chat.old.proto.conversation.ConversationListRequest;
import com.nago8.chat.old.utils.PrefUtils;
import com.nago8.chat.old.ws.WsClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ConversationsFragment extends Fragment {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private ConversationsAdapter adapter;
    private WsClient.MessageListener wsListener;
    private View searchBar;
    private View searchDivider;
    private EditText etSearch;
    private ImageView btnSearch;
    private ImageView btnSearchClose;
    private boolean searchMode = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_conversations, container, false);
        recyclerView = view.findViewById(R.id.recyclerView);
        progressBar = view.findViewById(R.id.progressBar);
        searchBar = view.findViewById(R.id.searchBar);
        searchDivider = view.findViewById(R.id.searchDivider);
        etSearch = view.findViewById(R.id.etSearch);
        btnSearch = view.findViewById(R.id.btnSearch);
        btnSearchClose = view.findViewById(R.id.btnSearchClose);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ConversationsAdapter();
        recyclerView.setAdapter(adapter);

        adapter.setOnConversationClickListener((data, position) -> {
            if (searchMode) {
                openChatFromSearch(data);
            } else {
                openChat(data, position);
            }
        });

        btnSearch.setOnClickListener(v -> doSearch());
        btnSearchClose.setOnClickListener(v -> hideSearch());
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doSearch();
                return true;
            }
            return false;
        });

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        fetchConversations();
    }

    @Override
    public void onResume() {
        super.onResume();
        // 在 onResume 注册，确保从 ChatActivity 返回后能重新监听
        wsListener = new WsClient.MessageListener() {
            @Override
            public void onPushMessage(WsMsg msg) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (adapter != null) {
                            adapter.onPushMessage(msg, getContext());
                        }
                    });
                }
            }
        };
        WsClient.getInstance().setMessageListener(wsListener);
    }

    @Override
    public void onPause() {
        // 注销监听，让 ChatActivity 等其他页面可以接管
        WsClient.getInstance().setMessageListener(null);
        wsListener = null;
        super.onPause();
    }

    private void fetchConversations() {
        String token = PrefUtils.getToken(getContext());
        if (token == null) return;

        progressBar.setVisibility(View.VISIBLE);

        ConversationListRequest listRequest = new ConversationListRequest.Builder()
                .md5("")
                .build();

        RequestBody body = RequestBody.create(
                MediaType.parse("application/x-protobuf"),
                listRequest.encode()
        );

        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/conversation/list")
                .header("token", token)
                .post(body)
                .build();

        ApiClient.getClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(getContext(), "获取会话失败", Toast.LENGTH_SHORT).show();
                    });
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        final ConversationList conversationList = ConversationList.ADAPTER.decode(response.body().source());
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                progressBar.setVisibility(View.GONE);
                                if (conversationList.data != null) {
                                    adapter.setData(conversationList.data);
                                }
                            });
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> progressBar.setVisibility(View.GONE));
                        }
                    }
                } else {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> progressBar.setVisibility(View.GONE));
                    }
                }
            }
        });
    }

    // ==================== 搜索 ====================

    /**
     * 展开搜索栏，由 HomeActivity 顶栏搜索图标触发。
     */
    public void showSearch() {
        searchMode = true;
        searchBar.setVisibility(View.VISIBLE);
        searchDivider.setVisibility(View.VISIBLE);
        etSearch.requestFocus();
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT);
    }

    /**
     * 收起搜索栏，恢复会话列表。
     */
    public void hideSearch() {
        searchMode = false;
        searchBar.setVisibility(View.GONE);
        searchDivider.setVisibility(View.GONE);
        etSearch.setText("");
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
        fetchConversations();
    }

    /**
     * 执行搜索，调用 /v1/search/home-search，结果复用会话 item 渲染。
     */
    private void doSearch() {
        String word = etSearch.getText().toString().trim();
        if (word.length() == 0) return;

        String token = PrefUtils.getToken(getContext());
        if (token == null) return;

        // 收起键盘
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);

        progressBar.setVisibility(View.VISIBLE);

        String json = ApiClient.getGson().toJson(new HashMap<String, String>() {{
            put("word", word);
        }});

        RequestBody body = RequestBody.create(
                MediaType.parse("application/json; charset=utf-8"), json);

        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/search/home-search")
                .header("token", token)
                .post(body)
                .build();

        ApiClient.getClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(getContext(), R.string.search_failed, Toast.LENGTH_SHORT).show();
                    });
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (getActivity() == null) return;
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String respStr = response.body().string();
                        final List<ConversationList.ConversationData> results = parseSearchResults(respStr);
                        getActivity().runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            adapter.setData(results);
                            if (results.isEmpty()) {
                                Toast.makeText(getContext(), R.string.search_no_result, Toast.LENGTH_SHORT).show();
                            }
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                        getActivity().runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(getContext(), R.string.search_failed, Toast.LENGTH_SHORT).show();
                        });
                    }
                } else {
                    getActivity().runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(getContext(), R.string.search_failed, Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    /**
     * 解析搜索结果 JSON，转成 ConversationData 列表以复用 adapter。
     * 响应结构: { code:1, data:{ list:[ { title:"用户", list:[...] }, ... ] } }
     * 每项: { friendId, friendType, nickname, name, avatarUrl, hit }
     */
    private List<ConversationList.ConversationData> parseSearchResults(String json) {
        List<ConversationList.ConversationData> results = new ArrayList<>();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (!root.has("data")) return results;
        JsonObject data = root.getAsJsonObject("data");
        if (!data.has("list")) return results;
        JsonArray categories = data.getAsJsonArray("list");

        for (JsonElement catElem : categories) {
            JsonObject cat = catElem.getAsJsonObject();
            if (!cat.has("list") || cat.get("list").isJsonNull()) continue;
            JsonArray items = cat.getAsJsonArray("list");
            for (JsonElement itemElem : items) {
                JsonObject item = itemElem.getAsJsonObject();
                String friendId = getJsonString(item, "friendId");
                int friendType = getJsonInt(item, "friendType", 1);
                String nickname = getJsonString(item, "nickname");
                String name = getJsonString(item, "name");
                String avatarUrl = getJsonString(item, "avatarUrl");

                // 显示名优先 nickname，其次 name
                String displayName = nickname.length() > 0 ? nickname : name;

                ConversationList.ConversationData cd = new ConversationList.ConversationData.Builder()
                        .chat_id(friendId)
                        .chat_type(friendType)
                        .name(displayName)
                        .avatar_url(avatarUrl)
                        .chat_content("")
                        .unread_message(0)
                        .timestamp_ms(0)
                        .build();
                results.add(cd);
            }
        }
        return results;
    }

    private String getJsonString(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return "";
    }

    private int getJsonInt(JsonObject obj, String key, int def) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsInt();
        }
        return def;
    }

    private void openChatFromSearch(ConversationList.ConversationData data) {
        if (getContext() == null || data == null) return;
        Intent intent = new Intent(getContext(), ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_CHAT_ID, data.chat_id);
        intent.putExtra(ChatActivity.EXTRA_CHAT_TYPE, data.chat_type);
        intent.putExtra(ChatActivity.EXTRA_CHAT_NAME, data.name);
        intent.putExtra(ChatActivity.EXTRA_CHAT_AVATAR, data.avatar_url);
        startActivity(intent);
    }

    private void openChat(ConversationList.ConversationData data, int position) {
        if (getContext() == null || data == null) return;

        Intent intent = new Intent(getContext(), ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_CHAT_ID, data.chat_id);
        intent.putExtra(ChatActivity.EXTRA_CHAT_TYPE, data.chat_type);
        intent.putExtra(ChatActivity.EXTRA_CHAT_NAME, data.name);
        intent.putExtra(ChatActivity.EXTRA_CHAT_AVATAR, data.avatar_url);
        startActivity(intent);

        dismissNotification(data.chat_id, position);
    }

    private void dismissNotification(String chatId, int position) {
        String token = PrefUtils.getToken(getContext());
        if (token == null) return;

        String json = "{\"chatId\":\"" + chatId + "\"}";
        RequestBody body = RequestBody.create(
                MediaType.parse("application/json; charset=utf-8"),
                json
        );

        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/conversation/dismiss-notification")
                .header("token", token)
                .post(body)
                .build();

        ApiClient.getClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "请求失败", Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "气泡：啊~我没了", Toast.LENGTH_SHORT).show();
                        adapter.markAsRead(position);
                    });
                }
            }
        });
    }
}
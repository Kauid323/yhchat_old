package com.nago8.chat.old.fragments;

import android.content.Intent;
import android.util.Log;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.nago8.chat.old.HomeActivity;
import com.nago8.chat.old.SearchHost;
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

public class ConversationsFragment extends Fragment implements SearchHost {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private ConversationsAdapter adapter;
    private WsClient.MessageListener wsListener;
    private boolean searchMode = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_conversations, container, false);
        recyclerView = view.findViewById(R.id.recyclerView);
        progressBar = view.findViewById(R.id.progressBar);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ConversationsAdapter();
        recyclerView.setAdapter(adapter);

        adapter.setOnConversationClickListener((data, position) -> {
            // 标题项（chat_id 为空）不响应点击
            if (data.chat_id == null || data.chat_id.length() == 0) return;

            if (searchMode) {
                openChatFromSearch(data);
            } else {
                openChat(data, position);
            }
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
        // 如果 listener 还没注册（首次 onResume），则注册
        if (wsListener == null) {
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
            WsClient.getInstance().addMessageListener(wsListener);
        }
    }

    @Override
    public void onDestroyView() {
        // Fragment 销毁时才注销，后台时保持监听以实时更新会话列表
        if (wsListener != null) {
            WsClient.getInstance().removeMessageListener(wsListener);
            wsListener = null;
        }
        super.onDestroyView();
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
                                  // 通知 HomeActivity 更新会话数量
                                  if (getActivity() instanceof HomeActivity) {
                                      ((HomeActivity) getActivity()).updateConversationCount(conversationList.data.size());
                                  }
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

    // ==================== 搜索（SearchHost 接口实现）====================

    /**
     * HomeActivity 顶栏搜索框输入后回调，执行搜索请求。
     */
    @Override
    public void onSearch(String word) {
        if (word.length() == 0) return;
        searchMode = true;

        String token = PrefUtils.getToken(getContext());
        if (token == null) return;

        progressBar.setVisibility(View.VISIBLE);

        HashMap<String, String> params = new HashMap<>();
        params.put("word", word);
        String json = ApiClient.getGson().toJson(params);

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
                    Log.e("ConvSearch", "onFailure", e);
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
                        Log.d("ConvSearch", "response: " + respStr);
                        final List<ConversationList.ConversationData> results = parseSearchResults(respStr);
                        getActivity().runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            adapter.setData(results);
                            Log.d("ConvSearch", "results size=" + results.size());
                            if (results.isEmpty()) {
                                Toast.makeText(getContext(), R.string.search_no_result, Toast.LENGTH_SHORT).show();
                            }
                        });
                    } catch (Exception e) {
                        Log.e("ConvSearch", "parse error", e);
                        e.printStackTrace();
                        getActivity().runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(getContext(), R.string.search_failed, Toast.LENGTH_SHORT).show();
                        });
                    }
                } else {
                    Log.d("ConvSearch", "http code=" + response.code());
                    getActivity().runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(getContext(), R.string.search_failed, Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    /**
     * HomeActivity 顶栏搜索框关闭后回调，退出搜索模式并重新加载会话列表。
     */
    @Override
    public void onSearchClosed() {
        searchMode = false;
        fetchConversations();
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
            if (items.size() == 0) continue;

            // 插入分组标题项（chat_id 为空标记为标题）
            String title = getJsonString(cat, "title");
            ConversationList.ConversationData header = new ConversationList.ConversationData.Builder()
                    .chat_id("")
                    .name(title)
                    .build();
            results.add(header);

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

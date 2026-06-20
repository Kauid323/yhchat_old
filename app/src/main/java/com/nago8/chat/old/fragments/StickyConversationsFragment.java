package com.nago8.chat.old.fragments;

import android.content.Intent;
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
import com.nago8.chat.old.R;
import com.nago8.chat.old.net.ApiClient;
import com.nago8.chat.old.proto.conversation.ConversationList;
import com.nago8.chat.old.proto.conversation.ConversationListRequest;
import com.nago8.chat.old.utils.PrefUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class StickyConversationsFragment extends Fragment {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private ConversationsAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_sticky_conversations, container, false);
        recyclerView = view.findViewById(R.id.recyclerView);
        progressBar = view.findViewById(R.id.progressBar);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ConversationsAdapter();
        recyclerView.setAdapter(adapter);

        adapter.setOnConversationClickListener((data, position) -> {
            if (data.chat_id == null || data.chat_id.length() == 0) return;
            if (getContext() == null) return;
            Intent intent = new Intent(getContext(), ChatActivity.class);
            intent.putExtra(ChatActivity.EXTRA_CHAT_ID, data.chat_id);
            intent.putExtra(ChatActivity.EXTRA_CHAT_TYPE, data.chat_type);
            intent.putExtra(ChatActivity.EXTRA_CHAT_NAME, data.name);
            intent.putExtra(ChatActivity.EXTRA_CHAT_AVATAR, data.avatar_url);
            startActivity(intent);
        });

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        fetchStickyList();
    }

    /**
     * 先请求会话列表（含完整信息），再请求置顶列表，
     * 用 chatId 匹配，从会话列表中取对应会话的完整信息（最后消息、未读数、时间戳等）。
     */
    private void fetchStickyList() {
        String token = PrefUtils.getToken(getContext());
        if (token == null) return;

        progressBar.setVisibility(View.VISIBLE);

        // 第一步：请求会话列表
        ConversationListRequest listRequest = new ConversationListRequest.Builder()
                .md5("")
                .build();
        RequestBody convBody = RequestBody.create(
                MediaType.parse("application/x-protobuf"),
                listRequest.encode()
        );
        Request convRequest = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/conversation/list")
                .header("token", token)
                .post(convBody)
                .build();

        ApiClient.getClient().newCall(convRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(getContext(), R.string.sticky_load_failed, Toast.LENGTH_SHORT).show();
                    });
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (getActivity() == null) return;
                if (!response.isSuccessful() || response.body() == null) {
                    getActivity().runOnUiThread(() -> progressBar.setVisibility(View.GONE));
                    return;
                }
                try {
                    // 解析会话列表，建立 chatId -> ConversationData 映射
                    final ConversationList conversationList = ConversationList.ADAPTER.decode(response.body().source());
                    final Map<String, ConversationList.ConversationData> convMap = new HashMap<>();
                    if (conversationList.data != null) {
                        for (ConversationList.ConversationData cd : conversationList.data) {
                            convMap.put(cd.chat_id, cd);
                        }
                    }
                    // 第二步：请求置顶列表，用 chatId 匹配取完整信息
                    fetchStickyListWithConvMap(token, convMap);
                } catch (Exception e) {
                    e.printStackTrace();
                    getActivity().runOnUiThread(() -> progressBar.setVisibility(View.GONE));
                }
            }
        });
    }

    /**
     * 请求置顶列表，用会话列表映射补全每项的完整信息。
     */
    private void fetchStickyListWithConvMap(String token, Map<String, ConversationList.ConversationData> convMap) {
        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/sticky/list")
                .header("token", token)
                .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), "{}"))
                .build();

        ApiClient.getClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(getContext(), R.string.sticky_load_failed, Toast.LENGTH_SHORT).show();
                    });
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String respStr = response.body().string();
                        final List<ConversationList.ConversationData> stickyList = parseStickyList(respStr, convMap);
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                progressBar.setVisibility(View.GONE);
                                adapter.setData(stickyList);
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

    private List<ConversationList.ConversationData> parseStickyList(String json, Map<String, ConversationList.ConversationData> convMap) {
        List<ConversationList.ConversationData> results = new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!root.has("data")) return results;
            JsonObject data = root.getAsJsonObject("data");
            if (!data.has("sticky") || data.get("sticky").isJsonNull()) return results;
            JsonArray stickyArray = data.getAsJsonArray("sticky");

            for (JsonElement elem : stickyArray) {
                JsonObject item = elem.getAsJsonObject();
                String chatId = getJsonString(item, "chatId");
                int chatType = getJsonInt(item, "chatType", 1);
                String chatName = getJsonString(item, "chatName");
                String avatarUrl = getJsonString(item, "avatarUrl");
                long sort = getJsonLong(item, "sort");

                // 优先从会话列表中取完整信息，匹配不到则用置顶 API 返回的数据
                ConversationList.ConversationData conv = convMap.get(chatId);
                ConversationList.ConversationData cd;
                if (conv != null) {
                    // 用会话列表的完整数据，保留置顶的 sort 作为排序时间
                    cd = new ConversationList.ConversationData.Builder()
                            .chat_id(conv.chat_id)
                            .chat_type(conv.chat_type)
                            .remark(conv.remark)
                            .chat_content(conv.chat_content)
                            .timestamp_ms(conv.timestamp_ms)
                            .unread_message(conv.unread_message)
                            .at(conv.at)
                            .avatar_id(conv.avatar_id)
                            .avatar_url(conv.avatar_url)
                            .do_not_disturb(conv.do_not_disturb)
                            .send_timestamp(conv.send_timestamp)
                            .at_data(conv.at_data)
                            .name(conv.name)
                            .certification_level(conv.certification_level)
                            .build();
                } else {
                    cd = new ConversationList.ConversationData.Builder()
                            .chat_id(chatId)
                            .chat_type(chatType)
                            .name(chatName)
                            .avatar_url(avatarUrl)
                            .chat_content("")
                            .unread_message(0)
                            .timestamp_ms(sort)
                            .build();
                }
                results.add(cd);
            }
        } catch (Exception e) {
            e.printStackTrace();
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

    private long getJsonLong(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsLong();
        }
        return 0;
    }
}

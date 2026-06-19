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

    private void fetchStickyList() {
        String token = PrefUtils.getToken(getContext());
        if (token == null) return;

        progressBar.setVisibility(View.VISIBLE);

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
                        final List<ConversationList.ConversationData> stickyList = parseStickyList(respStr);
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

    private List<ConversationList.ConversationData> parseStickyList(String json) {
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

                ConversationList.ConversationData cd = new ConversationList.ConversationData.Builder()
                        .chat_id(chatId)
                        .chat_type(chatType)
                        .name(chatName)
                        .avatar_url(avatarUrl)
                        .chat_content("")
                        .unread_message(0)
                        .timestamp_ms(sort)
                        .build();
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

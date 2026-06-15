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

import com.nago8.chat.old.R;
import com.nago8.chat.old.ChatActivity;
import com.nago8.chat.old.net.ApiClient;
import com.nago8.chat.old.proto.conversation.ConversationList;
import com.nago8.chat.old.proto.conversation.ConversationListRequest;
import com.nago8.chat.old.utils.PrefUtils;

import java.io.IOException;

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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_conversations, container, false);
        recyclerView = view.findViewById(R.id.recyclerView);
        progressBar = view.findViewById(R.id.progressBar);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ConversationsAdapter();
        recyclerView.setAdapter(adapter);
        
        adapter.setOnConversationClickListener((data, position) -> openChat(data, position));
        
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        fetchConversations();
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

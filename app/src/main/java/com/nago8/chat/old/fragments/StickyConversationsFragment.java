package com.nago8.chat.old.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
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
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class StickyConversationsFragment extends Fragment {

    private static final String TAG = "StickyConvFragment";
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ConversationsAdapter adapter;
    private WsClient.MessageListener wsListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_sticky_conversations, container, false);
        recyclerView = view.findViewById(R.id.recyclerView);
        progressBar = view.findViewById(R.id.progressBar);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(() -> refreshData());
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(20);
        recyclerView.setItemAnimator(null);
        adapter = new ConversationsAdapter();
        recyclerView.setAdapter(adapter);

        adapter.setOnConversationActionListener(new ConversationsAdapter.OnConversationActionListener() {
            @Override
            public void onConversationClick(ConversationList.ConversationData data, int position) {
                if (data.chat_id == null || data.chat_id.length() == 0) return;
                if (getContext() == null) return;
                Intent intent = new Intent(getContext(), ChatActivity.class);
                intent.putExtra(ChatActivity.EXTRA_CHAT_ID, data.chat_id);
                intent.putExtra(ChatActivity.EXTRA_CHAT_TYPE, data.chat_type);
                intent.putExtra(ChatActivity.EXTRA_CHAT_NAME, data.name);
                intent.putExtra(ChatActivity.EXTRA_CHAT_AVATAR, data.avatar_url);
                startActivity(intent);

                dismissNotification(data.chat_id, position);
            }

            @Override
            public void onPinToggle(ConversationList.ConversationData data, boolean isSticky, int position) {
                if (data == null || data.chat_id == null || data.chat_id.isEmpty()) return;
                int chatType = data.chat_type != 0 ? data.chat_type : 1;
                toggleStickyConversation(data.chat_id, chatType, isSticky);
            }

            @Override
            public void onDeleteConversation(ConversationList.ConversationData data, int position) {
                if (data == null || data.chat_id == null || data.chat_id.isEmpty()) return;
                deleteConversation(data.chat_id, position);
            }
        });

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        loadStickyData();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadStickyData();
        if (wsListener == null) {
            wsListener = new WsClient.MessageListener() {
                @Override
                public void onPushMessage(WsMsg msg) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (getActivity() instanceof HomeActivity) {
                                HomeActivity home = (HomeActivity) getActivity();
                                home.onPushMessageInMemory(msg, getContext());
                                if (adapter != null) {
                                    adapter.setData(home.getStickyConversationDataList());
                                }
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
        if (wsListener != null) {
            WsClient.getInstance().removeMessageListener(wsListener);
            wsListener = null;
        }
        super.onDestroyView();
    }

    private void loadStickyData() {
        if (getActivity() instanceof HomeActivity) {
            HomeActivity home = (HomeActivity) getActivity();
            List<ConversationList.ConversationData> stickyData = home.getStickyConversationDataList();
            if (stickyData != null && !stickyData.isEmpty()) {
                progressBar.setVisibility(View.GONE);
                adapter.setData(stickyData);
                return;
            }
        }
        // 若本地暂无置顶数据，则联网拉取
        fetchStickyList();
    }

    public void refreshData() {
        if (getContext() == null) return;
        progressBar.setVisibility(View.VISIBLE);
        fetchStickyList(true);
    }

    private void fetchStickyList() {
        fetchStickyList(false);
    }

    private void fetchStickyList(boolean isManualRefresh) {
        String token = PrefUtils.getToken(getContext());
        if (token == null) return;

        if (!isManualRefresh && (adapter == null || adapter.getItemCount() == 0)) {
            progressBar.setVisibility(View.VISIBLE);
        }

        if (getActivity() instanceof HomeActivity) {
            ((HomeActivity) getActivity()).fetchStickyCount();
        }

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
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                        Toast.makeText(getContext(), R.string.sticky_load_failed, Toast.LENGTH_SHORT).show();
                    });
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                    });
                }
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        final ConversationList conversationList = ConversationList.ADAPTER.decode(response.body().source());
                        if (conversationList.data != null && getActivity() instanceof HomeActivity) {
                            getActivity().runOnUiThread(() -> {
                                HomeActivity home = (HomeActivity) getActivity();
                                home.updateConversationDataList(conversationList.data);
                                List<ConversationList.ConversationData> stickyData = home.getStickyConversationDataList();
                                adapter.setData(stickyData);
                                if (isManualRefresh) {
                                    Toast.makeText(getContext(), R.string.sticky_refreshed, Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "fetchStickyList error", e);
                    } finally {
                        if (response.body() != null) {
                            response.body().close();
                        }
                    }
                }
            }
        });
    }

    private void dismissNotification(String chatId, int position) {
        String token = PrefUtils.getToken(getContext());
        if (token == null) return;

        if (getActivity() instanceof HomeActivity) {
            ((HomeActivity) getActivity()).markConversationReadInMemory(chatId);
            loadStickyData();
        }

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
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "dismissNotification failed", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (response.body() != null) {
                    response.body().close();
                }
            }
        });
    }

    private void toggleStickyConversation(String chatId, int chatType, boolean currentSticky) {
        String token = PrefUtils.getToken(getContext());
        if (token == null || token.isEmpty()) return;

        String endpoint = currentSticky ? "/v1/sticky/delete" : "/v1/sticky/add";
        String json = "{\"chatId\":\"" + chatId + "\",\"chatType\":" + chatType + "}";
        RequestBody body = RequestBody.create(
                MediaType.parse("application/json; charset=utf-8"),
                json
        );

        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + endpoint)
                .header("token", token)
                .post(body)
                .build();

        ApiClient.getClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "操作失败", Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), currentSticky ? "已取消置顶" : "已置顶", Toast.LENGTH_SHORT).show();
                        if (getActivity() instanceof HomeActivity) {
                            ((HomeActivity) getActivity()).fetchStickyCount();
                        }
                        loadStickyData();
                    });
                }
                if (response.body() != null) response.body().close();
            }
        });
    }

    private void deleteConversation(String chatId, int position) {
        String token = PrefUtils.getToken(getContext());
        if (token == null || token.isEmpty()) return;

        String json = "{\"chatId\":\"" + chatId + "\"}";
        RequestBody body = RequestBody.create(
                MediaType.parse("application/json; charset=utf-8"),
                json
        );

        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/conversation/remove")
                .header("token", token)
                .post(body)
                .build();

        ApiClient.getClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> Toast.makeText(getContext(), "删除失败", Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "已删除会话", Toast.LENGTH_SHORT).show();
                        if (getActivity() instanceof HomeActivity) {
                            HomeActivity home = (HomeActivity) getActivity();
                            List<ConversationList.ConversationData> currentList = home.getCachedConversationList();
                            List<ConversationList.ConversationData> updatedList = new ArrayList<>();
                            if (currentList != null) {
                                for (ConversationList.ConversationData cd : currentList) {
                                    if (!chatId.equals(cd.chat_id)) {
                                        updatedList.add(cd);
                                    }
                                }
                            }
                            home.updateConversationDataList(updatedList);
                        }
                        loadStickyData();
                    });
                }
                if (response.body() != null) response.body().close();
            }
        });
    }
}

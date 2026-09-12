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

import com.nago8.chat.old.ChatActivity;
import com.nago8.chat.old.HomeActivity;
import com.nago8.chat.old.R;
import com.nago8.chat.old.cache.ConversationCache;
import com.nago8.chat.old.net.ApiClient;
import com.nago8.chat.old.proto.conversation.ConversationList;
import com.nago8.chat.old.proto.conversation.ConversationListRequest;
import com.nago8.chat.old.utils.PrefUtils;

import java.io.IOException;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class ConversationsFragment extends Fragment {

    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ConversationsAdapter adapter;
    private ConversationCache.OnConversationDataChangeListener dataChangeListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_conversations, container, false);
        RecyclerView recyclerView = view.findViewById(R.id.recyclerView);
        progressBar = view.findViewById(R.id.progressBar);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(this::refreshData);
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(20);
        androidx.recyclerview.widget.DefaultItemAnimator animator = new androidx.recyclerview.widget.DefaultItemAnimator();
        animator.setSupportsChangeAnimations(false);
        animator.setMoveDuration(250);
        recyclerView.setItemAnimator(animator);
        adapter = new ConversationsAdapter();
        recyclerView.setAdapter(adapter);

        adapter.setOnConversationActionListener(new ConversationsAdapter.OnConversationActionListener() {
            @Override
            public void onConversationClick(ConversationList.ConversationData data, int position) {
                if (data == null || data.chat_id == null || data.chat_id.isEmpty()) return;
                openChat(data, position);
            }

            @Override
            public void onPinToggle(ConversationList.ConversationData data, boolean isSticky, int position) {
                if (data == null || data.chat_id == null || data.chat_id.isEmpty()) return;
                int chatType = data.chat_type != 0 ? data.chat_type : 1;
                toggleStickyConversation(data.chat_id, chatType, isSticky);
            }

            @Override
            public void onArchiveConversation(ConversationList.ConversationData data, int position) {
                if (data == null || data.chat_id == null || data.chat_id.isEmpty() || getContext() == null) return;
                com.nago8.chat.old.cache.ArchiveManager.getInstance().archiveConversation(getContext(), data);
                Toast.makeText(getContext(), R.string.conversation_archived_toast, Toast.LENGTH_SHORT).show();
                if (getActivity() instanceof HomeActivity) {
                    HomeActivity home = (HomeActivity) getActivity();
                    if (adapter != null) {
                        adapter.setData(home.getCachedConversationList());
                    }
                }
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
        loadConversationsData();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadConversationsData();
        if (dataChangeListener == null) {
            dataChangeListener = () -> {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (adapter != null) {
                            adapter.setData(ConversationCache.getInstance().getConversationList());
                        }
                    });
                }
            };
            ConversationCache.getInstance().addOnConversationDataChangeListener(dataChangeListener);
        }
    }

    private void loadConversationsData() {
        List<ConversationList.ConversationData> cached = ConversationCache.getInstance().getConversationList();
        if (cached != null && !cached.isEmpty()) {
            progressBar.setVisibility(View.GONE);
            adapter.setData(cached);
            return;
        }
        fetchConversations();
    }

    @Override
    public void onDestroyView() {
        if (dataChangeListener != null) {
            ConversationCache.getInstance().removeOnConversationDataChangeListener(dataChangeListener);
            dataChangeListener = null;
        }
        super.onDestroyView();
    }

    public void refreshData() {
        if (getContext() == null) return;
        progressBar.setVisibility(View.VISIBLE);
        fetchConversations(true);
    }

    private void fetchConversations() {
        fetchConversations(false);
    }

    private void fetchConversations(boolean isManualRefresh) {
        String token = PrefUtils.getToken(getContext());
        if (token == null) return;

        if (!isManualRefresh && (adapter == null || adapter.getItemCount() == 0)) {
            progressBar.setVisibility(View.VISIBLE);
        }

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
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                        Toast.makeText(getContext(), R.string.conv_fetch_failed, Toast.LENGTH_SHORT).show();
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
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                if (conversationList.data != null) {
                                    ConversationCache.getInstance().updateConversationList(conversationList.data);
                                    if (getActivity() instanceof HomeActivity) {
                                        HomeActivity home = (HomeActivity) getActivity();
                                        java.util.List<String> dndIds = new java.util.ArrayList<>();
                                        for (ConversationList.ConversationData cd : conversationList.data) {
                                            if (cd.do_not_disturb != 0) {
                                                dndIds.add(cd.chat_id);
                                            }
                                        }
                                        home.updateDoNotDisturbSet(dndIds);
                                        home.updateConvInfoCache(conversationList.data);
                                    }
                                    adapter.setData(ConversationCache.getInstance().getConversationList());
                                }
                                if (isManualRefresh) {
                                    Toast.makeText(getContext(), R.string.conversations_refreshed, Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    } catch (Exception e) {
                        Log.e("ConversationsFragment", "fetchConversations parse error", e);
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

        ConversationCache.getInstance().markAsRead(getContext(), data.chat_id);
        adapter.markAsRead(position);
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
                    getActivity().runOnUiThread(() -> Toast.makeText(getContext(), R.string.conv_operation_failed, Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), currentSticky ? R.string.conv_sticky_removed : R.string.conv_sticky_added, Toast.LENGTH_SHORT).show();
                        if (getActivity() instanceof HomeActivity) {
                            ((HomeActivity) getActivity()).fetchStickyCount();
                        }
                        loadConversationsData();
                    });
                }
                if (response.body() != null) response.body().close();
            }
        });
    }

    private void deleteConversation(String chatId, @SuppressWarnings("unused") int position) {
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
                    getActivity().runOnUiThread(() -> Toast.makeText(getContext(), R.string.conv_delete_failed, Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), R.string.conv_deleted, Toast.LENGTH_SHORT).show();
                        com.nago8.chat.old.cache.ConversationCache.getInstance().removeConversationFromMainList(chatId);
                        if (getActivity() instanceof HomeActivity) {
                            HomeActivity home = (HomeActivity) getActivity();
                            adapter.setData(home.getCachedConversationList());
                        }
                    });
                }
                if (response.body() != null) response.body().close();
            }
        });
    }
}

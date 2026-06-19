package com.nago8.chat.old;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nago8.chat.old.fragments.MessagesAdapter;
import com.nago8.chat.old.model.MessageGroup;
import com.nago8.chat.old.net.MessageRepository;
import com.nago8.chat.old.net.ApiClient;
import com.nago8.chat.old.proto.Msg;
import com.nago8.chat.old.proto.send_message;
import com.nago8.chat.old.proto.list_message;
import com.nago8.chat.old.proto.list_message_by_seq;
import com.nago8.chat.old.proto.group.info;
import com.nago8.chat.old.proto.group.info_send;
import com.nago8.chat.old.utils.PrefUtils;
import com.nago8.chat.old.utils.LocaleHelper;
import com.nago8.chat.old.utils.WsMsgConverter;
import com.nago8.chat.old.ws.WsClient;
import com.nago8.chat.old.proto.chat_ws_go.WsMsg;


import java.io.IOException;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ChatActivity extends AppCompatActivity {
    public static final String EXTRA_CHAT_ID = "chat_id";
    public static final String EXTRA_CHAT_TYPE = "chat_type";
    public static final String EXTRA_CHAT_NAME = "chat_name";
    public static final String EXTRA_CHAT_AVATAR = "chat_avatar";

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private MessagesAdapter adapter;
    private LinearLayoutManager layoutManager;
    private MessageRepository repository;
    private Call runningCall;
    private Call olderCall;
    private Call sendCall;
    private final List<Msg> allMessages = new ArrayList<>();
    private boolean loadingOlder = false;
    private boolean noMoreOlder = false;
    private LinearLayout inputBar;
    private TextView tvTitle;
    private Call groupInfoCall;
    private final Set<String> adminIds = new HashSet<>();
    private String ownerId;
    private WsClient.MessageListener wsListener;
    private AppCompatEditText etMessage;
    private AppCompatImageButton btnSend;

    private String chatId;
    private int chatType;
    private String chatName;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        installCrashLogger();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        chatId = getIntent().getStringExtra(EXTRA_CHAT_ID);
        chatType = getIntent().getIntExtra(EXTRA_CHAT_TYPE, 0);
        chatName = getIntent().getStringExtra(EXTRA_CHAT_NAME);

        AppCompatImageButton btnBack = findViewById(R.id.btnBack);
        AppCompatImageButton btnMore = findViewById(R.id.btnMore);
        tvTitle = findViewById(R.id.tvTitle);
        recyclerView = findViewById(R.id.recyclerViewMessages);
        progressBar = findViewById(R.id.progressBar);
        tvEmpty = findViewById(R.id.tvEmpty);

        if (chatName == null || chatName.length() == 0) chatName = chatId == null ? getString(R.string.chat_default_title) : chatId;
        tvTitle.setText(chatName);

        // 群聊时请求群信息，在标题后追加 (人数)
        if (chatType == 2) {
            fetchGroupInfo();
        }

        btnBack.setOnClickListener(v -> onBackPressed());
        btnMore.setOnClickListener(v -> {
            if (chatType == 1) {
                Intent intent = new Intent(this, UserProfileActivity.class);
                intent.putExtra(UserProfileActivity.EXTRA_USER_ID, chatId);
                startActivity(intent);
            } else if (chatType == 3) {
                Intent intent = new Intent(this, BotProfileActivity.class);
                intent.putExtra(BotProfileActivity.EXTRA_BOT_ID, chatId);
                startActivity(intent);
            } else if (chatType == 2) {
                Intent intent = new Intent(this, GroupProfileActivity.class);
                intent.putExtra(GroupProfileActivity.EXTRA_GROUP_ID, chatId);
                startActivity(intent);
            } else {
                Toast.makeText(this, R.string.action_more, Toast.LENGTH_SHORT).show();
            }
        });

        layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.addOnScrollListener(new TopLoadScrollListener());
        adapter = new MessagesAdapter();
        recyclerView.setAdapter(adapter);

        adapter.setOnAvatarClickListener((senderId, senderChatType) -> {
            // 根据 sender 的 chat type 判断：机器人(3)跳机器人详情，其他跳用户详情
            if (senderChatType == 3) {
                Intent intent = new Intent(this, BotProfileActivity.class);
                intent.putExtra(BotProfileActivity.EXTRA_BOT_ID, senderId);
                startActivity(intent);
            } else if (senderChatType == 1) {
                Intent intent = new Intent(this, UserProfileActivity.class);
                intent.putExtra(UserProfileActivity.EXTRA_USER_ID, senderId);
                startActivity(intent);
            }
        });

        setupComposeInput();

        repository = new MessageRepository();
        fetchMessages();
    }

    @Override
    protected void onDestroy() {
        if (runningCall != null) runningCall.cancel();
        if (olderCall != null) olderCall.cancel();
        if (sendCall != null) sendCall.cancel();
        if (groupInfoCall != null) groupInfoCall.cancel();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        wsListener = new WsClient.MessageListener() {
            @Override
            public void onPushMessage(WsMsg wsMsg) {
                runOnUiThread(() -> handlePushMessage(wsMsg));
            }
        };
        WsClient.getInstance().addMessageListener(wsListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (wsListener != null) {
            WsClient.getInstance().removeMessageListener(wsListener);
            wsListener = null;
        }
    }

    private void handlePushMessage(WsMsg wsMsg) {
        if (wsMsg == null || wsMsg.chat_id == null) return;
        // 只处理当前聊天界面的消息
        if (!chatId.equals(wsMsg.chat_id)) return;

        String myUserId = PrefUtils.getUserId(this);
        Msg msg = WsMsgConverter.convert(wsMsg, myUserId);
        if (msg == null) return;

        // 去重
        if (msg.msg_id != null && msg.msg_id.length() > 0) {
            for (Msg existing : allMessages) {
                if (existing != null && msg.msg_id.equals(existing.msg_id)) return;
            }
        }

        allMessages.add(msg);
        refreshMessages(true);
    }

    private void setupComposeInput() {
        inputBar = findViewById(R.id.inputBar);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);

        btnSend.setOnClickListener(v -> {
            String text = etMessage.getText() != null ? etMessage.getText().toString().trim() : "";
            if (text.length() == 0) return;
            performSend(text);
        });

        etMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                boolean hasText = s != null && s.toString().trim().length() > 0;
                btnSend.setAlpha(hasText ? 1.0f : 0.4f);
                btnSend.setEnabled(hasText);
            }
        });
        btnSend.setAlpha(0.4f);
        btnSend.setEnabled(false);
    }

    private void performSend(String text) {
        String token = PrefUtils.getToken(this);
        btnSend.setEnabled(false);
        sendCall = repository.sendMessage(token, chatId, chatType, text, new MessageRepository.SendMessageCallback() {
            @Override
            public void onSuccess(send_message response) {
                runOnUiThread(() -> {
                    etMessage.setText("");
                    btnSend.setEnabled(true);
                    // 发送成功后依赖 WS 推送自动插入消息到列表
                    // 如果 WS 未推送，做一次增量拉取
                    fetchLatestMessage();
                });
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> {
                    btnSend.setEnabled(true);
                    Toast.makeText(ChatActivity.this, R.string.send_failed, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void fetchLatestMessage() {
        // 增量拉取最新消息（用最大 msg_seq + 1 作为起点）
        long maxSeq = 0;
        for (Msg msg : allMessages) {
            if (msg != null && msg.msg_seq > maxSeq) maxSeq = msg.msg_seq;
        }

        String token = PrefUtils.getToken(this);
        runningCall = repository.listMessageBySeq(token, chatId, chatType, maxSeq, new MessageRepository.MessageListCallback() {
            @Override
            public void onSuccess(list_message_by_seq response) {
                runOnUiThread(() -> {
                    int added = mergeMessages(response == null ? null : response.msg);
                    if (added > 0) refreshMessages(true);
                });
            }

            @Override
            public void onError(Exception error) {
                // 静默失败，依赖 WS 推送
            }
        });
    }

    private void fetchGroupInfo() {
        String token = PrefUtils.getToken(this);
        if (token == null) return;

        info_send requestProto = new info_send.Builder()
                .group_id(chatId)
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

        groupInfoCall = ApiClient.getClient().newCall(request);
        groupInfoCall.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // 静默失败，标题保持原样
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        final info result = info.ADAPTER.decode(response.body().source());
                        runOnUiThread(() -> {
                            if (result != null && result.data != null) {
                                // 保存管理员ID列表，用于消息列表显示管理员标签
                                adminIds.clear();
                                if (result.data.admin != null) {
                                    adminIds.addAll(result.data.admin);
                                }
                                // 保存群主ID，用于消息列表显示群主标签
                                ownerId = result.data.owner;
                                // 群主也是管理员，加入 adminIds 以兼容逻辑
                                if (ownerId != null && ownerId.length() > 0) {
                                    adminIds.add(ownerId);
                                }
                                // 标题格式：群名 (人数)
                                String displayName = result.data.name != null && result.data.name.length() > 0
                                        ? result.data.name : chatName;
                                tvTitle.setText(displayName + " (" + result.data.member + ")");
                                // 刷新消息列表以应用管理员标签
                                refreshMessages(false);
                            }
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void fetchMessages() {
        String token = PrefUtils.getToken(this);
        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        runningCall = repository.listMessageBySeq(token, chatId, chatType, 0, new MessageRepository.MessageListCallback() {
            @Override
            public void onSuccess(list_message_by_seq response) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    allMessages.clear();
                    mergeMessages(response == null ? null : response.msg);
                    refreshMessages(true);
                });
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvEmpty.setVisibility(View.VISIBLE);
                    Toast.makeText(ChatActivity.this, R.string.chat_load_failed, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void loadOlderMessages() {
        if (loadingOlder || noMoreOlder || allMessages.isEmpty()) return;

        Msg oldest = findOldestMessage();
        if (oldest == null || oldest.msg_id == null || oldest.msg_id.length() == 0) return;

        String token = PrefUtils.getToken(this);
        loadingOlder = true;
        int oldGroupCount = adapter.getItemCount();
        olderCall = repository.listMessage(token, chatId, chatType, oldest.msg_id, 30, new MessageRepository.OlderMessageListCallback() {
            @Override
            public void onSuccess(list_message response) {
                runOnUiThread(() -> {
                    loadingOlder = false;
                    int added = mergeMessages(response == null ? null : response.msg);
                    if (added == 0) noMoreOlder = true;

                    List<MessageGroup> beforeGroups = MessageGroup.fromMessages(allMessages);
                    refreshMessages(false);
                    int newGroupCount = adapter.getItemCount();
                    int insertedGroups = Math.max(0, newGroupCount - oldGroupCount);
                    if (insertedGroups > 0) {
                        recyclerView.scrollToPosition(Math.min(insertedGroups, newGroupCount - 1));
                    } else if (!beforeGroups.isEmpty()) {
                        recyclerView.scrollToPosition(0);
                    }
                });
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> {
                    loadingOlder = false;
                    Toast.makeText(ChatActivity.this, R.string.chat_load_older_failed, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private int mergeMessages(List<Msg> messages) {
        if (messages == null || messages.isEmpty()) return 0;

        Set<String> existed = new HashSet<>();
        for (Msg msg : allMessages) {
            if (msg != null && msg.msg_id != null) existed.add(msg.msg_id);
        }

        int added = 0;
        for (Msg msg : messages) {
            if (msg == null || msg.msg_id == null || existed.contains(msg.msg_id)) continue;
            allMessages.add(msg);
            existed.add(msg.msg_id);
            added++;
        }
        sortMessagesOldToNew();
        return added;
    }

    private void sortMessagesOldToNew() {
        Collections.sort(allMessages, new Comparator<Msg>() {
            @Override
            public int compare(Msg left, Msg right) {
                long leftTime = left == null ? 0 : left.send_time;
                long rightTime = right == null ? 0 : right.send_time;
                if (leftTime == rightTime) return 0;
                return leftTime < rightTime ? -1 : 1;
            }
        });
    }

    private Msg findOldestMessage() {
        Msg oldest = null;
        for (Msg msg : allMessages) {
            if (msg == null) continue;
            if (oldest == null || msg.send_time < oldest.send_time) oldest = msg;
        }
        return oldest;
    }

    private void refreshMessages(boolean scrollToBottom) {
        sortMessagesOldToNew();
        List<MessageGroup> groups = MessageGroup.fromMessages(allMessages);
        // 标记管理员消息
        for (MessageGroup group : groups) {
            group.isAdmin = group.senderId != null && adminIds.contains(group.senderId);
            group.isOwner = ownerId != null && ownerId.length() > 0 && ownerId.equals(group.senderId);
        }
        adapter.setData(groups);
        tvEmpty.setVisibility(groups.isEmpty() ? View.VISIBLE : View.GONE);
        if (scrollToBottom && !groups.isEmpty()) recyclerView.scrollToPosition(groups.size() - 1);
    }

    private class TopLoadScrollListener extends RecyclerView.OnScrollListener {
        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);
            if (dy >= 0 || layoutManager == null) return;
            if (layoutManager.findFirstVisibleItemPosition() == 0) {
                loadOlderMessages();
            }
        }
    }

    private void installCrashLogger() {
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            writeCrashLog(throwable);
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            }
        });
    }

    private void writeCrashLog(Throwable throwable) {
        try {
            File file = new File(getFilesDir(), "chat_crash.log");
            PrintWriter writer = new PrintWriter(new FileWriter(file, true));
            writer.println("--- ChatActivity crash ---");
            throwable.printStackTrace(writer);
            writer.close();
        } catch (Exception ignored) {
        }
    }
}

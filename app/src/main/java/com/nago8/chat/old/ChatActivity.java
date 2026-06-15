package com.nago8.chat.old;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nago8.chat.old.fragments.MessagesAdapter;
import com.nago8.chat.old.model.MessageGroup;
import com.nago8.chat.old.net.MessageRepository;
import com.nago8.chat.old.proto.Msg;
import com.nago8.chat.old.proto.list_message;
import com.nago8.chat.old.proto.list_message_by_seq;
import com.nago8.chat.old.utils.PrefUtils;

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
    private final List<Msg> allMessages = new ArrayList<>();
    private boolean loadingOlder = false;
    private boolean noMoreOlder = false;

    private String chatId;
    private int chatType;
    private String chatName;

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
        TextView tvTitle = findViewById(R.id.tvTitle);
        recyclerView = findViewById(R.id.recyclerViewMessages);
        progressBar = findViewById(R.id.progressBar);
        tvEmpty = findViewById(R.id.tvEmpty);

        if (chatName == null || chatName.length() == 0) chatName = chatId == null ? "聊天" : chatId;
        tvTitle.setText(chatName);

        btnBack.setOnClickListener(v -> finish());
        btnMore.setOnClickListener(v -> Toast.makeText(this, "更多", Toast.LENGTH_SHORT).show());

        layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.addOnScrollListener(new TopLoadScrollListener());
        adapter = new MessagesAdapter();
        recyclerView.setAdapter(adapter);

        repository = new MessageRepository();
        fetchMessages();
    }

    @Override
    protected void onDestroy() {
        if (runningCall != null) runningCall.cancel();
        if (olderCall != null) olderCall.cancel();
        super.onDestroy();
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
                    Toast.makeText(ChatActivity.this, "获取聊天记录失败", Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(ChatActivity.this, "获取更早消息失败", Toast.LENGTH_SHORT).show();
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

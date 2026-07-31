package com.nago8.chat.old;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nago8.chat.old.fragments.MessagesAdapter;
import com.nago8.chat.old.model.MessageGroup;
import com.nago8.chat.old.repository.MessageRepository;
import com.nago8.chat.old.repository.GroupRepository;
import com.nago8.chat.old.proto.Msg;
import com.nago8.chat.old.proto.send_message;
import com.nago8.chat.old.proto.list_message;
import com.nago8.chat.old.proto.list_message_by_seq;
import com.nago8.chat.old.proto.group.info;
import com.nago8.chat.old.utils.PrefUtils;
import com.nago8.chat.old.utils.LocaleHelper;
import com.nago8.chat.old.utils.WsMsgConverter;
import com.nago8.chat.old.ws.WsClient;
import com.nago8.chat.old.proto.chat_ws_go.WsMsg;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import okhttp3.Call;

import com.nago8.chat.old.components.ChatInputBar;

public class ChatActivity extends AppCompatActivity {
    public static final String EXTRA_CHAT_ID = "chat_id";
    public static final String EXTRA_CHAT_TYPE = "chat_type";
    public static final String EXTRA_CHAT_NAME = "chat_name";
    public static final String EXTRA_CHAT_AVATAR = "chat_avatar";
    private static final int REQUEST_CODE_PICK_IMAGES = 2001;
    private static final int REQUEST_CODE_PICK_FILES = 2002;
    private static final int REQUEST_CODE_TAKE_PHOTO = 2003;
    private static final int REQUEST_CODE_CAMERA_PERMISSION = 3002;

    private Uri currentPhotoUri;

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private MessagesAdapter adapter;
    private LinearLayoutManager layoutManager;
    private MessageRepository repository;
    private GroupRepository groupRepository;
    private Call runningCall;
    private Call olderCall;
    private Call sendCall;
    private final List<Msg> allMessages = new ArrayList<>();
    private boolean loadingOlder = false;
    private boolean noMoreOlder = false;
    private ChatInputBar chatInputBar;
    private TextView tvTitle;
    private Call groupInfoCall;
    private final Set<String> adminIds = new HashSet<>();
    private String ownerId;
    private WsClient.MessageListener wsListener;

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

        repository = new MessageRepository();
        groupRepository = new GroupRepository();

        AppCompatImageButton btnBack = findViewById(R.id.btnBack);
        AppCompatImageButton btnMore = findViewById(R.id.btnMore);
        tvTitle = findViewById(R.id.tvTitle);
        recyclerView = findViewById(R.id.recyclerViewMessages);
        progressBar = findViewById(R.id.progressBar);
        tvEmpty = findViewById(R.id.tvEmpty);

        if (chatName == null || chatName.isEmpty()) chatName = chatId == null ? getString(R.string.chat_default_title) : chatId;
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
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(20);
        recyclerView.setItemAnimator(null);
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
        // 设置当前聊天会话，WsClient 据此跳过通知
        com.nago8.chat.old.ws.WsClient.getInstance().setActiveChatId(chatId);
        // 取消当前会话的通知
        com.nago8.chat.old.utils.NotificationHelper.cancelNotification(this, chatId);
        wsListener = wsMsg -> runOnUiThread(() -> handlePushMessage(wsMsg));
        WsClient.getInstance().addMessageListener(wsListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        com.nago8.chat.old.ws.WsClient.getInstance().setActiveChatId(null);
        if (wsListener != null) {
            WsClient.getInstance().removeMessageListener(wsListener);
            wsListener = null;
        }
    }

    private void handlePushMessage(WsMsg wsMsg) {
        if (wsMsg == null) return;
        String myUserId = PrefUtils.getUserId(this);
        String targetChatId = WsClient.getTargetChatId(wsMsg, myUserId);
        // 只处理当前聊天界面的消息
        if (!chatId.equals(targetChatId)) return;

        Msg msg = WsMsgConverter.convert(wsMsg, myUserId);
        if (msg == null) return;

        // 去重
        if (msg.msg_id != null && !msg.msg_id.isEmpty()) {
            for (Msg existing : allMessages) {
                if (existing != null && msg.msg_id.equals(existing.msg_id)) return;
            }
        }

        allMessages.add(msg);
        // 只有用户当前在底部附近时才滚动到最新消息，否则保持当前位置
        refreshMessages(isAtBottom());
    }

    /**
     * 判断用户是否在消息列表底部附近（最后一条可见）。
     */
    private boolean isAtBottom() {
        if (layoutManager == null || adapter == null) return true;
        int total = adapter.getItemCount();
        if (total == 0) return true;
        int lastVisible = layoutManager.findLastCompletelyVisibleItemPosition();
        // 允许差 1 条的容差（最后一条可能只是部分可见）
        return lastVisible >= total - 2;
    }

    @android.annotation.SuppressLint("InlinedApi")
    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(intent, "选择图片"), REQUEST_CODE_PICK_IMAGES);
    }

    @android.annotation.SuppressLint("InlinedApi")
    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(intent, "选择文件"), REQUEST_CODE_PICK_FILES);
    }

    private void openCamera() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_CAMERA_PERMISSION);
                return;
            }
        }

        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                String timeStamp = String.valueOf(System.currentTimeMillis());
                File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
                if (storageDir == null) storageDir = getCacheDir();
                photoFile = File.createTempFile("JPEG_" + timeStamp + "_", ".jpg", storageDir);
            } catch (IOException ex) {
                Toast.makeText(this, "创建照片文件失败", Toast.LENGTH_SHORT).show();
                return;
            }

            if (photoFile != null) {
                currentPhotoUri = FileProvider.getUriForFile(
                        this,
                        getPackageName() + ".fileprovider",
                        photoFile
                );
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri);
                takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                startActivityForResult(takePictureIntent, REQUEST_CODE_TAKE_PHOTO);
            }
        } else {
            Toast.makeText(this, "未找到相机应用", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "需要相机权限才能拍照", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;

        if (requestCode == REQUEST_CODE_TAKE_PHOTO) {
            if (currentPhotoUri != null) {
                uploadAndSendImages(Collections.singletonList(currentPhotoUri));
            }
            return;
        }

        if (data == null) return;

        if (requestCode == REQUEST_CODE_PICK_IMAGES || requestCode == REQUEST_CODE_PICK_FILES) {
            List<Uri> selectedUris = new ArrayList<>();
            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                for (int i = 0; i < count; i++) {
                    Uri uri = data.getClipData().getItemAt(i).getUri();
                    if (uri != null) selectedUris.add(uri);
                }
            } else if (data.getData() != null) {
                selectedUris.add(data.getData());
            }

            if (!selectedUris.isEmpty()) {
                if (requestCode == REQUEST_CODE_PICK_IMAGES) {
                    uploadAndSendImages(selectedUris);
                } else {
                    uploadAndSendFiles(selectedUris);
                }
            }
        }
    }

    private void uploadAndSendImages(List<Uri> uris) {
        String token = PrefUtils.getToken(this);
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "用户未登录", Toast.LENGTH_SHORT).show();
            return;
        }

        List<Uri> validUris = new ArrayList<>();
        for (Uri uri : uris) {
            com.nago8.chat.old.utils.MiscSettingManager.SizeCheckResult check =
                    com.nago8.chat.old.utils.MiscSettingManager.getInstance().checkMediaSize(this, uri, com.nago8.chat.old.utils.MiscSettingManager.MediaType.IMAGE);
            if (!check.isAllowed) {
                Toast.makeText(this, check.errorMessage, Toast.LENGTH_LONG).show();
            } else {
                validUris.add(uri);
            }
        }

        if (validUris.isEmpty()) return;

        Toast.makeText(this, "准备发送 " + validUris.size() + " 张图片...", Toast.LENGTH_SHORT).show();

        repository.uploadAndSendImages(this, token, chatId, chatType, validUris, new MessageRepository.ImageUploadListener() {
            @Override
            public void onProgress(int index, int total) {
            }

            @Override
            public void onImageSuccess(int index, int total) {
                runOnUiThread(() -> {
                    Toast.makeText(ChatActivity.this, "图片 (" + index + "/" + total + ") 发送成功", Toast.LENGTH_SHORT).show();
                    fetchLatestMessage();
                });
            }

            @Override
            public void onImageError(int index, int total, Exception error) {
                runOnUiThread(() -> Toast.makeText(ChatActivity.this, "图片发送失败: " + error.getMessage(), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onAllCompleted() {
            }
        });
    }

    private void uploadAndSendFiles(List<Uri> uris) {
        String token = PrefUtils.getToken(this);
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "用户未登录", Toast.LENGTH_SHORT).show();
            return;
        }

        List<Uri> validUris = new ArrayList<>();
        for (Uri uri : uris) {
            com.nago8.chat.old.utils.MiscSettingManager.MediaType type = com.nago8.chat.old.utils.MiscSettingManager.MediaType.FILE;
            String mimeType = getContentResolver().getType(uri);
            if (mimeType != null && mimeType.startsWith("video/")) {
                type = com.nago8.chat.old.utils.MiscSettingManager.MediaType.VIDEO;
            }
            com.nago8.chat.old.utils.MiscSettingManager.SizeCheckResult check =
                    com.nago8.chat.old.utils.MiscSettingManager.getInstance().checkMediaSize(this, uri, type);
            if (!check.isAllowed) {
                Toast.makeText(this, check.errorMessage, Toast.LENGTH_LONG).show();
            } else {
                validUris.add(uri);
            }
        }

        if (validUris.isEmpty()) return;

        Toast.makeText(this, "准备发送 " + validUris.size() + " 个文件...", Toast.LENGTH_SHORT).show();

        repository.uploadAndSendFiles(this, token, chatId, chatType, validUris, new MessageRepository.FileUploadListener() {
            @Override
            public void onProgress(int index, int total) {
            }

            @Override
            public void onFileSuccess(int index, int total, String fileName) {
                runOnUiThread(() -> {
                    Toast.makeText(ChatActivity.this, "文件 (" + index + "/" + total + ") " + fileName + " 发送成功", Toast.LENGTH_SHORT).show();
                    fetchLatestMessage();
                });
            }

            @Override
            public void onFileError(int index, int total, Exception error) {
                runOnUiThread(() -> Toast.makeText(ChatActivity.this, "文件发送失败: " + error.getMessage(), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onAllCompleted() {
            }
        });
    }

    private void setupComposeInput() {
        chatInputBar = findViewById(R.id.chatInputBar);
        if (chatInputBar != null) {
            chatInputBar.setOnSendClickListener(this::performSend);
            chatInputBar.setOnPanelActionClickListener(actionType -> {
                if ("image".equals(actionType)) {
                    openImagePicker();
                } else if ("camera".equals(actionType)) {
                    openCamera();
                } else if ("file".equals(actionType)) {
                    openFilePicker();
                } else {
                    String actionName;
                    switch (actionType) {
                        case "video":
                            actionName = "视频";
                            break;
                        case "record":
                            actionName = "录制";
                            break;
                        case "card":
                            actionName = "名片";
                            break;
                        case "article":
                            actionName = "文章";
                            break;
                        default:
                            actionName = actionType;
                            break;
                    }
                    Toast.makeText(this, "点击了：" + actionName, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void performSend(String text) {
        if (text == null || text.trim().isEmpty()) return;
        String token = PrefUtils.getToken(this);
        if (chatInputBar != null) chatInputBar.setSendEnabled(false);
        sendCall = repository.sendMessage(token, chatId, chatType, text, new MessageRepository.SendMessageCallback() {
            @Override
            public void onSuccess(send_message response) {
                runOnUiThread(() -> {
                    if (chatInputBar != null) {
                        chatInputBar.clearInput();
                        chatInputBar.setSendEnabled(true);
                    }
                    // 发送成功后依赖 WS 推送自动插入消息到列表
                    // 如果 WS 未推送，做一次增量拉取
                    fetchLatestMessage();
                });
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> {
                    if (chatInputBar != null) chatInputBar.setSendEnabled(true);
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
        if (groupRepository == null) groupRepository = new GroupRepository();

        groupInfoCall = groupRepository.getGroupInfo(token, chatId, new GroupRepository.GroupInfoCallback() {
            @Override
            public void onSuccess(info result) {
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
                        if (ownerId != null && !ownerId.isEmpty()) {
                            adminIds.add(ownerId);
                        }
                        // 标题格式：群名 (人数)
                        String displayName = result.data.name != null && !result.data.name.isEmpty()
                                ? result.data.name : chatName;
                        tvTitle.setText(getString(R.string.group_title_format, displayName, result.data.member));
                        // 刷新消息列表以应用管理员标签
                        refreshMessages(false);
                    }
                });
            }

            @Override
            public void onError(Exception error) {
                // 静默失败，标题保持原样
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
        if (oldest == null || oldest.msg_id == null || oldest.msg_id.isEmpty()) return;

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
        Collections.sort(allMessages, (left, right) -> {
            long leftTime = left == null ? 0 : left.send_time;
            long rightTime = right == null ? 0 : right.send_time;
            return Long.compare(leftTime, rightTime);
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
            group.isOwner = ownerId != null && !ownerId.isEmpty() && ownerId.equals(group.senderId);
            // 群主不显示管理员标签，只显示群主标签
            group.isAdmin = !group.isOwner && group.senderId != null && adminIds.contains(group.senderId);
        }
        adapter.setData(groups);
        tvEmpty.setVisibility(groups.isEmpty() ? View.VISIBLE : View.GONE);
        if (scrollToBottom && !groups.isEmpty()) recyclerView.scrollToPosition(groups.size() - 1);
    }

    private class TopLoadScrollListener extends RecyclerView.OnScrollListener {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
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

    @Override
    public void onBackPressed() {
        if (chatInputBar != null && chatInputBar.isPanelExpanded()) {
            chatInputBar.collapsePanel();
            return;
        }
        super.onBackPressed();
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

package com.nago8.chat.old;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.PopupMenu;
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
    private static final int REQUEST_CODE_PICK_VIDEOS = 2004;
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
        adapter.setChatContext(chatId, chatType, PrefUtils.getToken(this));
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

        adapter.setOnMessageClickListener((anchorView, msg, group) -> showMessageDropMenu(anchorView, msg, group));
        adapter.setOnEditHistoryClickListener(msg -> showEditHistory(msg));

        setupComposeInput();
        // 优先读取在其他界面提前增量保存的消息缓存
        List<Msg> cached = com.nago8.chat.old.cache.ConversationCache.getInstance().getCachedMessages(chatId);
        if (cached != null && !cached.isEmpty()) {
            mergeMessages(cached);
            refreshMessages(true);
        }
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

        // 从全局缓存增量同步在其他界面或后台期间接收到的 WS 消息
        List<Msg> cached = com.nago8.chat.old.cache.ConversationCache.getInstance().getCachedMessages(chatId);
        if (cached != null && !cached.isEmpty()) {
            int added = mergeMessages(cached);
            if (added > 0) {
                refreshMessages(isAtBottom());
            }
        }
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

        // 如果找到已有对应的消息id，直接覆盖原内容
        if (msg.msg_id != null && !msg.msg_id.isEmpty()) {
            int foundIndex = -1;
            for (int i = 0; i < allMessages.size(); i++) {
                Msg existing = allMessages.get(i);
                if (existing != null && msg.msg_id.equals(existing.msg_id)) {
                    foundIndex = i;
                    break;
                }
            }
            if (foundIndex != -1) {
                allMessages.set(foundIndex, msg);
                com.nago8.chat.old.cache.ConversationCache.getInstance().updateCachedMessages(chatId, allMessages);
                refreshMessages(false);
                return;
            }
        }

        allMessages.add(msg);
        com.nago8.chat.old.cache.ConversationCache.getInstance().updateCachedMessages(chatId, allMessages);
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
        startActivityForResult(Intent.createChooser(intent, getString(R.string.chat_pick_image_title)), REQUEST_CODE_PICK_IMAGES);
    }

    @android.annotation.SuppressLint("InlinedApi")
    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(intent, getString(R.string.chat_pick_file_title)), REQUEST_CODE_PICK_FILES);
    }

    @android.annotation.SuppressLint("InlinedApi")
    private void openVideoPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("video/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(intent, getString(R.string.chat_pick_video_title)), REQUEST_CODE_PICK_VIDEOS);
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
                Toast.makeText(this, R.string.chat_photo_file_failed, Toast.LENGTH_SHORT).show();
                return;
            }

            if (photoFile != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    currentPhotoUri = FileProvider.getUriForFile(
                            this,
                            getPackageName() + ".fileprovider",
                            photoFile
                    );
                } else {
                    currentPhotoUri = Uri.fromFile(photoFile);
                }

                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    List<ResolveInfo> resInfoList = getPackageManager().queryIntentActivities(takePictureIntent, PackageManager.MATCH_DEFAULT_ONLY);
                    for (ResolveInfo resolveInfo : resInfoList) {
                        String packageName = resolveInfo.activityInfo.packageName;
                        grantUriPermission(packageName, currentPhotoUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    }
                }

                startActivityForResult(takePictureIntent, REQUEST_CODE_TAKE_PHOTO);
            }
        } else {
            Toast.makeText(this, R.string.chat_no_camera_app, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, R.string.chat_camera_permission_denied, Toast.LENGTH_SHORT).show();
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

        if (requestCode == REQUEST_CODE_PICK_IMAGES || requestCode == REQUEST_CODE_PICK_FILES || requestCode == REQUEST_CODE_PICK_VIDEOS) {
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
                } else if (requestCode == REQUEST_CODE_PICK_VIDEOS) {
                    uploadAndSendVideos(selectedUris);
                } else {
                    uploadAndSendFiles(selectedUris);
                }
            }
        }
    }

    private void uploadAndSendImages(List<Uri> uris) {
        String token = PrefUtils.getToken(this);
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, R.string.chat_not_logged_in, Toast.LENGTH_SHORT).show();
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

        Toast.makeText(this, getString(R.string.chat_sending_images_format, validUris.size()), Toast.LENGTH_SHORT).show();

        repository.uploadAndSendImages(this, token, chatId, chatType, validUris, new MessageRepository.ImageUploadListener() {
            @Override
            public void onProgress(int index, int total) {
            }

            @Override
            public void onImageSuccess(int index, int total) {
                runOnUiThread(() -> {
                    Toast.makeText(ChatActivity.this, getString(R.string.chat_image_sent_format, index, total), Toast.LENGTH_SHORT).show();
                    fetchLatestMessage();
                });
            }

            @Override
            public void onImageError(int index, int total, Exception error) {
                runOnUiThread(() -> Toast.makeText(ChatActivity.this, getString(R.string.chat_image_failed_format, error.getMessage()), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onAllCompleted() {
            }
        });
    }

    private void uploadAndSendFiles(List<Uri> uris) {
        String token = PrefUtils.getToken(this);
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, R.string.chat_not_logged_in, Toast.LENGTH_SHORT).show();
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

        Toast.makeText(this, getString(R.string.chat_sending_files_format, validUris.size()), Toast.LENGTH_SHORT).show();

        repository.uploadAndSendFiles(this, token, chatId, chatType, validUris, new MessageRepository.FileUploadListener() {
            @Override
            public void onProgress(int index, int total) {
            }

            @Override
            public void onFileSuccess(int index, int total, String fileName) {
                runOnUiThread(() -> {
                    Toast.makeText(ChatActivity.this, getString(R.string.chat_file_sent_format, index, total, fileName), Toast.LENGTH_SHORT).show();
                    fetchLatestMessage();
                });
            }

            @Override
            public void onFileError(int index, int total, Exception error) {
                runOnUiThread(() -> Toast.makeText(ChatActivity.this, getString(R.string.chat_file_failed_format, error.getMessage()), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onAllCompleted() {
            }
        });
    }

    private void uploadAndSendVideos(List<Uri> uris) {
        String token = PrefUtils.getToken(this);
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, R.string.chat_not_logged_in, Toast.LENGTH_SHORT).show();
            return;
        }

        List<Uri> validUris = new ArrayList<>();
        for (Uri uri : uris) {
            com.nago8.chat.old.utils.MiscSettingManager.SizeCheckResult check =
                    com.nago8.chat.old.utils.MiscSettingManager.getInstance().checkMediaSize(this, uri, com.nago8.chat.old.utils.MiscSettingManager.MediaType.VIDEO);
            if (!check.isAllowed) {
                Toast.makeText(this, check.errorMessage, Toast.LENGTH_LONG).show();
            } else {
                validUris.add(uri);
            }
        }

        if (validUris.isEmpty()) return;

        Toast.makeText(this, "正在准备发送 " + validUris.size() + " 个视频...", Toast.LENGTH_SHORT).show();

        repository.uploadAndSendVideos(this, token, chatId, chatType, validUris, new MessageRepository.VideoUploadListener() {
            @Override
            public void onProgress(int index, int total) {
            }

            @Override
            public void onVideoSuccess(int index, int total, String fileName) {
                runOnUiThread(() -> {
                    Toast.makeText(ChatActivity.this, "视频发送成功", Toast.LENGTH_SHORT).show();
                    fetchLatestMessage();
                });
            }

            @Override
            public void onVideoError(int index, int total, Exception error) {
                runOnUiThread(() -> Toast.makeText(ChatActivity.this, getString(R.string.chat_video_failed_format, error.getMessage()), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onAllCompleted() {
            }
        });
    }

    private void openArticlePicker() {
        com.nago8.chat.old.fragments.ArticlePickerBottomSheetDialogFragment dialog =
                com.nago8.chat.old.fragments.ArticlePickerBottomSheetDialogFragment.newInstance(chatId, chatType);
        dialog.show(getSupportFragmentManager(), "article_picker");
    }

    private void setupComposeInput() {
        chatInputBar = findViewById(R.id.chatInputBar);
        if (chatInputBar != null) {
            chatInputBar.setOnSendClickListener(this::performSend);
            chatInputBar.setOnQuoteDismissListener(() -> { /* quote cleared by user tapping ✕ */ });
            chatInputBar.setOnPanelActionClickListener(actionType -> {
                if ("image".equals(actionType)) {
                    openImagePicker();
                } else if ("camera".equals(actionType)) {
                    openCamera();
                } else if ("file".equals(actionType)) {
                    openFilePicker();
                } else if ("video".equals(actionType)) {
                    openVideoPicker();
                } else if ("article".equals(actionType)) {
                    openArticlePicker();
                } else {
                    String actionName;
                    switch (actionType) {
                        case "record":
                            actionName = getString(R.string.chat_action_record);
                            break;
                        case "card":
                            actionName = getString(R.string.chat_action_card);
                            break;
                        default:
                            actionName = actionType;
                            break;
                    }
                    Toast.makeText(this, getString(R.string.chat_action_tapped_format, actionName), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void performSend(String text) {
        if (text == null || text.trim().isEmpty()) return;
        String token = PrefUtils.getToken(this);
        if (chatInputBar != null) chatInputBar.setSendEnabled(false);

        // Collect quote data from the preview bar
        com.nago8.chat.old.proto.Msg quoteMsg = chatInputBar != null ? chatInputBar.getPendingQuoteMsg() : null;
        String quoteId = null;
        String quoteText = null;
        if (quoteMsg != null) {
            quoteId = quoteMsg.quote_msg_id != null && !quoteMsg.quote_msg_id.isEmpty()
                    ? quoteMsg.quote_msg_id : quoteMsg.msg_id;
            String senderName = (quoteMsg.sender != null && quoteMsg.sender.name != null && !quoteMsg.sender.name.isEmpty())
                    ? quoteMsg.sender.name : getString(R.string.chat_msg_default);
            String msgContent;
            if (quoteMsg.content != null && quoteMsg.content.text != null && !quoteMsg.content.text.isEmpty()) {
                msgContent = quoteMsg.content.text;
            } else if (quoteMsg.content != null && quoteMsg.content.image_url != null && !quoteMsg.content.image_url.isEmpty()) {
                msgContent = getString(R.string.preview_image);
            } else if (quoteMsg.content != null && quoteMsg.content.video_url != null && !quoteMsg.content.video_url.isEmpty()) {
                msgContent = getString(R.string.preview_video);
            } else if (quoteMsg.content != null && quoteMsg.content.file_name != null && !quoteMsg.content.file_name.isEmpty()) {
                msgContent = quoteMsg.content.file_name;
            } else {
                msgContent = getString(R.string.preview_unknown);
            }
            quoteText = senderName + "：" + msgContent;
        }

        final String finalQuoteId = quoteId;
        final String finalQuoteText = quoteText;
        sendCall = repository.sendMessage(token, chatId, chatType, text, finalQuoteId, finalQuoteText, new MessageRepository.SendMessageCallback() {
            @Override
            public void onSuccess(send_message response) {
                runOnUiThread(() -> {
                    if (chatInputBar != null) {
                        chatInputBar.clearInput();
                        chatInputBar.clearQuote();
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

    public void fetchLatestMessage() {
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
                    mergeMessages(response == null ? null : response.msg);
                    refreshMessages(true);
                });
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvEmpty.setVisibility(View.GONE);
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

        int addedOrUpdated = 0;
        for (Msg msg : messages) {
            if (msg == null || msg.msg_id == null || msg.msg_id.isEmpty()) continue;
            int foundIndex = -1;
            for (int i = 0; i < allMessages.size(); i++) {
                Msg existing = allMessages.get(i);
                if (existing != null && msg.msg_id.equals(existing.msg_id)) {
                    foundIndex = i;
                    break;
                }
            }
            if (foundIndex != -1) {
                allMessages.set(foundIndex, msg);
                addedOrUpdated++;
            } else {
                allMessages.add(msg);
                addedOrUpdated++;
            }
        }
        sortMessagesOldToNew();
        com.nago8.chat.old.cache.ConversationCache.getInstance().updateCachedMessages(chatId, allMessages);
        return addedOrUpdated;
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

    private void showMessageDropMenu(View anchorView, Msg msg, MessageGroup group) {
        if (msg == null) return;
        // 1. 排除已撤回消息
        if (msg.msg_delete_time > 0) return;

        // 2. 排除 Tip / 系统提示消息
        if (msg.content_type == 9 || msg.content_type == 12) return;
        if (msg.content != null && !android.text.TextUtils.isEmpty(msg.content.tip)
                && android.text.TextUtils.isEmpty(msg.content.text)
                && android.text.TextUtils.isEmpty(msg.content.image_url)
                && android.text.TextUtils.isEmpty(msg.content.video_url)
                && android.text.TextUtils.isEmpty(msg.content.file_name)) {
            return;
        }

        List<String> options = new ArrayList<>();
        List<Integer> actions = new ArrayList<>();

        // 获取可复制文本
        final String copyContent = getCopyableContent(msg);
        if (!copyContent.isEmpty()) {
            options.add(getString(R.string.menu_copy));
            actions.add(1);
        }

        // 回复
        options.add(getString(R.string.menu_reply));
        actions.add(2);

        // 撤回 (如果是自己发送的消息，或者具备管理权限)
        String myUserId = PrefUtils.getUserId(this);
        boolean isMine = group != null ? group.mine : (msg.sender != null && myUserId.equals(msg.sender.chat_id));
        boolean isAdminOrOwner = (ownerId != null && ownerId.equals(myUserId)) || adminIds.contains(myUserId);

        if (isMine || isAdminOrOwner) {
            options.add(getString(R.string.menu_recall));
            actions.add(3);
        }

        // 删除
        options.add(getString(R.string.menu_delete));
        actions.add(4);

        // 构建对话框标题（发件人 + 消息摘要）
        String senderName = msg.sender != null && !android.text.TextUtils.isEmpty(msg.sender.name)
                ? msg.sender.name : getString(R.string.chat_msg_default);
        String previewText = !copyContent.isEmpty() ? copyContent : getString(R.string.preview_unknown);
        if (previewText.length() > 20) {
            previewText = previewText.substring(0, 20) + "…";
        }
        String dialogTitle = senderName + ": " + previewText;

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(dialogTitle)
                .setItems(options.toArray(new String[0]), (dialog, which) -> {
                    if (which >= 0 && which < actions.size()) {
                        int action = actions.get(which);
                        switch (action) {
                            case 1:
                                copyToClipboard(copyContent);
                                break;
                            case 2:
                                replyToMessage(msg);
                                break;
                            case 3:
                                recallMsg(msg);
                                break;
                            case 4:
                                deleteMsg(msg);
                                break;
                        }
                    }
                })
                .show();
    }

    private String getCopyableContent(Msg msg) {
        if (msg == null || msg.content == null) return "";
        if (!android.text.TextUtils.isEmpty(msg.content.text)) return msg.content.text;
        if (!android.text.TextUtils.isEmpty(msg.content.file_name)) return msg.content.file_name;
        if (!android.text.TextUtils.isEmpty(msg.content.image_url)) return msg.content.image_url;
        if (!android.text.TextUtils.isEmpty(msg.content.video_url)) return msg.content.video_url;
        return "";
    }

    private void copyToClipboard(String content) {
        if (content == null || content.isEmpty()) return;
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("chat_msg", content));
            Toast.makeText(this, R.string.toast_copied_to_clipboard, Toast.LENGTH_SHORT).show();
        }
    }

    private void replyToMessage(Msg msg) {
        if (msg == null || chatInputBar == null) return;
        chatInputBar.showQuotePreview(msg);
        if (chatInputBar.getEditText() != null) {
            chatInputBar.getEditText().requestFocus();
        }
    }

    private void recallMsg(Msg msg) {
        if (msg == null || msg.msg_id == null || msg.msg_id.isEmpty()) return;
        String token = PrefUtils.getToken(this);
        repository.recallMessage(token, msg.msg_id, chatId, chatType, new MessageRepository.RecallMessageCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(ChatActivity.this, R.string.toast_message_recalled, Toast.LENGTH_SHORT).show();
                    // Find the message by ID (Wire objects may not implement equals())
                    int foundIndex = -1;
                    for (int i = 0; i < allMessages.size(); i++) {
                        Msg m = allMessages.get(i);
                        if (m != null && msg.msg_id.equals(m.msg_id)) {
                            foundIndex = i;
                            break;
                        }
                    }
                    if (foundIndex >= 0) {
                        Msg updatedMsg = allMessages.get(foundIndex).newBuilder()
                                .msg_delete_time(System.currentTimeMillis() / 1000L)
                                .build();
                        allMessages.set(foundIndex, updatedMsg);
                    }
                    refreshMessages(false);
                });
            }

            @Override
            public void onError(String errorMsg) {
                runOnUiThread(() -> Toast.makeText(ChatActivity.this, errorMsg != null ? errorMsg : getString(R.string.toast_recall_failed), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void deleteMsg(Msg msg) {
        if (msg == null) return;
        allMessages.remove(msg);
        refreshMessages(false);
        Toast.makeText(this, R.string.toast_deleted, Toast.LENGTH_SHORT).show();
    }

    private void showEditHistory(Msg msg) {
        if (msg == null || msg.msg_id == null || msg.msg_id.isEmpty()) return;
        String token = PrefUtils.getToken(this);

        // 展示加载中状态
        android.app.ProgressDialog progress = new android.app.ProgressDialog(this);
        progress.setMessage("正在加载编辑历史…");
        progress.setCancelable(false);
        progress.show();

        repository.listMessageEditRecord(token, msg.msg_id, new MessageRepository.EditRecordCallback() {
            @Override
            public void onSuccess(java.util.List<MessageRepository.EditRecord> records) {
                runOnUiThread(() -> {
                    progress.dismiss();
                    if (records == null || records.isEmpty()) {
                        Toast.makeText(ChatActivity.this, "暂无编辑历史", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // 构建列表展示
                    android.widget.ScrollView scrollView = new android.widget.ScrollView(ChatActivity.this);
                    android.widget.LinearLayout listLayout = new android.widget.LinearLayout(ChatActivity.this);
                    listLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
                    listLayout.setPadding(dp(16), dp(8), dp(16), dp(8));

                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());

                    for (int i = 0; i < records.size(); i++) {
                        MessageRepository.EditRecord rec = records.get(i);

                        // 时间标头
                        android.widget.TextView tvTime = new android.widget.TextView(ChatActivity.this);
                        String timeStr = sdf.format(new java.util.Date(rec.msgTime > 0 ? rec.msgTime : rec.createTime));
                        tvTime.setText("第 " + (i + 1) + " 次编辑·" + timeStr);
                        tvTime.setTextSize(12);
                        tvTime.setTextColor(0xFF9E9E9E);
                        android.widget.LinearLayout.LayoutParams timeParams = new android.widget.LinearLayout.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
                        timeParams.topMargin = i == 0 ? 0 : dp(12);
                        tvTime.setLayoutParams(timeParams);
                        listLayout.addView(tvTime);

                        // 旧内容文本
                        String oldText = "";
                        try {
                            org.json.JSONObject contentJson = new org.json.JSONObject(rec.contentOld);
                            oldText = contentJson.optString("text", rec.contentOld);
                        } catch (Exception e) {
                            oldText = rec.contentOld;
                        }

                        android.widget.TextView tvContent = new android.widget.TextView(ChatActivity.this);
                        tvContent.setText(oldText);
                        tvContent.setTextSize(14);
                        tvContent.setTextColor(0xFF212121);
                        android.widget.LinearLayout.LayoutParams contentParams = new android.widget.LinearLayout.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
                        contentParams.topMargin = dp(2);
                        tvContent.setLayoutParams(contentParams);

                        // 内容卡片背景
                        android.graphics.drawable.GradientDrawable cardBg = new android.graphics.drawable.GradientDrawable();
                        cardBg.setCornerRadius(dp(8));
                        cardBg.setColor(0xFFF5F5F5);
                        tvContent.setBackground(cardBg);
                        tvContent.setPadding(dp(10), dp(8), dp(10), dp(8));
                        listLayout.addView(tvContent);
                    }

                    scrollView.addView(listLayout);

                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(ChatActivity.this)
                            .setTitle("编辑历史")
                            .setView(scrollView)
                            .setPositiveButton("关闭", null)
                            .show();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    progress.dismiss();
                    Toast.makeText(ChatActivity.this, "加载失败: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private int dp(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
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

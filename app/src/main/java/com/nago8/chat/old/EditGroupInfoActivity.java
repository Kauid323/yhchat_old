package com.nago8.chat.old;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.AppCompatImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.textfield.TextInputEditText;
import com.nago8.chat.old.net.ApiClient;
import com.nago8.chat.old.proto.group.edit_group;
import com.nago8.chat.old.proto.group.edit_group_send;
import com.nago8.chat.old.proto.group.info;
import com.nago8.chat.old.proto.group.info_send;
import com.nago8.chat.old.utils.ImageUploadUtils;
import com.nago8.chat.old.utils.ImageUtils;
import com.nago8.chat.old.utils.LocaleHelper;
import com.nago8.chat.old.utils.PrefUtils;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class EditGroupInfoActivity extends AppCompatActivity {

    public static final String EXTRA_GROUP_ID = "extra_group_id";
    public static final String EXTRA_GROUP_NAME = "extra_group_name";
    public static final String EXTRA_AVATAR_URL = "extra_avatar_url";

    public static final String RESULT_GROUP_NAME = "result_group_name";
    public static final String RESULT_AVATAR_URL = "result_avatar_url";

    private static final int REQUEST_CODE_PICK_AVATAR = 3001;

    private AppCompatImageView ivGroupAvatar;
    private TextInputEditText etGroupName;
    private TextInputEditText etAvatarUrl;
    private View btnSave;
    private FrameLayout loadingOverlay;
    private TextView tvLoadingText;

    private String groupId;
    private String currentName;
    private String currentAvatarUrl;
    private Uri selectedAvatarUri;

    private info.Group_data currentGroupData;
    private Call runningCall;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_group_info);

        groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);
        currentName = getIntent().getStringExtra(EXTRA_GROUP_NAME);
        currentAvatarUrl = getIntent().getStringExtra(EXTRA_AVATAR_URL);

        if (groupId == null || groupId.isEmpty()) {
            Toast.makeText(this, R.string.group_profile_load_failed, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        fetchGroupInfo();
    }

    private void initViews() {
        AppCompatImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        ivGroupAvatar = findViewById(R.id.ivGroupAvatar);
        etGroupName = findViewById(R.id.etGroupName);
        etAvatarUrl = findViewById(R.id.etAvatarUrl);
        btnSave = findViewById(R.id.btnSave);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        tvLoadingText = findViewById(R.id.tvLoadingText);

        if (!TextUtils.isEmpty(currentName)) {
            etGroupName.setText(currentName);
            etGroupName.setSelection(etGroupName.getText() != null ? etGroupName.getText().length() : 0);
        }
        if (!TextUtils.isEmpty(currentAvatarUrl)) {
            etAvatarUrl.setText(currentAvatarUrl);
            ImageUtils.loadAvatar(this, currentAvatarUrl, ivGroupAvatar);
        }

        findViewById(R.id.layoutAvatarPicker).setOnClickListener(v -> openAvatarPicker());

        btnSave.setOnClickListener(v -> submitSave());
    }

    private void openAvatarPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, getString(R.string.action_choose_image)), REQUEST_CODE_PICK_AVATAR);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_AVATAR && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            selectedAvatarUri = data.getData();
            int radius = (int) (12 * getResources().getDisplayMetrics().density);
            Glide.with(this)
                    .load(selectedAvatarUri)
                    .apply(new RequestOptions().transform(new CenterCrop(), new RoundedCorners(radius)))
                    .into(ivGroupAvatar);
        }
    }

    private void fetchGroupInfo() {
        String token = PrefUtils.getToken(this);
        if (token == null) return;

        info_send requestProto = new info_send.Builder()
                .group_id(groupId)
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

        runningCall = ApiClient.getClient().newCall(request);
        runningCall.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        final info result = info.ADAPTER.decode(response.body().source());
                        if (result != null && result.data != null) {
                            runOnUiThread(() -> {
                                currentGroupData = result.data;
                                if (TextUtils.isEmpty(etGroupName.getText())) {
                                    etGroupName.setText(result.data.name);
                                    if (etGroupName.getText() != null) {
                                        etGroupName.setSelection(etGroupName.getText().length());
                                    }
                                }
                                if (TextUtils.isEmpty(currentAvatarUrl) && !TextUtils.isEmpty(result.data.avatar_url)) {
                                    currentAvatarUrl = result.data.avatar_url;
                                    etAvatarUrl.setText(currentAvatarUrl);
                                    if (selectedAvatarUri == null) {
                                        ImageUtils.loadAvatar(EditGroupInfoActivity.this, currentAvatarUrl, ivGroupAvatar);
                                    }
                                }
                            });
                        }
                    } catch (Exception ignored) {
                    } finally {
                        response.body().close();
                    }
                }
            }
        });
    }

    private void submitSave() {
        String newName = etGroupName.getText() != null ? etGroupName.getText().toString().trim() : "";
        if (TextUtils.isEmpty(newName)) {
            Toast.makeText(this, R.string.edit_group_name_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        String token = PrefUtils.getToken(this);
        if (TextUtils.isEmpty(token)) {
            Toast.makeText(this, R.string.address_book_not_logged_in, Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(getString(R.string.edit_group_saving));

        if (selectedAvatarUri != null) {
            // 先上传所选本地图片到七牛云
            tvLoadingText.setText(R.string.sticker_uploading);
            ImageUploadUtils.getQiniuUploadToken(token, new ImageUploadUtils.TokenCallback() {
                @Override
                public void onSuccess(String uploadToken) {
                    ImageUploadUtils.uploadImage(EditGroupInfoActivity.this, selectedAvatarUri, uploadToken, new ImageUploadUtils.UploadCallback() {
                        @Override
                        public void onSuccess(ImageUploadUtils.QiniuResult result) {
                            String finalAvatarUrl = "https://chat-img.jwznb.com/" + result.key;
                            runOnUiThread(() -> doEditGroup(token, newName, finalAvatarUrl));
                        }

                        @Override
                        public void onError(Exception e) {
                            runOnUiThread(() -> {
                                hideLoading();
                                Toast.makeText(EditGroupInfoActivity.this, getString(R.string.chat_image_upload_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
                            });
                        }
                    });
                }

                @Override
                public void onError(Exception e) {
                    runOnUiThread(() -> {
                        hideLoading();
                        Toast.makeText(EditGroupInfoActivity.this, getString(R.string.report_token_failed_format, e.getMessage()), Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } else {
            String manualAvatarUrl = etAvatarUrl.getText() != null ? etAvatarUrl.getText().toString().trim() : "";
            String finalAvatarUrl = !TextUtils.isEmpty(manualAvatarUrl) ? manualAvatarUrl : currentAvatarUrl;
            doEditGroup(token, newName, finalAvatarUrl);
        }
    }

    private void doEditGroup(String token, String newName, String avatarUrl) {
        tvLoadingText.setText(R.string.edit_group_saving);

        String intro = (currentGroupData != null && currentGroupData.introduction != null) ? currentGroupData.introduction : "";
        int directJoin = (currentGroupData != null) ? currentGroupData.direct_join : 0;
        int historyMsg = (currentGroupData != null) ? currentGroupData.history_msg : 0;
        String categoryName = (currentGroupData != null && currentGroupData.category_name != null) ? currentGroupData.category_name : "";
        long categoryId = (currentGroupData != null) ? currentGroupData.category_id : 0L;
        int privateVal = (currentGroupData != null) ? currentGroupData.private_ : 0;
        long hideMembers = (currentGroupData != null) ? currentGroupData.hide_group_members : 0L;

        edit_group_send requestProto = new edit_group_send.Builder()
                .group_id(groupId)
                .name(newName)
                .avatar_url(avatarUrl != null ? avatarUrl : "")
                .introduction(intro)
                .direct_join(directJoin)
                .history_msg(historyMsg)
                .category_name(categoryName)
                .category_id(categoryId)
                .private_(privateVal)
                .hide_group_members(hideMembers)
                .build();

        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/group/edit-group")
                .header("token", token)
                .post(RequestBody.create(MediaType.parse("application/x-protobuf"), requestProto.encode()))
                .build();

        if (runningCall != null) runningCall.cancel();
        runningCall = ApiClient.getClient().newCall(request);
        runningCall.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    hideLoading();
                    Toast.makeText(EditGroupInfoActivity.this, R.string.group_profile_update_failed, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                boolean success = false;
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        edit_group result = edit_group.ADAPTER.decode(response.body().source());
                        success = (result != null && result.status != null && result.status.code == 1);
                    } catch (Exception ignored) {
                    } finally {
                        response.body().close();
                    }
                }
                final boolean finalSuccess = success;
                runOnUiThread(() -> {
                    hideLoading();
                    if (finalSuccess) {
                        Toast.makeText(EditGroupInfoActivity.this, R.string.edit_group_success, Toast.LENGTH_SHORT).show();
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra(RESULT_GROUP_NAME, newName);
                        resultIntent.putExtra(RESULT_AVATAR_URL, avatarUrl);
                        setResult(Activity.RESULT_OK, resultIntent);
                        finish();
                    } else {
                        Toast.makeText(EditGroupInfoActivity.this, R.string.group_profile_update_failed, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void showLoading(String text) {
        if (loadingOverlay != null) {
            if (tvLoadingText != null && !TextUtils.isEmpty(text)) {
                tvLoadingText.setText(text);
            }
            loadingOverlay.setVisibility(View.VISIBLE);
        }
        if (btnSave != null) {
            btnSave.setEnabled(false);
        }
    }

    private void hideLoading() {
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.GONE);
        }
        if (btnSave != null) {
            btnSave.setEnabled(true);
        }
    }

    @Override
    protected void onDestroy() {
        if (runningCall != null) {
            runningCall.cancel();
        }
        super.onDestroy();
    }
}

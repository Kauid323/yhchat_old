package com.nago8.chat.old;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.AppCompatImageView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.nago8.chat.old.cache.AddressBookCache;
import com.nago8.chat.old.proto.user.Medal_info;
import com.nago8.chat.old.proto.user.ProfileInfo;
import com.nago8.chat.old.proto.user.get_user;
import com.nago8.chat.old.repository.FriendRepository;
import com.nago8.chat.old.repository.ReportRepository;
import com.nago8.chat.old.repository.UserRepository;
import com.nago8.chat.old.utils.ImageUtils;
import com.nago8.chat.old.utils.ImageUploadUtils;
import com.nago8.chat.old.utils.LocaleHelper;
import com.nago8.chat.old.utils.PrefUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;

public class UserProfileActivity extends AppCompatActivity {

    public static final String EXTRA_USER_ID = "user_id";
    private static final int REQUEST_CODE_PICK_REPORT_IMAGE = 2004;

    private Uri reportImageUri = null;
    private ReportRepository reportRepository;
    private ProgressDialog reportProgressDialog;
    private ImageView dialogIvPreview;
    private TextView dialogTvSelectImage;

    private AppCompatImageView ivAvatar;
    private TextView tvName;
    private TextView tvUserId;
    private TextView tvVip;
    private TextView tvRegisterTime;
    private TextView tvOnlineDay;
    private TextView tvContinuousOnline;
    private TextView tvGender;
    private TextView tvBirthday;
    private TextView tvLastActive;
    private TextView tvIntroduction;
    private TextView tvIpGeo;
    private TextView tvMedals;
    private ProgressBar progressBar;
    private UserRepository repository;
    private FriendRepository friendRepository;
    private Call runningCall;

    private FloatingActionButton fabMain;
    private View fabOverlay;
    private LinearLayout layoutSubAddOrChat;
    private LinearLayout layoutSubReport;
    private FloatingActionButton fabAddOrChat;
    private FloatingActionButton fabReport;
    private TextView tvAddOrChatLabel;
    private boolean isFabExpanded = false;

    private String currentUserId;
    private String currentUserName = "";
    private String currentUserAvatar = "";

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);

        String userId = getIntent().getStringExtra(EXTRA_USER_ID);
        currentUserId = userId;

        AppCompatImageButton btnBack = findViewById(R.id.btnBack);
        ivAvatar = findViewById(R.id.ivAvatar);
        if (ivAvatar != null) {
            ivAvatar.setOnClickListener(v -> {
                if (currentUserAvatar != null && currentUserAvatar.length() > 0) {
                    Intent intent = new Intent(this, ImagePreviewActivity.class);
                    intent.putExtra(ImagePreviewActivity.EXTRA_IMAGE_URL, currentUserAvatar);
                    startActivity(intent);
                }
            });
        }
        tvName = findViewById(R.id.tvName);
        tvUserId = findViewById(R.id.tvUserId);
        tvVip = findViewById(R.id.tvVip);
        tvRegisterTime = findViewById(R.id.tvRegisterTime);
        tvOnlineDay = findViewById(R.id.tvOnlineDay);
        tvContinuousOnline = findViewById(R.id.tvContinuousOnline);
        tvGender = findViewById(R.id.tvGender);
        tvBirthday = findViewById(R.id.tvBirthday);
        tvLastActive = findViewById(R.id.tvLastActive);
        tvIntroduction = findViewById(R.id.tvIntroduction);
        tvIpGeo = findViewById(R.id.tvIpGeo);
        tvMedals = findViewById(R.id.tvMedals);
        progressBar = findViewById(R.id.progressBar);

        fabMain = findViewById(R.id.fabMain);
        fabOverlay = findViewById(R.id.fabOverlay);
        layoutSubAddOrChat = findViewById(R.id.layoutSubAddOrChat);
        layoutSubReport = findViewById(R.id.layoutSubReport);
        fabAddOrChat = findViewById(R.id.fabAddOrChat);
        fabReport = findViewById(R.id.fabReport);
        tvAddOrChatLabel = findViewById(R.id.tvAddOrChatLabel);

        if (fabMain != null) {
            fabMain.setOnClickListener(v -> toggleFabMenu());
        }
        if (fabOverlay != null) {
            fabOverlay.setOnClickListener(v -> collapseFabMenu());
        }
        if (fabReport != null) {
            fabReport.setOnClickListener(v -> {
                collapseFabMenu();
                showReportDialog();
            });
        }
        if (layoutSubReport != null) {
            layoutSubReport.setOnClickListener(v -> {
                collapseFabMenu();
                showReportDialog();
            });
        }
        if (fabAddOrChat != null) {
            fabAddOrChat.setOnClickListener(v -> {
                collapseFabMenu();
                boolean isFriend = AddressBookCache.containsUserId(this, currentUserId);
                if (isFriend) {
                    Intent intent = new Intent(this, ChatActivity.class);
                    intent.putExtra(ChatActivity.EXTRA_CHAT_ID, currentUserId);
                    intent.putExtra(ChatActivity.EXTRA_CHAT_TYPE, 1);
                    intent.putExtra(ChatActivity.EXTRA_CHAT_NAME, currentUserName);
                    intent.putExtra(ChatActivity.EXTRA_CHAT_AVATAR, currentUserAvatar);
                    startActivity(intent);
                } else {
                    showAddFriendDialog();
                }
            });
        }

        btnBack.setOnClickListener(v -> onBackPressed());

        repository = new UserRepository();
        fetchUser(userId);
    }

    @Override
    protected void onDestroy() {
        if (runningCall != null) runningCall.cancel();
        super.onDestroy();
    }

    private void fetchUser(String userId) {
        if (userId == null || userId.length() == 0) {
            Toast.makeText(this, R.string.user_profile_load_failed, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        String token = PrefUtils.getToken(this);
        runningCall = repository.getUser(token, userId, new UserRepository.GetUserCallback() {
            @Override
            public void onSuccess(get_user response) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    if (response == null || response.data == null) {
                        Toast.makeText(UserProfileActivity.this, R.string.user_profile_load_failed, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    bindUser(response.data);
                });
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(UserProfileActivity.this, R.string.user_profile_load_failed, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void bindUser(get_user.Data data) {
        if (data == null) return;
        currentUserName = (data.name != null && !"未知用户".equals(data.name) && !"Unknown user".equals(data.name)) ? data.name : "";
        currentUserAvatar = data.avatar_url != null ? data.avatar_url : "";

        tvName.setText(currentUserName);
        tvUserId.setText(getString(R.string.user_id_format, data.id));
        ImageUtils.loadAvatar(this, currentUserAvatar, ivAvatar);

        if (data.is_vip == 1) {
            tvVip.setVisibility(View.VISIBLE);
            tvVip.setText(R.string.user_profile_vip);
        } else {
            tvVip.setVisibility(View.GONE);
        }

        tvRegisterTime.setText(getString(R.string.user_profile_register_time, data.register_time != null ? data.register_time : ""));
        tvOnlineDay.setText(getString(R.string.user_profile_online_day, data.online_day));
        tvContinuousOnline.setText(getString(R.string.user_profile_continuous_online, data.continuous_online_day));

        ProfileInfo profile = data.profile_info;
        if (profile != null) {
            int genderRes;
            switch (profile.gender) {
                case 1: genderRes = R.string.user_profile_gender_male; break;
                case 2: genderRes = R.string.user_profile_gender_female; break;
                case 3: genderRes = R.string.user_profile_gender_other; break;
                default: genderRes = R.string.user_profile_gender_unknown; break;
            }
            tvGender.setText(getString(R.string.user_profile_gender, getString(genderRes)));

            if (profile.birthday > 0) {
                String birthdayStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(profile.birthday * 1000L));
                tvBirthday.setText(getString(R.string.user_profile_birthday, birthdayStr));
            } else {
                tvBirthday.setText(getString(R.string.user_profile_birthday, getString(R.string.user_profile_gender_unknown)));
            }

            String lastActive = profile.last_active_time != null ? profile.last_active_time : "";
            tvLastActive.setText(getString(R.string.user_profile_last_active, lastActive));

            String intro = profile.introduction != null && profile.introduction.length() > 0 ? profile.introduction : "";
            tvIntroduction.setText(getString(R.string.user_profile_introduction, intro));
        } else {
            tvGender.setText(getString(R.string.user_profile_gender, getString(R.string.user_profile_gender_unknown)));
            tvBirthday.setText(getString(R.string.user_profile_birthday, getString(R.string.user_profile_gender_unknown)));
            tvLastActive.setText(getString(R.string.user_profile_last_active, ""));
            tvIntroduction.setText(getString(R.string.user_profile_introduction, ""));
        }

        tvIpGeo.setText(getString(R.string.user_profile_ip_geo, data.ipGeo != null ? data.ipGeo : ""));

        List<Medal_info> medals = data.yh_user_medal;
        if (medals != null && !medals.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < medals.size(); i++) {
                Medal_info m = medals.get(i);
                if (m.name != null && m.name.length() > 0) {
                    if (sb.length() > 0) sb.append("、");
                    sb.append(m.name);
                }
            }
            tvMedals.setText(getString(R.string.user_profile_medals, sb.length() > 0 ? sb.toString() : getString(R.string.user_profile_no_medal)));
        } else {
            tvMedals.setText(getString(R.string.user_profile_medals, getString(R.string.user_profile_no_medal)));
        }
    }

    private void updateFabSubItemState() {
        if (currentUserId == null || currentUserId.isEmpty()) return;
        boolean isFriend = AddressBookCache.containsUserId(this, currentUserId);
        if (tvAddOrChatLabel != null) {
            tvAddOrChatLabel.setText(isFriend ? R.string.action_enter_chat : R.string.action_add_friend);
        }
        if (fabAddOrChat != null) {
            fabAddOrChat.setImageResource(isFriend ? R.drawable.ic_chat : R.drawable.ic_add);
        }
    }

    private void toggleFabMenu() {
        if (isFabExpanded) {
            collapseFabMenu();
        } else {
            expandFabMenu();
        }
    }

    private void expandFabMenu() {
        updateFabSubItemState();
        isFabExpanded = true;
        if (fabMain != null) fabMain.animate().rotation(45f).setDuration(200).start();
        if (fabOverlay != null) fabOverlay.setVisibility(View.VISIBLE);
        if (layoutSubReport != null) {
            layoutSubReport.setVisibility(View.VISIBLE);
            layoutSubReport.setAlpha(0f);
            layoutSubReport.setTranslationY(20f);
            layoutSubReport.animate().alpha(1f).translationY(0f).setDuration(200).start();
        }
        if (layoutSubAddOrChat != null) {
            layoutSubAddOrChat.setVisibility(View.VISIBLE);
            layoutSubAddOrChat.setAlpha(0f);
            layoutSubAddOrChat.setTranslationY(20f);
            layoutSubAddOrChat.animate().alpha(1f).translationY(0f).setDuration(200).start();
        }
    }

    private void collapseFabMenu() {
        if (!isFabExpanded) return;
        isFabExpanded = false;
        if (fabMain != null) fabMain.animate().rotation(0f).setDuration(200).start();
        if (fabOverlay != null) fabOverlay.setVisibility(View.GONE);
        if (layoutSubReport != null) {
            layoutSubReport.animate().alpha(0f).translationY(20f).setDuration(150).withEndAction(() -> layoutSubReport.setVisibility(View.GONE)).start();
        }
        if (layoutSubAddOrChat != null) {
            layoutSubAddOrChat.animate().alpha(0f).translationY(20f).setDuration(150).withEndAction(() -> layoutSubAddOrChat.setVisibility(View.GONE)).start();
        }
    }

    private void showAddFriendDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder =
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);
        builder.setTitle(R.string.add_friend_dialog_title);

        com.google.android.material.textfield.TextInputLayout inputLayout =
                new com.google.android.material.textfield.TextInputLayout(this, null, com.google.android.material.R.attr.textInputStyle);
        inputLayout.setHint(getString(R.string.add_friend_remark_hint));

        final com.google.android.material.textfield.TextInputEditText etRemark =
                new com.google.android.material.textfield.TextInputEditText(inputLayout.getContext());
        etRemark.setSingleLine(true);
        inputLayout.addView(etRemark);

        FrameLayout container = new FrameLayout(this);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding / 2, padding, padding / 2);
        container.addView(inputLayout);
        builder.setView(container);

        builder.setPositiveButton(R.string.dialog_confirm, (dialog, which) -> {
            String remark = etRemark.getText() != null ? etRemark.getText().toString().trim() : "";
            sendFriendApply(remark);
        });
        builder.setNegativeButton(R.string.dialog_cancel, (dialog, which) -> dialog.dismiss());

        builder.show();
    }

    private void sendFriendApply(String remark) {
        String token = PrefUtils.getToken(this);
        if (token == null || token.isEmpty()) return;

        if (friendRepository == null) {
            friendRepository = new FriendRepository();
        }

        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        friendRepository.applyFriend(token, currentUserId, 1, remark, new FriendRepository.ApplyFriendCallback() {
            @Override
            public void onSuccess(int code, String msg) {
                runOnUiThread(() -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    if (code == 1) {
                        Toast.makeText(UserProfileActivity.this, R.string.add_friend_sent, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(UserProfileActivity.this, msg != null && !msg.isEmpty() ? msg : getString(R.string.add_friend_failed), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    Toast.makeText(UserProfileActivity.this, getString(R.string.add_friend_failed_format, error.getMessage()), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showReportDialog() {
        reportImageUri = null;
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder =
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);
        builder.setTitle(R.string.report_user_dialog_title);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        root.setPadding(padding, padding / 2, padding, padding / 2);

        // 1. 举报原因分类选择器
        TextView tvReasonTitle = new TextView(this);
        tvReasonTitle.setText(R.string.report_type_label);
        tvReasonTitle.setTextSize(14);
        tvReasonTitle.setTextColor(0xFF888888);
        root.addView(tvReasonTitle);

        final String[] reasons = getResources().getStringArray(R.array.report_reasons);
        Spinner spinnerReason = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, reasons);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerReason.setAdapter(adapter);
        spinnerReason.setPadding(0, (int) (4 * getResources().getDisplayMetrics().density), 0, (int) (12 * getResources().getDisplayMetrics().density));
        root.addView(spinnerReason);

        // 2. MD2 举报详细内容输入框
        com.google.android.material.textfield.TextInputLayout inputLayoutContent =
                new com.google.android.material.textfield.TextInputLayout(this, null, com.google.android.material.R.attr.textInputStyle);
        inputLayoutContent.setHint(getString(R.string.report_content_label));

        final com.google.android.material.textfield.TextInputEditText etContent =
                new com.google.android.material.textfield.TextInputEditText(inputLayoutContent.getContext());
        etContent.setHint(R.string.report_content_hint);
        etContent.setMinLines(3);
        etContent.setGravity(Gravity.TOP | Gravity.START);
        inputLayoutContent.addView(etContent);
        
        LinearLayout.LayoutParams paramsInput = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        paramsInput.topMargin = (int) (8 * getResources().getDisplayMetrics().density);
        paramsInput.bottomMargin = (int) (12 * getResources().getDisplayMetrics().density);
        inputLayoutContent.setLayoutParams(paramsInput);
        root.addView(inputLayoutContent);

        // 3. 图片证据选择器
        TextView tvImageTitle = new TextView(this);
        tvImageTitle.setText(R.string.report_image_label);
        tvImageTitle.setTextSize(14);
        tvImageTitle.setTextColor(0xFF888888);
        root.addView(tvImageTitle);

        LinearLayout imageRow = new LinearLayout(this);
        imageRow.setOrientation(LinearLayout.HORIZONTAL);
        imageRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams paramsImageRow = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        paramsImageRow.topMargin = (int) (6 * getResources().getDisplayMetrics().density);
        imageRow.setLayoutParams(paramsImageRow);

        dialogIvPreview = new ImageView(this);
        int imgSize = (int) (60 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(imgSize, imgSize);
        imgParams.rightMargin = (int) (12 * getResources().getDisplayMetrics().density);
        dialogIvPreview.setLayoutParams(imgParams);
        dialogIvPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        dialogIvPreview.setVisibility(View.GONE);
        imageRow.addView(dialogIvPreview);

        com.google.android.material.button.MaterialButton btnSelectImg =
                new com.google.android.material.button.MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle);
        btnSelectImg.setText(R.string.report_select_image);
        btnSelectImg.setIconResource(R.drawable.ic_image);
        btnSelectImg.setOnClickListener(v -> openImagePickerForReport());
        imageRow.addView(btnSelectImg);

        root.addView(imageRow);

        builder.setView(root);

        builder.setPositiveButton(R.string.report_btn_submit, (dialog, which) -> {
            int selectedPos = spinnerReason.getSelectedItemPosition();
            String reason = (selectedPos >= 0 && selectedPos < reasons.length) ? reasons[selectedPos] : reasons[reasons.length - 1];
            String content = etContent.getText() != null ? etContent.getText().toString().trim() : "";
            doSubmitReport(reason, content);
        });

        builder.setNegativeButton(R.string.report_btn_cancel, (dialog, which) -> dialog.dismiss());
        com.nago8.chat.old.utils.ThemeUtils.showThemedDialog(builder);
    }

    private void openImagePickerForReport() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, getString(R.string.report_pick_image_title)), REQUEST_CODE_PICK_REPORT_IMAGE);
    }

    private void updateReportDialogImagePreview() {
        if (dialogIvPreview != null && reportImageUri != null) {
            dialogIvPreview.setImageURI(reportImageUri);
            dialogIvPreview.setVisibility(View.VISIBLE);
            if (dialogTvSelectImage != null) dialogTvSelectImage.setText(R.string.report_change_image);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_REPORT_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            reportImageUri = data.getData();
            updateReportDialogImagePreview();
        }
    }

    private void doSubmitReport(String reason, String content) {
        String token = PrefUtils.getToken(this);
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, R.string.user_profile_not_logged_in, Toast.LENGTH_SHORT).show();
            return;
        }

        if (reportProgressDialog == null) {
            reportProgressDialog = new ProgressDialog(this);
            reportProgressDialog.setCancelable(false);
        }
        reportProgressDialog.setMessage(getString(R.string.report_submitting));
        reportProgressDialog.show();

        if (reportImageUri != null) {
            reportProgressDialog.setMessage(getString(R.string.report_uploading_image));
            ImageUploadUtils.getQiniuUploadToken(token, new ImageUploadUtils.TokenCallback() {
                @Override
                public void onSuccess(String uploadToken) {
                    ImageUploadUtils.uploadImage(UserProfileActivity.this, reportImageUri, uploadToken, new ImageUploadUtils.UploadCallback() {
                        @Override
                        public void onSuccess(ImageUploadUtils.QiniuResult result) {
                            String imageUrl = "https://chat-img.jwznb.com/" + result.key;
                            sendReportRequest(token, reason, content, imageUrl);
                        }

                        @Override
                        public void onError(Exception e) {
                            runOnUiThread(() -> {
                                if (reportProgressDialog != null) reportProgressDialog.dismiss();
                                Toast.makeText(UserProfileActivity.this, getString(R.string.report_image_upload_failed_format, e.getMessage()), Toast.LENGTH_SHORT).show();
                            });
                        }
                    });
                }

                @Override
                public void onError(Exception e) {
                    runOnUiThread(() -> {
                        if (reportProgressDialog != null) reportProgressDialog.dismiss();
                        Toast.makeText(UserProfileActivity.this, getString(R.string.report_token_failed_format, e.getMessage()), Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } else {
            sendReportRequest(token, reason, content, "");
        }
    }

    private void sendReportRequest(String token, String reason, String content, String imageUrl) {
        if (reportRepository == null) reportRepository = new ReportRepository();
        reportRepository.submitReport(token, currentUserId, 1, currentUserName, content, imageUrl, reason, new ReportRepository.ReportCallback() {
            @Override
            public void onSuccess(int code, String msg) {
                runOnUiThread(() -> {
                    if (reportProgressDialog != null) reportProgressDialog.dismiss();
                    if (code == 1) {
                        Toast.makeText(UserProfileActivity.this, R.string.report_submitted, Toast.LENGTH_SHORT).show();
                        reportImageUri = null;
                    } else {
                        Toast.makeText(UserProfileActivity.this, msg != null && !msg.isEmpty() ? msg : getString(R.string.report_submit_failed), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> {
                    if (reportProgressDialog != null) reportProgressDialog.dismiss();
                    Toast.makeText(UserProfileActivity.this, getString(R.string.report_submit_failed_format, error.getMessage()), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (isFabExpanded) {
            collapseFabMenu();
        } else {
            super.onBackPressed();
        }
    }
}

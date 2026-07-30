package com.nago8.chat.old;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.AppCompatImageView;

import com.nago8.chat.old.proto.user.Medal_info;
import com.nago8.chat.old.proto.user.ProfileInfo;

import com.nago8.chat.old.repository.UserRepository;
import com.nago8.chat.old.proto.user.get_user;
import com.nago8.chat.old.utils.ImageUtils;
import com.nago8.chat.old.utils.LocaleHelper;
import com.nago8.chat.old.utils.PrefUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;

import android.content.Intent;
import android.widget.LinearLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.nago8.chat.old.cache.AddressBookCache;

public class UserProfileActivity extends AppCompatActivity {

    public static final String EXTRA_USER_ID = "user_id";

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
                Toast.makeText(this, R.string.report_submitted, Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(this, R.string.friend_request_sent, Toast.LENGTH_SHORT).show();
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
        currentUserName = data.name != null && data.name.length() > 0 ? data.name : getString(R.string.unknown_user);
        currentUserAvatar = data.avatar_url != null ? data.avatar_url : "";

        tvName.setText(currentUserName);
        tvUserId.setText("ID: " + data.id);
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

    @Override
    public void onBackPressed() {
        if (isFabExpanded) {
            collapseFabMenu();
        } else {
            super.onBackPressed();
        }
    }
}

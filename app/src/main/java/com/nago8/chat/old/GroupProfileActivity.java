package com.nago8.chat.old;

import android.content.Context;
import android.content.Intent;
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
import androidx.appcompat.widget.SwitchCompat;

import com.nago8.chat.old.net.ApiClient;
import com.nago8.chat.old.proto.group.info;
import com.nago8.chat.old.proto.group.info_send;
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

public class GroupProfileActivity extends AppCompatActivity {

    public static final String EXTRA_GROUP_ID = "group_id";

    private AppCompatImageView ivAvatar;
    private TextView tvName;
    private TextView tvGroupId;
    private TextView tvIntroduction;
    private TextView tvMemberCount;
    private TextView tvCreateBy;
    private TextView tvCategory;
    private TextView tvGroupCode;
    private TextView tvMyNickname;
    private TextView tvCommunity;
    private View rowCommunity;
    private View rowBanReason;
    private TextView tvBanReason;
    private SwitchCompat swDoNotDisturb;
    private SwitchCompat swTop;
    private ProgressBar progressBar;
    private Call runningCall;

    private String groupId;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_profile);

        groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);

        AppCompatImageButton btnBack = findViewById(R.id.btnBack);
        ivAvatar = findViewById(R.id.ivAvatar);
        tvName = findViewById(R.id.tvName);
        tvGroupId = findViewById(R.id.tvGroupId);
        tvIntroduction = findViewById(R.id.tvIntroduction);
        tvMemberCount = findViewById(R.id.tvMemberCount);
        tvCreateBy = findViewById(R.id.tvCreateBy);
        tvCategory = findViewById(R.id.tvCategory);
        tvGroupCode = findViewById(R.id.tvGroupCode);
        tvMyNickname = findViewById(R.id.tvMyNickname);
        tvCommunity = findViewById(R.id.tvCommunity);
        rowCommunity = findViewById(R.id.rowCommunity);
        rowBanReason = findViewById(R.id.rowBanReason);
        tvBanReason = findViewById(R.id.tvBanReason);
        swDoNotDisturb = findViewById(R.id.swDoNotDisturb);
        swTop = findViewById(R.id.swTop);
        progressBar = findViewById(R.id.progressBar);

        btnBack.setOnClickListener(v -> onBackPressed());

        // 群成员点击进入成员列表
        findViewById(R.id.rowMembers).setOnClickListener(v -> {
            if (groupId != null && groupId.length() > 0) {
                Intent intent = new Intent(this, GroupMembersActivity.class);
                intent.putExtra(GroupMembersActivity.EXTRA_GROUP_ID, groupId);
                startActivity(intent);
            }
        });

        fetchGroupInfo(groupId);
    }

    @Override
    protected void onDestroy() {
        if (runningCall != null) runningCall.cancel();
        super.onDestroy();
    }

    private void fetchGroupInfo(String groupId) {
        if (groupId == null || groupId.length() == 0) {
            Toast.makeText(this, R.string.group_profile_load_failed, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String token = PrefUtils.getToken(this);
        if (token == null) {
            finish();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

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
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(GroupProfileActivity.this, R.string.group_profile_load_failed, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        final info result = info.ADAPTER.decode(response.body().source());
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            if (result == null || result.data == null) {
                                Toast.makeText(GroupProfileActivity.this, R.string.group_profile_load_failed, Toast.LENGTH_SHORT).show();
                                return;
                            }
                            bindGroup(result.data);
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(GroupProfileActivity.this, R.string.group_profile_load_failed, Toast.LENGTH_SHORT).show();
                        });
                    }
                } else {
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(GroupProfileActivity.this, R.string.group_profile_load_failed, Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private void bindGroup(info.Group_data data) {
        tvName.setText(data.name != null && data.name.length() > 0 ? data.name : getString(R.string.unknown_user));
        tvGroupId.setText("ID: " + data.group_id);
        ImageUtils.loadAvatar(this, data.avatar_url, ivAvatar);

        String intro = data.introduction != null && data.introduction.length() > 0 ? data.introduction : getString(R.string.group_profile_no_ban);
        tvIntroduction.setText(intro);

        tvMemberCount.setText(getString(R.string.group_profile_members_format, data.member));

        String createBy = data.create_by != null && data.create_by.length() > 0 ? data.create_by : "";
        tvCreateBy.setText(createBy);

        String category = data.category_name != null && data.category_name.length() > 0 ? data.category_name : "";
        tvCategory.setText(category);

        // 群号码
        if (data.group_code != null && data.group_code.length() > 0) {
            tvGroupCode.setText(data.group_code);
        }

        // 我的群昵称
        if (data.my_group_nickname != null && data.my_group_nickname.length() > 0) {
            tvMyNickname.setText(data.my_group_nickname);
        }

        // 关联分区
        if (data.community_name != null && data.community_name.length() > 0) {
            tvCommunity.setText(data.community_name);
            rowCommunity.setVisibility(View.VISIBLE);
        } else {
            rowCommunity.setVisibility(View.GONE);
        }

        // 禁言原因
        if (data.ban_reason != null && data.ban_reason.length() > 0) {
            tvBanReason.setText(data.ban_reason);
            rowBanReason.setVisibility(View.VISIBLE);
        } else {
            rowBanReason.setVisibility(View.GONE);
        }

        // 开关项
        swDoNotDisturb.setChecked(data.do_not_disturb == 1);
        swTop.setChecked(data.top == 1);
    }
}

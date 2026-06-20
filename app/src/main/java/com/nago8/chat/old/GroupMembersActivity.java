package com.nago8.chat.old;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nago8.chat.old.proto.group.User;
import com.nago8.chat.old.proto.group.list_member;
import com.nago8.chat.old.proto.group.list_member_send;
import com.nago8.chat.old.net.ApiClient;
import com.nago8.chat.old.utils.ImageUtils;
import com.nago8.chat.old.utils.LocaleHelper;
import com.nago8.chat.old.utils.PrefUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GroupMembersActivity extends AppCompatActivity {

    public static final String EXTRA_GROUP_ID = "group_id";

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
 private MemberAdapter adapter;
    private Call runningCall;
    private String groupId;
    private String ownerId;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_members);

        groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);

        AppCompatImageButton btnBack = findViewById(R.id.btnBack);
        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);

        btnBack.setOnClickListener(v -> onBackPressed());

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MemberAdapter();
        recyclerView.setAdapter(adapter);

        fetchMembers();
    }

    @Override
    protected void onDestroy() {
        if (runningCall != null) runningCall.cancel();
        super.onDestroy();
    }

    private void fetchMembers() {
        if (groupId == null || groupId.length() == 0) {
            finish();
            return;
        }

        String token = PrefUtils.getToken(this);
        if (token == null) {
            finish();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        list_member_send requestProto = new list_member_send.Builder()
                .group_id(groupId)
                .data(new list_member_send.Data(200, 1))
                .keywords("")
                .build();

        RequestBody body = RequestBody.create(
                MediaType.parse("application/x-protobuf"),
                requestProto.encode()
        );

        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/group/list-member")
                .header("token", token)
                .post(body)
                .build();

        runningCall = ApiClient.getClient().newCall(request);
        runningCall.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(GroupMembersActivity.this, R.string.group_members_load_failed, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        final list_member result = list_member.ADAPTER.decode(response.body().source());
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            if (result != null && result.user != null) {
                                adapter.setData(result.user);
                            }
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(GroupMembersActivity.this, R.string.group_members_load_failed, Toast.LENGTH_SHORT).show();
                        });
                    }
                } else {
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(GroupMembersActivity.this, R.string.group_members_load_failed, Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private class MemberAdapter extends RecyclerView.Adapter<MemberAdapter.MemberViewHolder> {

        private final List<User> members = new ArrayList<>();

        void setData(List<User> data) {
            members.clear();
            if (data != null) members.addAll(data);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public MemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_group_member, parent, false);
            return new MemberViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull MemberViewHolder holder, int position) {
            User member = members.get(position);
            String name = "";
            String avatarUrl = "";
            String userId = "";

            if (member.user_info != null) {
                name = member.user_info.name != null ? member.user_info.name : "";
                avatarUrl = member.user_info.avatar_url != null ? member.user_info.avatar_url : "";
                userId = member.user_info.user_id != null ? member.user_info.user_id : "";
            }

            holder.tvName.setText(name);
            ImageUtils.loadAvatar(holder.itemView.getContext(), avatarUrl, holder.ivAvatar);

            // 权限标签
            String tag = "";
            if (member.permission_level == 2) {
                tag = getString(R.string.group_member_owner);
            } else if (member.permission_level == 1) {
                tag = getString(R.string.group_member_admin);
            }
            if (member.is_gag == 1) {
                tag = tag.length() > 0 ? tag + " · " + getString(R.string.group_member_gagged) : getString(R.string.group_member_gagged);
            }
            if (tag.length() > 0) {
                holder.tvTag.setText(tag);
                holder.tvTag.setVisibility(View.VISIBLE);
            } else {
                holder.tvTag.setVisibility(View.GONE);
            }

            final String finalUserId = userId;
            holder.itemView.setOnClickListener(v -> {
                if (finalUserId.length() > 0) {
                    Intent intent = new Intent(v.getContext(), UserProfileActivity.class);
                    intent.putExtra(UserProfileActivity.EXTRA_USER_ID, finalUserId);
                    v.getContext().startActivity(intent);
                }
            });
        }

        @Override
        public int getItemCount() {
            return members.size();
        }

        class MemberViewHolder extends RecyclerView.ViewHolder {
            AppCompatImageView ivAvatar;
            TextView tvName;
            TextView tvTag;

            MemberViewHolder(@NonNull View itemView) {
                super(itemView);
                ivAvatar = itemView.findViewById(R.id.ivAvatar);
                tvName = itemView.findViewById(R.id.tvName);
                tvTag = itemView.findViewById(R.id.tvTag);
            }
        }
    }
}

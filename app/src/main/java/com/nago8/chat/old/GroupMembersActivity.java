package com.nago8.chat.old;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nago8.chat.old.net.ApiClient;
import com.nago8.chat.old.proto.group.User;
import com.nago8.chat.old.proto.group.info;
import com.nago8.chat.old.proto.group.info_send;
import com.nago8.chat.old.proto.group.list_member;
import com.nago8.chat.old.proto.group.list_member_send;
import com.nago8.chat.old.repository.GroupRepository;
import com.nago8.chat.old.utils.ImageUtils;
import com.nago8.chat.old.utils.LocaleHelper;
import com.nago8.chat.old.utils.PrefUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GroupMembersActivity extends AppCompatActivity {

    private static final String TAG = "GroupMembersActivity";
    public static final String EXTRA_GROUP_ID = "group_id";

    private ProgressBar progressBar;
    private MemberAdapter adapter;
    private Call runningCall;
    private Call infoCall;
    private String groupId;
    private String ownerId;
    private final Set<String> adminIds = new HashSet<>();
    private GroupRepository groupRepository;

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
        groupRepository = new GroupRepository();

        AppCompatImageButton btnBack = findViewById(R.id.btnBack);
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);

        btnBack.setOnClickListener(v -> onBackPressed());

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MemberAdapter();
        recyclerView.setAdapter(adapter);

        fetchGroupRoleInfo();
        fetchMembers();
    }

    @Override
    protected void onDestroy() {
        if (runningCall != null) runningCall.cancel();
        if (infoCall != null) infoCall.cancel();
        super.onDestroy();
    }

    private void fetchGroupRoleInfo() {
        if (groupId == null || groupId.isEmpty()) return;

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

        infoCall = ApiClient.getClient().newCall(request);
        infoCall.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "fetchGroupRoleInfo failed", e);
            }

            @Override
            @SuppressLint("NotifyDataSetChanged")
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (!response.isSuccessful() || response.body() == null) return;
                try {
                    final info result = info.ADAPTER.decode(response.body().source());
                    if (result == null || result.data == null) return;
                    ownerId = result.data.owner != null ? result.data.owner : "";
                    adminIds.clear();
                    if (result.data.admin != null) adminIds.addAll(result.data.admin);
                    runOnUiThread(() -> adapter.notifyDataSetChanged());
                } catch (Exception e) {
                    Log.e(TAG, "fetchGroupRoleInfo decode error", e);
                } finally {
                    response.body().close();
                }
            }
        });
    }

    private void fetchMembers() {
        if (groupId == null || groupId.isEmpty()) {
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
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(GroupMembersActivity.this, R.string.group_members_load_failed, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
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
                        Log.e(TAG, "fetchMembers parse error", e);
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(GroupMembersActivity.this, R.string.group_members_load_failed, Toast.LENGTH_SHORT).show();
                        });
                    } finally {
                        response.body().close();
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

        @SuppressLint("NotifyDataSetChanged")
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

            boolean isOwner = !userId.isEmpty() && ownerId != null && !ownerId.isEmpty() && ownerId.equals(userId);
            boolean isAdmin = !isOwner && !userId.isEmpty() && adminIds.contains(userId);
            if (!isOwner && !isAdmin) {
                if (member.permission_level == 2) {
                    isOwner = true;
                } else if (member.permission_level == 1) {
                    isAdmin = true;
                }
            }

            // 权限标签
            String tag = "";
            if (isOwner) {
                tag = getString(R.string.group_member_owner);
            } else if (isAdmin) {
                tag = getString(R.string.group_member_admin);
            }
            if (member.is_gag == 1) {
                tag = !tag.isEmpty() ? tag + " · " + getString(R.string.group_member_gagged) : getString(R.string.group_member_gagged);
            }
            if (!tag.isEmpty()) {
                holder.tvTag.setText(tag);
                holder.tvTag.setVisibility(View.VISIBLE);
            } else {
                holder.tvTag.setVisibility(View.GONE);
            }

            String currentUserId = PrefUtils.getUserId(GroupMembersActivity.this);
            boolean isMyselfOwner = ownerId != null && ownerId.equals(currentUserId);
            boolean isMyselfAdmin = adminIds.contains(currentUserId);
            boolean isMyselfManager = isMyselfOwner || isMyselfAdmin;

            // 规则：不在 群主/管理员 列表的话，都不显示；如果是群主本身(Target是群主)，也不显示 more 菜单
            if (!isMyselfManager || isOwner) {
                holder.ibMore.setVisibility(View.GONE);
            } else {
                holder.ibMore.setVisibility(View.VISIBLE);
            }

            final String finalUserId = userId;
            final String finalName = name;
            final boolean finalIsAdmin = isAdmin;
            final boolean finalIsGag = (member.is_gag == 1);
            final int itemPos = position;

            holder.ibMore.setOnClickListener(v -> showMemberActionMenu(v, finalUserId, finalName, finalIsAdmin, finalIsGag, isMyselfOwner, itemPos));

            holder.itemView.setOnClickListener(v -> {
                if (!finalUserId.isEmpty()) {
                    Intent intent = new Intent(v.getContext(), UserProfileActivity.class);
                    intent.putExtra(UserProfileActivity.EXTRA_USER_ID, finalUserId);
                    v.getContext().startActivity(intent);
                }
            });
        }

        private void showMemberActionMenu(View anchor, String targetUserId, String targetName, boolean isTargetAdmin, boolean isTargetGagged, boolean isMyselfOwner, int position) {
            PopupMenu popup = new PopupMenu(GroupMembersActivity.this, anchor);
            popup.getMenu().add(0, 1, 0, "踢出成员");
            popup.getMenu().add(0, 2, 0, isTargetGagged ? "取消禁言" : "禁言成员");

            if (isMyselfOwner) {
                popup.getMenu().add(0, 3, 0, isTargetAdmin ? "移除管理员" : "设置管理员");
            }

            popup.setOnMenuItemClickListener(item -> {
                String token = PrefUtils.getToken(GroupMembersActivity.this);
                if (token == null || token.isEmpty()) return true;

                switch (item.getItemId()) {
                    case 1:
                        // 踢出成员
                        groupRepository.removeMember(token, groupId, targetUserId, new GroupRepository.GroupActionCallback() {
                            @Override
                            public void onSuccess(int code, String msg) {
                                runOnUiThread(() -> {
                                    if (code == 1) {
                                        Toast.makeText(GroupMembersActivity.this, "已踢出成员: " + targetName, Toast.LENGTH_SHORT).show();
                                        if (position >= 0 && position < members.size()) {
                                            members.remove(position);
                                            notifyItemRemoved(position);
                                        }
                                    } else {
                                        Toast.makeText(GroupMembersActivity.this, msg != null && !msg.isEmpty() ? msg : "踢出失败", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }

                            @Override
                            public void onError(Exception error) {
                                runOnUiThread(() -> Toast.makeText(GroupMembersActivity.this, "操作失败: " + error.getMessage(), Toast.LENGTH_SHORT).show());
                            }
                        });
                        return true;

                    case 2:
                        // 禁言 / 取消禁言
                        if (isTargetGagged) {
                            groupRepository.gagMember(token, groupId, targetUserId, 0, new GroupRepository.GroupActionCallback() {
                                @Override
                                public void onSuccess(int code, String msg) {
                                    runOnUiThread(() -> {
                                        if (code == 1) {
                                            Toast.makeText(GroupMembersActivity.this, "已取消禁言", Toast.LENGTH_SHORT).show();
                                            fetchMembers();
                                        } else {
                                            Toast.makeText(GroupMembersActivity.this, msg != null && !msg.isEmpty() ? msg : "操作失败", Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                }

                                @Override
                                public void onError(Exception error) {
                                    runOnUiThread(() -> Toast.makeText(GroupMembersActivity.this, "操作失败: " + error.getMessage(), Toast.LENGTH_SHORT).show());
                                }
                            });
                        } else {
                            showGagOptionsDialog(token, targetUserId);
                        }
                        return true;

                    case 3:
                        // 设置 / 移除管理员
                        groupRepository.editAdmin(token, groupId, targetUserId, !isTargetAdmin, new GroupRepository.GroupActionCallback() {
                            @Override
                            public void onSuccess(int code, String msg) {
                                runOnUiThread(() -> {
                                    if (code == 1) {
                                        Toast.makeText(GroupMembersActivity.this, !isTargetAdmin ? "已设为管理员" : "已移除管理员", Toast.LENGTH_SHORT).show();
                                        if (!isTargetAdmin) {
                                            adminIds.add(targetUserId);
                                        } else {
                                            adminIds.remove(targetUserId);
                                        }
                                        notifyItemChanged(position);
                                    } else {
                                        Toast.makeText(GroupMembersActivity.this, msg != null && !msg.isEmpty() ? msg : "操作失败", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }

                            @Override
                            public void onError(Exception error) {
                                runOnUiThread(() -> Toast.makeText(GroupMembersActivity.this, "操作失败: " + error.getMessage(), Toast.LENGTH_SHORT).show());
                            }
                        });
                        return true;

                    default:
                        return false;
                }
            });

            popup.show();
        }

        private void showGagOptionsDialog(String token, String targetUserId) {
            String[] options = new String[]{"10分钟", "1小时", "6小时", "12小时", "永久禁言"};
            int[] seconds = new int[]{600, 3600, 21600, 43200, -1};

            AlertDialog.Builder builder = new AlertDialog.Builder(GroupMembersActivity.this);
            builder.setTitle("选择禁言时长");
            builder.setItems(options, (dialog, which) -> {
                int duration = seconds[which];
                groupRepository.gagMember(token, groupId, targetUserId, duration, new GroupRepository.GroupActionCallback() {
                    @Override
                    public void onSuccess(int code, String msg) {
                        runOnUiThread(() -> {
                            if (code == 1) {
                                Toast.makeText(GroupMembersActivity.this, "已禁言该成员", Toast.LENGTH_SHORT).show();
                                fetchMembers();
                            } else {
                                Toast.makeText(GroupMembersActivity.this, msg != null && !msg.isEmpty() ? msg : "禁言失败", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void onError(Exception error) {
                        runOnUiThread(() -> Toast.makeText(GroupMembersActivity.this, "操作失败: " + error.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                });
            });
            builder.show();
        }

        @Override
        public int getItemCount() {
            return members.size();
        }

        class MemberViewHolder extends RecyclerView.ViewHolder {
            AppCompatImageView ivAvatar;
            TextView tvName;
            TextView tvTag;
            AppCompatImageButton ibMore;

            MemberViewHolder(@NonNull View itemView) {
                super(itemView);
                ivAvatar = itemView.findViewById(R.id.ivAvatar);
                tvName = itemView.findViewById(R.id.tvName);
                tvTag = itemView.findViewById(R.id.tvTag);
                ibMore = itemView.findViewById(R.id.ibMore);
            }
        }
    }
}

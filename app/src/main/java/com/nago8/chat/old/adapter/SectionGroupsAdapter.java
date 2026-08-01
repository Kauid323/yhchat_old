package com.nago8.chat.old.adapter;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.imageview.ShapeableImageView;
import com.nago8.chat.old.GroupProfileActivity;
import com.nago8.chat.old.R;
import com.nago8.chat.old.model.SectionGroupModel;
import com.nago8.chat.old.utils.ImageUtils;

import java.util.ArrayList;
import java.util.List;

public class SectionGroupsAdapter extends RecyclerView.Adapter<SectionGroupsAdapter.GroupViewHolder> {

    private final Context context;
    private final List<SectionGroupModel> groupList = new ArrayList<>();

    public SectionGroupsAdapter(Context context) {
        this.context = context;
    }

    public void setGroups(List<SectionGroupModel> newGroups) {
        this.groupList.clear();
        if (newGroups != null) {
            this.groupList.addAll(newGroups);
        }
        notifyDataSetChanged();
    }

    public void addGroups(List<SectionGroupModel> newGroups) {
        if (newGroups != null && !newGroups.isEmpty()) {
            int start = this.groupList.size();
            this.groupList.addAll(newGroups);
            notifyItemRangeInserted(start, newGroups.size());
        }
    }

    @NonNull
    @Override
    public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_section_group, parent, false);
        return new GroupViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
        holder.bind(groupList.get(position));
    }

    @Override
    public int getItemCount() {
        return groupList.size();
    }

    class GroupViewHolder extends RecyclerView.ViewHolder {
        private final ShapeableImageView ivGroupAvatar;
        private final TextView tvGroupName;
        private final TextView tvGroupInfo;

        public GroupViewHolder(@NonNull View itemView) {
            super(itemView);
            ivGroupAvatar = itemView.findViewById(R.id.ivGroupAvatar);
            tvGroupName = itemView.findViewById(R.id.tvGroupName);
            tvGroupInfo = itemView.findViewById(R.id.tvGroupInfo);
        }

        public void bind(SectionGroupModel group) {
            if (group == null) return;

            tvGroupName.setText(group.getName());

            String headcountStr = context.getString(R.string.group_members_format, group.getHeadcount());
            String categoryStr = group.getCategory();
            if (!TextUtils.isEmpty(categoryStr)) {
                tvGroupInfo.setText(headcountStr + " • " + categoryStr);
            } else if (!TextUtils.isEmpty(group.getIntroduction())) {
                tvGroupInfo.setText(headcountStr + " • " + group.getIntroduction());
            } else {
                tvGroupInfo.setText(headcountStr);
            }

            ImageUtils.loadAvatar(context, group.getAvatarUrl(), ivGroupAvatar);

            itemView.setOnClickListener(v -> {
                if (!TextUtils.isEmpty(group.getGroupId())) {
                    Intent intent = new Intent(context, GroupProfileActivity.class);
                    intent.putExtra(GroupProfileActivity.EXTRA_GROUP_ID, group.getGroupId());
                    context.startActivity(intent);
                }
            });
        }
    }
}

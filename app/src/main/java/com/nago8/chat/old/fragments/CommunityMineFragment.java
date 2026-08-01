package com.nago8.chat.old.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.nago8.chat.old.BlockedUsersActivity;
import com.nago8.chat.old.MyCollectsActivity;
import com.nago8.chat.old.MyPostsActivity;
import com.nago8.chat.old.R;

public class CommunityMineFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_community_mine, container, false);

        View menuMyPosts = view.findViewById(R.id.menuMyPosts);
        View menuMyCollects = view.findViewById(R.id.menuMyCollects);
        View menuBlockedUsers = view.findViewById(R.id.menuBlockedUsers);

        menuMyPosts.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), MyPostsActivity.class);
            startActivity(intent);
        });

        menuMyCollects.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), MyCollectsActivity.class);
            startActivity(intent);
        });

        menuBlockedUsers.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), BlockedUsersActivity.class);
            startActivity(intent);
        });

        return view;
    }
}

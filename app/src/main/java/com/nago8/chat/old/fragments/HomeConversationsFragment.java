package com.nago8.chat.old.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.nago8.chat.old.R;

public class HomeConversationsFragment extends Fragment {

    private ViewPager2 viewPager;
    private ConversationsFragment conversationsFragment;
    private StickyConversationsFragment stickyConversationsFragment;
    private OnViewPagerReadyListener onViewPagerReadyListener;

    public interface OnViewPagerReadyListener {
        void onViewPagerReady(ViewPager2 viewPager);
    }

    public void setOnViewPagerReadyListener(OnViewPagerReadyListener listener) {
        this.onViewPagerReadyListener = listener;
        if (viewPager != null && listener != null) {
            listener.onViewPagerReady(viewPager);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home_conversations, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewPager = view.findViewById(R.id.viewPagerConversations);

        conversationsFragment = new ConversationsFragment();
        stickyConversationsFragment = new StickyConversationsFragment();

        viewPager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                if (position == 1) {
                    return stickyConversationsFragment;
                }
                return conversationsFragment;
            }

            @Override
            public int getItemCount() {
                return 2;
            }
        });

        if (onViewPagerReadyListener != null) {
            onViewPagerReadyListener.onViewPagerReady(viewPager);
        }
    }

    public ViewPager2 getViewPager() {
        return viewPager;
    }

    public ConversationsFragment getConversationsFragment() {
        return conversationsFragment;
    }

    public StickyConversationsFragment getStickyConversationsFragment() {
        return stickyConversationsFragment;
    }
}

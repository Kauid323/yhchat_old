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

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.nago8.chat.old.R;

public class CommunityFragment extends Fragment {

    private TabLayout tabLayoutCommunity;
    private ViewPager2 viewPagerCommunity;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_community, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tabLayoutCommunity = view.findViewById(R.id.tabLayoutCommunity);
        viewPagerCommunity = view.findViewById(R.id.viewPagerCommunity);

        viewPagerCommunity.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                switch (position) {
                    case 0:
                        return new CommunityAllFragment();
                    case 1:
                        return new CommunitySectionsFragment();
                    case 2:
                        return new CommunityMineFragment();
                    default:
                        return new CommunityAllFragment();
                }
            }

            @Override
            public int getItemCount() {
                return 3;
            }
        });

        new TabLayoutMediator(tabLayoutCommunity, viewPagerCommunity, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText(R.string.tab_community_all);
                    break;
                case 1:
                    tab.setText(R.string.tab_community_sections);
                    break;
                case 2:
                    tab.setText(R.string.tab_community_mine);
                    break;
            }
        }).attach();
    }
}
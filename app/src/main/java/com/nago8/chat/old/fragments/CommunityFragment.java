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

    public static final String ARG_IS_PICKER_MODE = "is_picker_mode";

    private View layoutTabsContainer;
    private View containerSectionDetail;
    private TabLayout tabLayoutCommunity;
    private ViewPager2 viewPagerCommunity;

    public static CommunityFragment newInstance(boolean isPickerMode) {
        CommunityFragment fragment = new CommunityFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_IS_PICKER_MODE, isPickerMode);
        fragment.setArguments(args);
        return fragment;
    }

    public boolean isPickerMode() {
        if (getArguments() != null) {
            return getArguments().getBoolean(ARG_IS_PICKER_MODE, false);
        }
        return false;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_community, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        layoutTabsContainer = view.findViewById(R.id.layoutTabsContainer);
        containerSectionDetail = view.findViewById(R.id.containerSectionDetail);
        tabLayoutCommunity = view.findViewById(R.id.tabLayoutCommunity);
        viewPagerCommunity = view.findViewById(R.id.viewPagerCommunity);

        viewPagerCommunity.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                switch (position) {
                    case 1:
                        return new CommunitySectionsFragment();
                    case 2:
                        return new CommunityMineFragment();
                    case 0:
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

        com.nago8.chat.old.utils.ThemeUtils.applyThemeToViewTree(view, com.nago8.chat.old.utils.ThemeUtils.getThemeColor(getContext()));
    }

    public void openSectionDetail(int baId, String baName) {
        if (containerSectionDetail == null || layoutTabsContainer == null) return;
        containerSectionDetail.setVisibility(View.VISIBLE);
        layoutTabsContainer.setVisibility(View.GONE);

        SectionDetailFragment fragment = SectionDetailFragment.newInstance(baId, baName);
        getChildFragmentManager().beginTransaction()
                .replace(R.id.containerSectionDetail, fragment)
                .commit();
    }

    public void closeSectionDetail() {
        if (containerSectionDetail == null || layoutTabsContainer == null) return;
        containerSectionDetail.setVisibility(View.GONE);
        layoutTabsContainer.setVisibility(View.VISIBLE);

        Fragment fragment = getChildFragmentManager().findFragmentById(R.id.containerSectionDetail);
        if (fragment != null) {
            getChildFragmentManager().beginTransaction().remove(fragment).commit();
        }
    }
}
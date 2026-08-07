package com.nago8.chat.old.fragments;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.nago8.chat.old.ChatActivity;
import com.nago8.chat.old.R;
import com.nago8.chat.old.listeners.OnArticleSelectListener;
import com.nago8.chat.old.model.CommunityPostModel;
import com.nago8.chat.old.repository.CommunityRepository;
import com.nago8.chat.old.utils.PrefUtils;

public class ArticlePickerBottomSheetDialogFragment extends BottomSheetDialogFragment implements OnArticleSelectListener {

    private static final String ARG_CHAT_ID = "chat_id";
    private static final String ARG_CHAT_TYPE = "chat_type";

    private String chatId;
    private int chatType;
    private CommunityRepository communityRepository;

    public static ArticlePickerBottomSheetDialogFragment newInstance(String chatId, int chatType) {
        ArticlePickerBottomSheetDialogFragment fragment = new ArticlePickerBottomSheetDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CHAT_ID, chatId);
        args.putInt(ARG_CHAT_TYPE, chatType);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            chatId = getArguments().getString(ARG_CHAT_ID);
            chatType = getArguments().getInt(ARG_CHAT_TYPE);
        }
        communityRepository = new CommunityRepository();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            BottomSheetDialog bsd = (BottomSheetDialog) d;
            View bottomSheet = bsd.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
                ViewGroup.LayoutParams params = bottomSheet.getLayoutParams();
                params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                bottomSheet.setLayoutParams(params);
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.layout_article_picker_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        View btnClose = view.findViewById(R.id.btnClose);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dismissAllowingStateLoss());
        }

        if (savedInstanceState == null) {
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.articlePickerContainer, new CommunityFragment())
                    .commit();
        }
    }

    @Override
    public void onArticleSelected(CommunityPostModel post) {
        if (post == null) return;
        if (getContext() == null) return;

        String token = PrefUtils.getToken(requireContext());
        if (token == null || token.isEmpty()) {
            Toast.makeText(getContext(), R.string.address_book_not_logged_in, Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(getContext(), "正在发送文章...", Toast.LENGTH_SHORT).show();

        communityRepository.forwardPost(token, post.getId(), chatId, chatType, new CommunityRepository.StringCallback() {
            @Override
            public void onSuccess(String responseBody) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "文章发送成功", Toast.LENGTH_SHORT).show();
                        dismissAllowingStateLoss();
                        if (getActivity() instanceof ChatActivity) {
                            ((ChatActivity) getActivity()).fetchLatestMessage();
                        }
                    });
                }
            }

            @Override
            public void onError(String msg) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "发送文章失败: " + msg, Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }
}

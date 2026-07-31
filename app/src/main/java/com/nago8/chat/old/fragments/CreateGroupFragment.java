package com.nago8.chat.old.fragments;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.nago8.chat.old.ChatActivity;
import com.nago8.chat.old.R;
import com.nago8.chat.old.repository.GroupRepository;
import com.nago8.chat.old.utils.ImageUploadUtils;
import com.nago8.chat.old.utils.PrefUtils;

public class CreateGroupFragment extends Fragment {

    private static final int REQUEST_CODE_PICK_AVATAR = 1001;

    private ImageView ivGroupAvatarPreview;
    private Button btnSelectGroupAvatar;
    private EditText etGroupName;
    private EditText etGroupIntro;
    private EditText etAvatarUrl;
    private Button btnSubmitGroup;
    private ProgressBar progressBarGroup;
    private GroupRepository groupRepository;
    private Uri selectedAvatarUri;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_create_group, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ivGroupAvatarPreview = view.findViewById(R.id.ivGroupAvatarPreview);
        btnSelectGroupAvatar = view.findViewById(R.id.btnSelectGroupAvatar);
        etGroupName = view.findViewById(R.id.etGroupName);
        etGroupIntro = view.findViewById(R.id.etGroupIntro);
        etAvatarUrl = view.findViewById(R.id.etAvatarUrl);
        btnSubmitGroup = view.findViewById(R.id.btnSubmitGroup);
        progressBarGroup = view.findViewById(R.id.progressBarGroup);
        groupRepository = new GroupRepository();

        btnSelectGroupAvatar.setOnClickListener(v -> openAvatarPicker());
        btnSubmitGroup.setOnClickListener(v -> submitCreateGroup());
    }

    private void openAvatarPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, getString(R.string.report_pick_image_title)), REQUEST_CODE_PICK_AVATAR);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_AVATAR && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            selectedAvatarUri = data.getData();
            if (ivGroupAvatarPreview != null) {
                ivGroupAvatarPreview.setImageURI(selectedAvatarUri);
                ivGroupAvatarPreview.setVisibility(View.VISIBLE);
            }
        }
    }

    private void submitCreateGroup() {
        if (getContext() == null) return;
        String name = etGroupName.getText().toString().trim();
        String intro = etGroupIntro.getText().toString().trim();
        String manualAvatarUrl = etAvatarUrl.getText().toString().trim();

        if (name.isEmpty()) {
            Toast.makeText(getContext(), R.string.hint_group_name, Toast.LENGTH_SHORT).show();
            return;
        }

        String token = PrefUtils.getToken(requireContext());
        if (token == null || token.isEmpty()) {
            Toast.makeText(getContext(), R.string.address_book_not_logged_in, Toast.LENGTH_SHORT).show();
            return;
        }

        progressBarGroup.setVisibility(View.VISIBLE);
        btnSubmitGroup.setEnabled(false);

        if (selectedAvatarUri != null) {
            ImageUploadUtils.getQiniuUploadToken(token, new ImageUploadUtils.TokenCallback() {
                @Override
                public void onSuccess(String uploadToken) {
                    if (getContext() == null) return;
                    ImageUploadUtils.uploadImage(requireContext(), selectedAvatarUri, uploadToken, new ImageUploadUtils.UploadCallback() {
                        @Override
                        public void onSuccess(ImageUploadUtils.QiniuResult result) {
                            String finalAvatarUrl = "https://chat-img.jwznb.com/" + result.key;
                            doCreateGroup(token, name, intro, finalAvatarUrl);
                        }

                        @Override
                        public void onError(Exception e) {
                            if (getActivity() == null) return;
                            getActivity().runOnUiThread(() -> {
                                progressBarGroup.setVisibility(View.GONE);
                                btnSubmitGroup.setEnabled(true);
                                Toast.makeText(getContext(), getString(R.string.report_image_upload_failed_format, e.getMessage()), Toast.LENGTH_SHORT).show();
                            });
                        }
                    });
                }

                @Override
                public void onError(Exception e) {
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() -> {
                        progressBarGroup.setVisibility(View.GONE);
                        btnSubmitGroup.setEnabled(true);
                        Toast.makeText(getContext(), getString(R.string.report_token_failed_format, e.getMessage()), Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } else {
            doCreateGroup(token, name, intro, manualAvatarUrl);
        }
    }

    private void doCreateGroup(String token, String name, String intro, String avatarUrl) {
        groupRepository.createGroup(token, name, intro, avatarUrl, new GroupRepository.GroupActionCallback() {
            @Override
            public void onSuccess(int code, String msg) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    progressBarGroup.setVisibility(View.GONE);
                    btnSubmitGroup.setEnabled(true);
                    if (code == 1 || (msg != null && !msg.isEmpty() && msg.matches("\\d+"))) {
                        Toast.makeText(getContext(), R.string.create_group_success, Toast.LENGTH_SHORT).show();
                        String createdGroupId = (msg != null && !msg.isEmpty() && !msg.equals("success")) ? msg : "";
                        if (!createdGroupId.isEmpty() && getContext() != null) {
                            Intent intent = new Intent(getContext(), ChatActivity.class);
                            intent.putExtra(ChatActivity.EXTRA_CHAT_ID, createdGroupId);
                            intent.putExtra(ChatActivity.EXTRA_CHAT_TYPE, 2);
                            intent.putExtra(ChatActivity.EXTRA_CHAT_NAME, name);
                            intent.putExtra(ChatActivity.EXTRA_CHAT_AVATAR, avatarUrl);
                            startActivity(intent);
                        }
                        getActivity().finish();
                    } else {
                        Toast.makeText(getContext(), getString(R.string.create_failed_format, msg != null && !msg.isEmpty() ? msg : "Code " + code), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(Exception error) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    progressBarGroup.setVisibility(View.GONE);
                    btnSubmitGroup.setEnabled(true);
                    Toast.makeText(getContext(), getString(R.string.create_failed_format, error.getMessage()), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
}

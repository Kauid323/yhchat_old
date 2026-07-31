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
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import com.nago8.chat.old.ChatActivity;
import com.nago8.chat.old.R;
import com.nago8.chat.old.repository.BotRepository;
import com.nago8.chat.old.utils.ImageUploadUtils;
import com.nago8.chat.old.utils.PrefUtils;

public class CreateBotFragment extends Fragment {

    private static final int REQUEST_CODE_PICK_AVATAR = 1002;

    private ImageView ivBotAvatarPreview;
    private Button btnSelectBotAvatar;
    private EditText etBotName;
    private EditText etBotIntro;
    private EditText etBotAvatarUrl;
    private SwitchCompat switchPrivateBot;
    private Button btnSubmitBot;
    private ProgressBar progressBarBot;
    private BotRepository botRepository;
    private Uri selectedAvatarUri;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_create_bot, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ivBotAvatarPreview = view.findViewById(R.id.ivBotAvatarPreview);
        btnSelectBotAvatar = view.findViewById(R.id.btnSelectBotAvatar);
        etBotName = view.findViewById(R.id.etBotName);
        etBotIntro = view.findViewById(R.id.etBotIntro);
        etBotAvatarUrl = view.findViewById(R.id.etBotAvatarUrl);
        switchPrivateBot = view.findViewById(R.id.switchPrivateBot);
        btnSubmitBot = view.findViewById(R.id.btnSubmitBot);
        progressBarBot = view.findViewById(R.id.progressBarBot);
        botRepository = new BotRepository();

        btnSelectBotAvatar.setOnClickListener(v -> openAvatarPicker());
        btnSubmitBot.setOnClickListener(v -> submitCreateBot());
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
            if (ivBotAvatarPreview != null) {
                ivBotAvatarPreview.setImageURI(selectedAvatarUri);
                ivBotAvatarPreview.setVisibility(View.VISIBLE);
            }
        }
    }

    private void submitCreateBot() {
        if (getContext() == null) return;
        String name = etBotName.getText().toString().trim();
        String intro = etBotIntro.getText().toString().trim();
        String manualAvatarUrl = etBotAvatarUrl.getText().toString().trim();
        boolean isPrivate = switchPrivateBot.isChecked();

        if (name.isEmpty()) {
            Toast.makeText(getContext(), R.string.hint_bot_name, Toast.LENGTH_SHORT).show();
            return;
        }

        String token = PrefUtils.getToken(requireContext());
        if (token == null || token.isEmpty()) {
            Toast.makeText(getContext(), R.string.address_book_not_logged_in, Toast.LENGTH_SHORT).show();
            return;
        }

        progressBarBot.setVisibility(View.VISIBLE);
        btnSubmitBot.setEnabled(false);

        if (selectedAvatarUri != null) {
            ImageUploadUtils.getQiniuUploadToken(token, new ImageUploadUtils.TokenCallback() {
                @Override
                public void onSuccess(String uploadToken) {
                    if (getContext() == null) return;
                    ImageUploadUtils.uploadImage(requireContext(), selectedAvatarUri, uploadToken, new ImageUploadUtils.UploadCallback() {
                        @Override
                        public void onSuccess(ImageUploadUtils.QiniuResult result) {
                            String finalAvatarUrl = "https://chat-img.jwznb.com/" + result.key;
                            doCreateBot(token, name, intro, finalAvatarUrl, isPrivate);
                        }

                        @Override
                        public void onError(Exception e) {
                            if (getActivity() == null) return;
                            getActivity().runOnUiThread(() -> {
                                progressBarBot.setVisibility(View.GONE);
                                btnSubmitBot.setEnabled(true);
                                Toast.makeText(getContext(), getString(R.string.report_image_upload_failed_format, e.getMessage()), Toast.LENGTH_SHORT).show();
                            });
                        }
                    });
                }

                @Override
                public void onError(Exception e) {
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() -> {
                        progressBarBot.setVisibility(View.GONE);
                        btnSubmitBot.setEnabled(true);
                        Toast.makeText(getContext(), getString(R.string.report_token_failed_format, e.getMessage()), Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } else {
            doCreateBot(token, name, intro, manualAvatarUrl, isPrivate);
        }
    }

    private void doCreateBot(String token, String name, String intro, String avatarUrl, boolean isPrivate) {
        botRepository.createBot(token, name, intro, avatarUrl, isPrivate, new BotRepository.BotActionCallback() {
            @Override
            public void onSuccess(int code, String msg, String botId) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    progressBarBot.setVisibility(View.GONE);
                    btnSubmitBot.setEnabled(true);
                    if (code == 1 || (botId != null && !botId.isEmpty())) {
                        Toast.makeText(getContext(), R.string.create_bot_success, Toast.LENGTH_SHORT).show();
                        if (botId != null && !botId.isEmpty() && getContext() != null) {
                            Intent intent = new Intent(getContext(), ChatActivity.class);
                            intent.putExtra(ChatActivity.EXTRA_CHAT_ID, botId);
                            intent.putExtra(ChatActivity.EXTRA_CHAT_TYPE, 3);
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
                    progressBarBot.setVisibility(View.GONE);
                    btnSubmitBot.setEnabled(true);
                    Toast.makeText(getContext(), getString(R.string.create_failed_format, error.getMessage()), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
}

package com.nago8.chat.old;

import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.nago8.chat.old.model.ChatInstruction;
import com.nago8.chat.old.proto.send_message;
import com.nago8.chat.old.repository.MessageRepository;
import com.nago8.chat.old.utils.PrefUtils;
import com.nago8.chat.old.utils.ThemeUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class CustomInstructionActivity extends AppCompatActivity {

    public static final String EXTRA_INSTRUCTION = "extra_instruction";
    public static final String EXTRA_CHAT_ID = "extra_chat_id";
    public static final String EXTRA_CHAT_TYPE = "extra_chat_type";

    private ChatInstruction instruction;
    private String chatId;
    private int chatType;
    private MessageRepository repository;

    private TextView tvCommandTitle;
    private TextView tvBotBadge;
    private TextView tvCommandDesc;
    private LinearLayout layoutFormContainer;
    private LinearLayout layoutRawParam;
    private TextInputLayout tilRawParam;
    private TextInputEditText etRawParam;
    private MaterialButton btnSubmitInstruction;

    // 存储表单输入控件引用
    private final Map<String, View> formFields = new HashMap<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_instruction);
        ThemeUtils.registerActivity(this);

        instruction = (ChatInstruction) getIntent().getSerializableExtra(EXTRA_INSTRUCTION);
        chatId = getIntent().getStringExtra(EXTRA_CHAT_ID);
        chatType = getIntent().getIntExtra(EXTRA_CHAT_TYPE, 2);

        if (instruction == null || TextUtils.isEmpty(chatId)) {
            Toast.makeText(this, R.string.user_profile_load_failed, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        repository = new MessageRepository();

        initViews();
        bindInstructionData();
        applyThemeColors();
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.custom_instruction_title);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        tvCommandTitle = findViewById(R.id.tvCommandTitle);
        tvBotBadge = findViewById(R.id.tvBotBadge);
        tvCommandDesc = findViewById(R.id.tvCommandDesc);
        layoutFormContainer = findViewById(R.id.layoutFormContainer);
        layoutRawParam = findViewById(R.id.layoutRawParam);
        tilRawParam = findViewById(R.id.tilRawParam);
        etRawParam = findViewById(R.id.etRawParam);
        btnSubmitInstruction = findViewById(R.id.btnSubmitInstruction);

        btnSubmitInstruction.setOnClickListener(v -> performSubmit());
    }

    private void applyThemeColors() {
        int primaryColor = ThemeUtils.getThemeColor(this);

        tvCommandTitle.setTextColor(primaryColor);

        // 机器人标签主题色自适应
        if (!TextUtils.isEmpty(instruction.botName)) {
            tvBotBadge.setVisibility(View.VISIBLE);
            tvBotBadge.setText(instruction.botName);
            tvBotBadge.setTextColor(primaryColor);

            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setShape(GradientDrawable.RECTANGLE);
            badgeBg.setCornerRadius(dp(4));
            badgeBg.setColor((primaryColor & 0x00FFFFFF) | 0x22000000);
            badgeBg.setStroke(dp(1), (primaryColor & 0x00FFFFFF) | 0x44000000);
            tvBotBadge.setBackground(badgeBg);
        } else {
            tvBotBadge.setVisibility(View.GONE);
        }

        // 提交按钮主题色（使用 setBackgroundTintList 保持 MaterialButton 阴影与波纹水波纹特性）
        btnSubmitInstruction.setBackgroundTintList(ColorStateList.valueOf(primaryColor));
        int fgColor = ThemeUtils.getContrastingForegroundColor(primaryColor);
        btnSubmitInstruction.setTextColor(fgColor);

        if (tilRawParam != null) {
            applyMaterialFieldTheme(tilRawParam, primaryColor);
        }
    }

    private void bindInstructionData() {
        tvCommandTitle.setText(String.format(getString(R.string.chat_instruction_item_format), instruction.name));
        tvCommandDesc.setText(instruction.desc);

        String formStr = !TextUtils.isEmpty(instruction.form) ? instruction.form : instruction.botSettingsJson;
        if (!TextUtils.isEmpty(formStr)) {
            boolean parsed = buildDynamicForm(formStr);
            if (!parsed) {
                // 如果不是结构化 JSON 数组，展示自由文本/参数编辑框
                layoutRawParam.setVisibility(View.VISIBLE);
                etRawParam.setText(formStr);
            }
        } else {
            layoutRawParam.setVisibility(View.VISIBLE);
            if (!TextUtils.isEmpty(instruction.defaultText)) {
                etRawParam.setText(instruction.defaultText);
            }
            if (!TextUtils.isEmpty(instruction.hintText)) {
                tilRawParam.setHint(instruction.hintText);
            }
        }
    }

    private boolean buildDynamicForm(String jsonStr) {
        try {
            String trimmed = jsonStr.trim();
            if (trimmed.startsWith("[")) {
                JSONArray array = new JSONArray(trimmed);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.optJSONObject(i);
                    if (item != null) {
                        addMaterialFormField(item);
                    }
                }
                return array.length() > 0;
            } else if (trimmed.startsWith("{")) {
                JSONObject obj = new JSONObject(trimmed);
                Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    Object val = obj.opt(key);
                    addSimpleMaterialField(key, val);
                }
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void addMaterialFormField(JSONObject item) {
        String key = item.optString("key", item.optString("name", item.optString("id")));
        String label = item.optString("label", item.optString("title", key));
        String type = item.optString("type", "input");
        String defVal = item.optString("default", item.optString("value", ""));
        int primaryColor = ThemeUtils.getThemeColor(this);

        if (TextUtils.isEmpty(key)) return;

        if ("switch".equalsIgnoreCase(type) || "boolean".equalsIgnoreCase(type)) {
            // Material 卡片式 Switch
            MaterialCardView switchCard = new MaterialCardView(this);
            switchCard.setRadius(dp(10));
            switchCard.setCardElevation(dp(1));
            switchCard.setStrokeWidth(dp(1));
            switchCard.setStrokeColor(ContextCompat.getColor(this, R.color.divider_color));

            LinearLayout switchLayout = new LinearLayout(this);
            switchLayout.setOrientation(LinearLayout.HORIZONTAL);
            switchLayout.setGravity(Gravity.CENTER_VERTICAL);
            switchLayout.setPadding(dp(16), dp(12), dp(16), dp(12));

            TextView tvLabel = new TextView(this);
            tvLabel.setText(label);
            tvLabel.setTextSize(15);
            tvLabel.setTextColor(ContextCompat.getColor(this, R.color.bubble_text_left));
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            switchLayout.addView(tvLabel, labelParams);

            SwitchCompat sw = new SwitchCompat(this);
            sw.setChecked("true".equalsIgnoreCase(defVal) || "1".equals(defVal));
            switchLayout.addView(sw);

            switchCard.addView(switchLayout);

            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardParams.bottomMargin = dp(14);
            layoutFormContainer.addView(switchCard, cardParams);

            formFields.put(key, sw);
        } else if ("select".equalsIgnoreCase(type) || "spinner".equalsIgnoreCase(type)) {
            // Material Outlined Dropdown
            TextInputLayout til = new TextInputLayout(this, null, com.google.android.material.R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox_ExposedDropdownMenu);
            til.setHint(label);
            til.setBoxCornerRadii(dp(10), dp(10), dp(10), dp(10));
            applyMaterialFieldTheme(til, primaryColor);

            AutoCompleteTextView actv = new AutoCompleteTextView(this);
            actv.setInputType(0); // 禁止直接键盘输入
            actv.setTextColor(ContextCompat.getColor(this, R.color.bubble_text_left));
            actv.setTextSize(14);

            JSONArray optionsArr = item.optJSONArray("options");
            List<String> optionsList = new ArrayList<>();
            if (optionsArr != null) {
                for (int j = 0; j < optionsArr.length(); j++) {
                    optionsList.add(optionsArr.optString(j));
                }
            }
            if (optionsList.isEmpty()) optionsList.add(defVal);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, optionsList);
            actv.setAdapter(adapter);
            actv.setText(defVal, false);

            til.addView(actv);

            LinearLayout.LayoutParams tilParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tilParams.bottomMargin = dp(14);
            layoutFormContainer.addView(til, tilParams);

            formFields.put(key, actv);
        } else {
            // 标准 Material TextInputLayout + TextInputEditText
            TextInputLayout til = new TextInputLayout(this, null, com.google.android.material.R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox);
            til.setHint(label);
            til.setBoxCornerRadii(dp(10), dp(10), dp(10), dp(10));
            applyMaterialFieldTheme(til, primaryColor);

            TextInputEditText tiet = new TextInputEditText(this);
            tiet.setText(defVal);
            tiet.setTextSize(14);
            tiet.setTextColor(ContextCompat.getColor(this, R.color.bubble_text_left));

            String placeholder = item.optString("hint", item.optString("placeholder", ""));
            if (!TextUtils.isEmpty(placeholder)) {
                til.setPlaceholderText(placeholder);
            }

            til.addView(tiet);

            LinearLayout.LayoutParams tilParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tilParams.bottomMargin = dp(14);
            layoutFormContainer.addView(til, tilParams);

            formFields.put(key, tiet);
        }
    }

    private void addSimpleMaterialField(String key, Object defaultVal) {
        int primaryColor = ThemeUtils.getThemeColor(this);

        TextInputLayout til = new TextInputLayout(this, null, com.google.android.material.R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox);
        til.setHint(key);
        til.setBoxCornerRadii(dp(10), dp(10), dp(10), dp(10));
        applyMaterialFieldTheme(til, primaryColor);

        TextInputEditText tiet = new TextInputEditText(this);
        tiet.setText(defaultVal != null ? String.valueOf(defaultVal) : "");
        tiet.setTextSize(14);
        tiet.setTextColor(ContextCompat.getColor(this, R.color.bubble_text_left));

        til.addView(tiet);

        LinearLayout.LayoutParams tilParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tilParams.bottomMargin = dp(14);
        layoutFormContainer.addView(til, tilParams);

        formFields.put(key, tiet);
    }

    private void applyMaterialFieldTheme(TextInputLayout til, int primaryColor) {
        int strokeColor = (primaryColor & 0x00FFFFFF) | 0x88000000;
        ColorStateList boxStrokeColorStateList = new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_focused},
                        new int[]{}
                },
                new int[]{
                        primaryColor,
                        strokeColor
                }
        );
        til.setBoxStrokeColorStateList(boxStrokeColorStateList);
        til.setHintTextColor(ColorStateList.valueOf(primaryColor));
        til.setDefaultHintTextColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.text_secondary)));
    }

    private void performSubmit() {
        String token = PrefUtils.getToken(this);
        if (TextUtils.isEmpty(token)) {
            Toast.makeText(this, R.string.chat_not_logged_in, Toast.LENGTH_SHORT).show();
            return;
        }

        String formResultJson = null;
        String textResult = null;

        if (!formFields.isEmpty()) {
            JSONObject resultObj = new JSONObject();
            try {
                for (Map.Entry<String, View> entry : formFields.entrySet()) {
                    String key = entry.getKey();
                    View view = entry.getValue();
                    if (view instanceof SwitchCompat) {
                        resultObj.put(key, ((SwitchCompat) view).isChecked());
                    } else if (view instanceof AutoCompleteTextView) {
                        resultObj.put(key, ((AutoCompleteTextView) view).getText().toString().trim());
                    } else if (view instanceof EditText) {
                        resultObj.put(key, ((EditText) view).getText().toString().trim());
                    }
                }
                formResultJson = resultObj.toString();
                textResult = formResultJson;
            } catch (Exception ignored) {}
        } else if (layoutRawParam.getVisibility() == View.VISIBLE) {
            String raw = etRawParam.getText() != null ? etRawParam.getText().toString().trim() : "";
            textResult = raw;
            formResultJson = raw;
        }

        btnSubmitInstruction.setEnabled(false);

        repository.sendFormInstructionMessage(token, chatId, chatType, instruction.commandId, formResultJson, textResult, new MessageRepository.SendMessageCallback() {
            @Override
            public void onSuccess(send_message response) {
                runOnUiThread(() -> {
                    Toast.makeText(CustomInstructionActivity.this, R.string.send_success, Toast.LENGTH_SHORT).show();
                    finish();
                });
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> {
                    btnSubmitInstruction.setEnabled(true);
                    Toast.makeText(CustomInstructionActivity.this, getString(R.string.chat_send_failed_format, error.getMessage()), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}

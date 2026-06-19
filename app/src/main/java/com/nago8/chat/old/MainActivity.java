package com.nago8.chat.old;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.tabs.TabLayout;
import com.nago8.chat.old.model.UserModels;
import com.nago8.chat.old.net.ApiClient;
import com.nago8.chat.old.utils.LocaleHelper;
import com.nago8.chat.old.utils.PrefUtils;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private LinearLayout layoutEmail;
    private LinearLayout layoutPhone;
    private EditText etEmail, etPassword;
    private EditText etPhone, etImageCode, etSmsCode;
    private ImageView ivImageCode;
    private String captchaId = "";

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        super.onCreate(savedInstanceState);

        // Auto-login check
        String token = PrefUtils.getToken(this);
        if (token != null && !token.isEmpty()) {
            goToHome();
            return;
        }

        setContentView(R.layout.activity_main);

        layoutEmail = findViewById(R.id.layoutEmail);
        layoutPhone = findViewById(R.id.layoutPhone);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etPhone = findViewById(R.id.etPhone);
        etImageCode = findViewById(R.id.etImageCode);
        etSmsCode = findViewById(R.id.etSmsCode);
        ivImageCode = findViewById(R.id.ivImageCode);
        Button btnGetCode = findViewById(R.id.btnGetCode);
        TabLayout tabLayout = findViewById(R.id.tabLayout);
        View btnLogin = findViewById(R.id.btnLogin);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    layoutEmail.setVisibility(View.VISIBLE);
                    layoutPhone.setVisibility(View.GONE);
                } else {
                    layoutEmail.setVisibility(View.GONE);
                    layoutPhone.setVisibility(View.VISIBLE);
                    if (captchaId.isEmpty()) fetchCaptcha();
                }
            }
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}
            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        ivImageCode.setOnClickListener(v -> fetchCaptcha());
        btnGetCode.setOnClickListener(v -> sendSmsCode());
        btnLogin.setOnClickListener(v -> {
            if (layoutEmail.getVisibility() == View.VISIBLE) performEmailLogin();
            else performPhoneLogin();
        });
    }

    private void fetchCaptcha() {
        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/user/captcha")
                .post(RequestBody.create(null, new byte[0]))
                .build();

        ApiClient.getClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "获取验证码失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    UserModels.CaptchaResponse res = ApiClient.getGson().fromJson(response.body().string(), UserModels.CaptchaResponse.class);
                    if (res.code == 1 && res.data != null) {
                        captchaId = res.data.id;
                        String base64 = res.data.b64s;
                        if (base64.contains(",")) base64 = base64.split(",")[1];
                        byte[] decodedString = Base64.decode(base64, Base64.DEFAULT);
                        Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                        runOnUiThread(() -> ivImageCode.setImageBitmap(decodedByte));
                    }
                }
            }
        });
    }

    private void sendSmsCode() {
        String phone = etPhone.getText().toString().trim();
        String code = etImageCode.getText().toString().trim();
        if (phone.isEmpty() || code.isEmpty() || captchaId.isEmpty()) {
            Toast.makeText(this, "请输入手机号和图形验证码", Toast.LENGTH_SHORT).show();
            return;
        }

        UserModels.SmsRequest smsRequest = new UserModels.SmsRequest(phone, code, captchaId, Build.MODEL);
        RequestBody body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), ApiClient.getGson().toJson(smsRequest));
        Request request = new Request.Builder()
                .url(ApiClient.BASE_URL + "/v1/verification/get-verification-code")
                .post(body)
                .build();

        ApiClient.getClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "发送失败", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseData = response.body().string();
                UserModels.CommonResponse res = ApiClient.getGson().fromJson(responseData, UserModels.CommonResponse.class);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, res.msg, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void performEmailLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "请输入账号密码", Toast.LENGTH_SHORT).show();
            return;
        }

        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        UserModels.LoginRequest loginRequest = new UserModels.LoginRequest(email, password, deviceId, "android");
        RequestBody body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), ApiClient.getGson().toJson(loginRequest));
        Request request = new Request.Builder().url(ApiClient.BASE_URL + "/v1/user/email-login").post(body).build();

        ApiClient.getClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "网络错误: " + e.getClass().getSimpleName() + " " + e.getMessage(), Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                });
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                handleLoginResponse(response);
            }
        });
    }

    private void performPhoneLogin() {
        String phone = etPhone.getText().toString().trim();
        String code = etSmsCode.getText().toString().trim();
        if (phone.isEmpty() || code.isEmpty()) {
            Toast.makeText(this, "请输入手机号和验证码", Toast.LENGTH_SHORT).show();
            return;
        }

        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        UserModels.PhoneLoginRequest loginRequest = new UserModels.PhoneLoginRequest(phone, code, deviceId, "android");
        RequestBody body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), ApiClient.getGson().toJson(loginRequest));
        Request request = new Request.Builder().url(ApiClient.BASE_URL + "/v1/user/verification-login").post(body).build();

        ApiClient.getClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "网络错误: " + e.getClass().getSimpleName() + " " + e.getMessage(), Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                });
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                handleLoginResponse(response);
            }
        });
    }

    private void handleLoginResponse(Response response) throws IOException {
        if (response.isSuccessful()) {
            String responseData = response.body().string();
            UserModels.LoginResponse loginResponse = ApiClient.getGson().fromJson(responseData, UserModels.LoginResponse.class);
            if (loginResponse.code == 1 && loginResponse.data != null) {
                PrefUtils.saveToken(this, loginResponse.data.token);
                runOnUiThread(this::goToHome);
            } else {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "登录失败: " + loginResponse.msg, Toast.LENGTH_SHORT).show());
            }
        } else {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "服务器错误: " + response.code(), Toast.LENGTH_SHORT).show());
        }
    }

    private void goToHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        finish();
    }
}

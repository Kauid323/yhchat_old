package com.nago8.chat.old.model;

public class UserModels {

    public static class LoginRequest {
        public String email, password, deviceId, platform;
        public LoginRequest(String email, String password, String deviceId, String platform) {
            this.email = email; this.password = password; this.deviceId = deviceId; this.platform = platform;
        }
    }

    public static class PhoneLoginRequest {
        public String mobile, captcha, deviceId, platform;
        public PhoneLoginRequest(String mobile, String captcha, String deviceId, String platform) {
            this.mobile = mobile; this.captcha = captcha; this.deviceId = deviceId; this.platform = platform;
        }
    }

    public static class SmsRequest {
        public String mobile, code, id, platform;
        public SmsRequest(String mobile, String code, String id, String platform) {
            this.mobile = mobile; this.code = code; this.id = id; this.platform = platform;
        }
    }

    public static class CaptchaResponse {
        public int code;
        public Data data;
        public String msg;
        public static class Data { public String b64s, id; }
    }

    public static class LoginResponse {
        public int code;
        public Data data;
        public String msg;
        public static class Data { public String token; }
    }

    public static class CommonResponse {
        public int code;
        public String msg;
    }

    public static class LogoutRequest {
        public String deviceId;
        public LogoutRequest(String deviceId) {
            this.deviceId = deviceId;
        }
    }
}
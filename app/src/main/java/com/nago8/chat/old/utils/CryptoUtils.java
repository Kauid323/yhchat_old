package com.nago8.chat.old.utils;

import android.annotation.SuppressLint;
import android.util.Base64;

import java.nio.charset.StandardCharsets;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class CryptoUtils {
    private static final String KEY = "1234224343298904"; // Fixed: AES-128 key must be exactly 16 bytes
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";

    @SuppressLint("GetInstance")
    public static String encrypt(String data) throws Exception {
        if (data == null) return null;
        SecretKeySpec secretKey = new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "AES");
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(encrypted, Base64.DEFAULT);
    }

    @SuppressLint("GetInstance")
    public static String decrypt(String encryptedData) throws Exception {
        if (encryptedData == null) return null;
        SecretKeySpec secretKey = new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "AES");
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        byte[] decoded = Base64.decode(encryptedData, Base64.DEFAULT);
        return new String(cipher.doFinal(decoded), StandardCharsets.UTF_8);
    }
}
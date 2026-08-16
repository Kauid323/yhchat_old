package com.nago8.chat.old.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.nago8.chat.old.BotProfileActivity;
import com.nago8.chat.old.GroupProfileActivity;
import com.nago8.chat.old.PostDetailActivity;
import com.nago8.chat.old.UserProfileActivity;
import com.nago8.chat.old.net.ApiClient;

import org.json.JSONObject;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class InternalLinkUtils {

    private static final String TAG = "InternalLinkUtils";

    // 匹配 yunhu://post-detail?id=xxx
    private static final Pattern YUNHU_POST_PATTERN = Pattern.compile("yunhu://post-detail\\?(?:.*&)?id=([^&\\s]+)");

    // 匹配 www.yhchat.com/c/p/xxx 或 https://www.yhchat.com/c/p/xxx 或 yhchat.com/c/p/xxx
    private static final Pattern YHCHAT_POST_PATTERN = Pattern.compile("(?:https?://)?(?:[a-zA-Z0-9-]+\\.)?yhchat\\.com/c/p/([^/?\\s#]+)");

    // 匹配 yunhu://add-chat?id=xxx&type=user / group
    private static final Pattern YUNHU_ADD_CHAT_PATTERN = Pattern.compile("yunhu://add-chat\\?(?:[^\\s]+)");

    // 匹配 yhfx.jwznb.com/share?key=xxx 或 yunhu://share?key=xxx
    private static final Pattern SHARE_LINK_PATTERN = Pattern.compile("(?:https?://)?(?:[a-zA-Z0-9-]+\\.)?jwznb\\.com/share\\?(?:[^\\s]+)");

    // 通用 URL 正则匹配（包含 http, https, yunhu 协议）
    private static final Pattern GENERAL_URL_PATTERN = Pattern.compile("(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+|yunhu://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)");

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * 判断并解析链接中的文章 ID。
     */
    public static String parsePostId(String url) {
        if (url == null || url.trim().isEmpty()) return null;
        String trimmedUrl = url.trim();

        Matcher yunhuMatcher = YUNHU_POST_PATTERN.matcher(trimmedUrl);
        if (yunhuMatcher.find()) {
            return yunhuMatcher.group(1);
        }

        if (trimmedUrl.startsWith("yunhu://post-detail")) {
            try {
                Uri uri = Uri.parse(trimmedUrl);
                String id = uri.getQueryParameter("id");
                if (id != null && !id.isEmpty()) {
                    return id;
                }
            } catch (Exception ignored) {}
        }

        Matcher yhchatMatcher = YHCHAT_POST_PATTERN.matcher(trimmedUrl);
        if (yhchatMatcher.find()) {
            return yhchatMatcher.group(1);
        }

        return null;
    }

    /**
     * 核心统一分发：处理链接点击（文章、分享、云湖 Scheme 或普通外部网页）。
     * @return 是否作为内链或分享链接被拦截消费
     */
    public static boolean handleUrl(Context context, String url) {
        if (context == null || url == null || url.trim().isEmpty()) return false;
        String trimmedUrl = url.trim();

        // 1. 处理文章内链
        String postId = parsePostId(trimmedUrl);
        if (postId != null && !postId.isEmpty()) {
            Intent intent = new Intent(context, PostDetailActivity.class);
            intent.putExtra(PostDetailActivity.EXTRA_POST_ID, postId);
            context.startActivity(intent);
            return true;
        }

        // 2. 处理分享链接（如 https://yhfx.jwznb.com/share?key=WQEwaxPFqvOg&ts=1786892659）
        if (trimmedUrl.contains("jwznb.com/share") || trimmedUrl.startsWith("yunhu://share")) {
            parseAndOpenShareLink(context, trimmedUrl);
            return true;
        }

        // 3. 处理添加会话 Scheme（yunhu://add-chat?id=xxx&type=user）
        if (trimmedUrl.startsWith("yunhu://add-chat")) {
            try {
                Uri uri = Uri.parse(trimmedUrl);
                String id = uri.getQueryParameter("id");
                String type = uri.getQueryParameter("type");
                if (id != null && !id.isEmpty()) {
                    if ("group".equalsIgnoreCase(type) || "2".equals(type)) {
                        Intent intent = new Intent(context, GroupProfileActivity.class);
                        intent.putExtra(GroupProfileActivity.EXTRA_GROUP_ID, id);
                        context.startActivity(intent);
                        return true;
                    } else if ("bot".equalsIgnoreCase(type) || "3".equals(type)) {
                        Intent intent = new Intent(context, BotProfileActivity.class);
                        intent.putExtra(BotProfileActivity.EXTRA_BOT_ID, id);
                        context.startActivity(intent);
                        return true;
                    } else {
                        Intent intent = new Intent(context, UserProfileActivity.class);
                        intent.putExtra(UserProfileActivity.EXTRA_USER_ID, id);
                        context.startActivity(intent);
                        return true;
                    }
                }
            } catch (Exception ignored) {}
        }

        return false;
    }

    /**
     * 调用云湖分享信息 API (POST /v1/share/info) 解析分享并打开对应详情页
     */
    private static void parseAndOpenShareLink(Context context, String url) {
        String key = null;
        String ts = "";
        try {
            Uri uri = Uri.parse(url.startsWith("http") || url.startsWith("yunhu://") ? url : "https://" + url);
            key = uri.getQueryParameter("key");
            String tsParam = uri.getQueryParameter("ts");
            if (tsParam != null) ts = tsParam;
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse URI: " + url, e);
        }

        if (key == null || key.isEmpty()) {
            openFallbackBrowser(context, url);
            return;
        }

        Toast.makeText(context, "正在获取分享信息...", Toast.LENGTH_SHORT).show();

        JSONObject json = new JSONObject();
        try {
            json.put("key", key);
            if (!ts.isEmpty()) {
                json.put("ts", ts);
            }
        } catch (Exception ignored) {}

        RequestBody body = RequestBody.create(
                MediaType.parse("application/json; charset=utf-8"),
                json.toString()
        );

        Request request = new Request.Builder()
                .url("https://chat-go.jwzhd.com/v1/share/info")
                .post(body)
                .build();

        final String finalKey = key;
        ApiClient.getClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Fetch share info failed", e);
                mainHandler.post(() -> openFallbackBrowser(context, url));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    mainHandler.post(() -> openFallbackBrowser(context, url));
                    return;
                }
                String respStr = response.body() != null ? response.body().string() : "";
                try {
                    JSONObject root = new JSONObject(respStr);
                    JSONObject data = root.optJSONObject("data");
                    if (data != null && data.has("share")) {
                        JSONObject share = data.getJSONObject("share");
                        int chatType = share.optInt("chat_type", 0);
                        String chatId = share.optString("chat_id", "");
                        if (!chatId.isEmpty()) {
                            mainHandler.post(() -> openTargetChatDetail(context, chatType, chatId));
                            return;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Parse share JSON failed: " + respStr, e);
                }

                mainHandler.post(() -> openFallbackBrowser(context, url));
            }
        });
    }

    /**
     * 根据 chat_type 和 chat_id 打开对应详情页
     */
    private static void openTargetChatDetail(Context context, int chatType, String chatId) {
        try {
            Intent intent;
            if (chatType == 2) {
                // 群聊
                intent = new Intent(context, GroupProfileActivity.class);
                intent.putExtra(GroupProfileActivity.EXTRA_GROUP_ID, chatId);
            } else if (chatType == 3) {
                // 机器人
                intent = new Intent(context, BotProfileActivity.class);
                intent.putExtra(BotProfileActivity.EXTRA_BOT_ID, chatId);
            } else {
                // 用户 (chatType == 1)
                intent = new Intent(context, UserProfileActivity.class);
                intent.putExtra(UserProfileActivity.EXTRA_USER_ID, chatId);
            }
            if (!(context instanceof Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "打开详情失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private static void openFallbackBrowser(Context context, String url) {
        try {
            String openUrl = url;
            if (!openUrl.startsWith("http://") && !openUrl.startsWith("https://") && !openUrl.startsWith("yunhu://")) {
                openUrl = "https://" + openUrl;
            }
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(openUrl));
            if (!(context instanceof Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "无法打开链接: " + url, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 扫描 TextView 中的文本，自动识别内链、分享链接及各类 URL 并加上智能对比度高亮和点击效果
     */
    public static void processTextViewLinks(TextView textView, boolean isMine) {
        if (textView == null) return;
        CharSequence text = textView.getText();
        if (text == null || text.length() == 0) return;

        Context context = textView.getContext();
        int primaryColor = ThemeUtils.getThemeColor(context);

        // 自适应配色算法：
        // 1. 如果是自己发送的消息（右侧气泡，背景为 primaryColor）：
        //    根据 primaryColor 的明暗决定高亮前景色（深色背景用高对比浅蓝/纯白，浅色背景用深蓝/深黑）
        // 2. 如果是他人发送的消息（左侧气泡，背景为白/浅灰）：
        //    使用 primaryColor 主题色作为高亮链接颜色（若主题色偏浅则自动微调饱和度，确保绝对清晰）
        int linkColor;
        if (isMine) {
            if (ThemeUtils.isColorDark(primaryColor)) {
                linkColor = 0xFFBAE6FD; // 亮浅天蓝
            } else {
                linkColor = 0xFF0369A1; // 深海蓝
            }
        } else {
            if (ThemeUtils.isColorDark(primaryColor)) {
                linkColor = primaryColor;
            } else {
                // 如果主题色较浅，在白色底上加深后使用
                float[] hsv = new float[3];
                android.graphics.Color.colorToHSV(primaryColor, hsv);
                hsv[2] *= 0.65f; // 降低明度保证对比度
                linkColor = android.graphics.Color.HSVToColor(hsv);
            }
        }

        textView.setLinkTextColor(linkColor);

        SpannableStringBuilder builder = new SpannableStringBuilder(text);
        boolean foundLink = false;

        Matcher matcher = GENERAL_URL_PATTERN.matcher(text);
        while (matcher.find()) {
            final String url = matcher.group(0);
            int start = matcher.start();
            int end = matcher.end();
            builder.setSpan(createClickableSpan(url, linkColor), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            foundLink = true;
        }

        if (foundLink) {
            textView.setText(builder);
            textView.setMovementMethod(LinkMovementMethod.getInstance());
        }
    }

    private static ClickableSpan createClickableSpan(final String url, final int linkColor) {
        return new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                if (!handleUrl(widget.getContext(), url)) {
                    openFallbackBrowser(widget.getContext(), url);
                }
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                super.updateDrawState(ds);
                ds.setColor(linkColor);
                ds.setUnderlineText(true);
            }
        };
    }
}

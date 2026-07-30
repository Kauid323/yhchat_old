package com.nago8.chat.old.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.nago8.chat.old.PostDetailActivity;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InternalLinkUtils {

    // 匹配 yunhu://post-detail?id=xxx
    private static final Pattern YUNHU_SCHEME_PATTERN = Pattern.compile("yunhu://post-detail\\?(?:.*&)?id=([^&\\s]+)");

    // 匹配 www.yhchat.com/c/p/xxx 或 https://www.yhchat.com/c/p/xxx 或 yhchat.com/c/p/xxx
    private static final Pattern YHCHAT_POST_PATTERN = Pattern.compile("(?:https?://)?(?:[a-zA-Z0-9-]+\\.)?yhchat\\.com/c/p/([^/\\?\\s#]+)");

    /**
     * 判断并解析链接中的文章 ID。
     * @param url 待解析的链接
     * @return 文章 ID，若非内链或解析失败返回 null
     */
    public static String parsePostId(String url) {
        if (url == null || url.trim().isEmpty()) return null;
        String trimmedUrl = url.trim();

        // 1. 尝试匹配 yunhu://post-detail?id=文章id
        Matcher yunhuMatcher = YUNHU_SCHEME_PATTERN.matcher(trimmedUrl);
        if (yunhuMatcher.find()) {
            return yunhuMatcher.group(1);
        }

        // 2. 尝试使用 Uri 解析 query 参数
        if (trimmedUrl.startsWith("yunhu://")) {
            try {
                Uri uri = Uri.parse(trimmedUrl);
                String id = uri.getQueryParameter("id");
                if (id != null && !id.isEmpty()) {
                    return id;
                }
            } catch (Exception ignored) {}
        }

        // 3. 尝试匹配 www.yhchat.com/c/p/文章id
        Matcher yhchatMatcher = YHCHAT_POST_PATTERN.matcher(trimmedUrl);
        if (yhchatMatcher.find()) {
            return yhchatMatcher.group(1);
        }

        return null;
    }

    /**
     * 判断是否为文章内链
     */
    public static boolean isInternalLink(String url) {
        return parsePostId(url) != null;
    }

    /**
     * 处理内链点击。如果是文章内链则自动跳转到文章详情 Activity。
     * @param context Context
     * @param url 目标链接
     * @return 是否已作为内链成功处理
     */
    public static boolean handleUrl(Context context, String url) {
        if (context == null || url == null) return false;
        String postId = parsePostId(url);
        if (postId != null && !postId.isEmpty()) {
            Intent intent = new Intent(context, PostDetailActivity.class);
            intent.putExtra(PostDetailActivity.EXTRA_POST_ID, postId);
            context.startActivity(intent);
            return true;
        }
        return false;
    }

    /**
     * 扫描 TextView 中的文本，自动识别内链及常用 URL 并加上高亮点击效果
     */
    public static void processTextViewLinks(TextView textView) {
        processTextViewLinks(textView, false);
    }

    /**
     * 扫描 TextView 中的文本，自动识别内链及常用 URL 并加上高亮点击效果
     * @param textView 文本控件
     * @param isMine 是否为自己发送的消息（用于自适应高亮颜色）
     */
    public static void processTextViewLinks(TextView textView, boolean isMine) {
        if (textView == null) return;
        CharSequence text = textView.getText();
        if (text == null || text.length() == 0) return;

        // 自己发送的消息气泡为深色/彩色背景，使用浅蓝/纯白高亮；对方消息气泡为浅色背景，使用深蓝色高亮
        int linkColor = isMine ? 0xFFE0F2FE : 0xFF1A73E8;
        textView.setLinkTextColor(linkColor);

        SpannableStringBuilder builder = new SpannableStringBuilder(text);
        boolean foundLink = false;

        // 查找 yunhu:// 链接
        Matcher m1 = YUNHU_SCHEME_PATTERN.matcher(text);
        while (m1.find()) {
            final String url = m1.group(0);
            int start = m1.start();
            int end = m1.end();
            builder.setSpan(createClickableSpan(url, linkColor), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            foundLink = true;
        }

        // 查找 yhchat.com/c/p/ 链接
        Matcher m2 = YHCHAT_POST_PATTERN.matcher(text);
        while (m2.find()) {
            final String url = m2.group(0);
            int start = m2.start();
            int end = m2.end();
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
                    try {
                        String openUrl = url;
                        if (!openUrl.startsWith("http://") && !openUrl.startsWith("https://") && !openUrl.startsWith("yunhu://")) {
                            openUrl = "http://" + openUrl;
                        }
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(openUrl));
                        widget.getContext().startActivity(intent);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
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

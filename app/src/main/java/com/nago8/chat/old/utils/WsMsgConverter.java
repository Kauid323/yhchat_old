package com.nago8.chat.old.utils;

import android.content.Context;

import com.nago8.chat.old.R;
import com.nago8.chat.old.proto.Msg;
import com.nago8.chat.old.proto.chat_ws_go.WsMsg;

public class WsMsgConverter {

    public static Msg convert(WsMsg wsMsg, String myUserId) {
        if (wsMsg == null) return null;

        // 判断方向：发送者是自己则为 right
        String direction = "left";
        if (wsMsg.sender != null && wsMsg.sender.chat_id != null && wsMsg.sender.chat_id.equals(myUserId)) {
            direction = "right";
        }

        // 构建 Sender
        Msg.Sender.Builder senderBuilder = new Msg.Sender.Builder();
        if (wsMsg.sender != null) {
            senderBuilder.chat_id(wsMsg.sender.chat_id != null ? wsMsg.sender.chat_id : "");
            senderBuilder.chat_type(wsMsg.sender.chat_type);
            senderBuilder.name(wsMsg.sender.name != null ? wsMsg.sender.name : "");
            senderBuilder.avatar_url(wsMsg.sender.avatar_url != null ? wsMsg.sender.avatar_url : "");
        }

        // 构建 Content
        Msg.Content.Builder contentBuilder = new Msg.Content.Builder();
        if (wsMsg.content != null) {
            if (wsMsg.content.text != null) contentBuilder.text(wsMsg.content.text);
            if (wsMsg.content.image_url != null) contentBuilder.image_url(wsMsg.content.image_url);
            if (wsMsg.content.file_name != null) contentBuilder.file_name(wsMsg.content.file_name);
            if (wsMsg.content.file_url != null) contentBuilder.file_url(wsMsg.content.file_url);
            if (wsMsg.content.quote_msg_text != null) contentBuilder.quote_msg_text(wsMsg.content.quote_msg_text);
            if (wsMsg.content.sticker_url != null) contentBuilder.sticker_url(wsMsg.content.sticker_url);
            contentBuilder.file_size(wsMsg.content.file_size);
            if (wsMsg.content.video_url != null) contentBuilder.video_url(wsMsg.content.video_url);
            if (wsMsg.content.audio_url != null) contentBuilder.audio_url(wsMsg.content.audio_url);
            contentBuilder.audio_time(wsMsg.content.audio_time);
            contentBuilder.width(wsMsg.content.width);
            contentBuilder.height(wsMsg.content.height);
            if (wsMsg.content.post_id != null) contentBuilder.post_id(wsMsg.content.post_id);
            if (wsMsg.content.post_title != null) contentBuilder.post_title(wsMsg.content.post_title);
            if (wsMsg.content.post_content != null) contentBuilder.post_content(wsMsg.content.post_content);
            if (wsMsg.content.post_content_type != null) contentBuilder.post_content_type(wsMsg.content.post_content_type);
        }

        // 构建 Cmd
        Msg.Cmd cmd = null;
        if (wsMsg.cmd != null) {
            cmd = new Msg.Cmd.Builder()
                    .name(wsMsg.cmd.name != null ? wsMsg.cmd.name : "")
                    .type(0)
                    .build();
        }

        long editTime = wsMsg.edit_time;
        if (editTime <= 0 && wsMsg.cmd != null && wsMsg.cmd.name != null &&
                ("edit_message".equalsIgnoreCase(wsMsg.cmd.name) || "edit".equalsIgnoreCase(wsMsg.cmd.name))) {
            editTime = wsMsg.timestamp > 0 ? wsMsg.timestamp : System.currentTimeMillis();
        }

        return new Msg.Builder()
                .msg_id(wsMsg.msg_id != null ? wsMsg.msg_id : "")
                .sender(senderBuilder.build())
                .direction(direction)
                .content_type(wsMsg.content_type)
                .content(contentBuilder.build())
                .send_time(wsMsg.timestamp)
                .cmd(cmd)
                .msg_delete_time(wsMsg.delete_time)
                .quote_msg_id(wsMsg.quote_msg_id != null ? wsMsg.quote_msg_id : "")
                .msg_seq(wsMsg.msg_seq)
                .edit_time(editTime)
                .build();
    }

    /**
     * 智能融合已有消息 existing 与推送新消息 newMsg。
     * 当收到编辑消息推送时，如果推送缺失 sender、direction、content_type 等，自动安全继承旧消息元数据。
     */
    public static Msg mergeMsg(Msg existing, Msg newMsg) {
        if (existing == null) return newMsg;
        if (newMsg == null) return existing;

        Msg.Builder builder = newMsg.newBuilder();

        // 1. 严格继承原始发送时间与序列号（消息编辑绝不能改变其历史时间线与排序位置）
        if (existing.send_time > 0) {
            builder.send_time(existing.send_time);
        }
        if (existing.msg_seq > 0) {
            builder.msg_seq(existing.msg_seq);
        }

        // 2. 如果新消息没有 sender 或 sender 字段不完整，保留原有 sender
        if (newMsg.sender == null || android.text.TextUtils.isEmpty(newMsg.sender.chat_id)) {
            if (existing.sender != null) {
                builder.sender(existing.sender);
            }
        }

        // 3. 继承 direction（方向不变）
        if (existing.direction != null && !existing.direction.isEmpty()) {
            builder.direction(existing.direction);
        }

        // 4. 继承 content_type
        if (newMsg.content_type <= 0 && existing.content_type > 0) {
            builder.content_type(existing.content_type);
        }

        // 5. 继承引用消息 ID
        if (android.text.TextUtils.isEmpty(newMsg.quote_msg_id) && !android.text.TextUtils.isEmpty(existing.quote_msg_id)) {
            builder.quote_msg_id(existing.quote_msg_id);
        }

        // 6. 合并 content 内容（更新新文本，保留原有其他字段）
        if (newMsg.content == null) {
            builder.content(existing.content);
        } else if (existing.content != null) {
            Msg.Content.Builder contentBuilder = newMsg.content.newBuilder();
            if (android.text.TextUtils.isEmpty(newMsg.content.text) && !android.text.TextUtils.isEmpty(existing.content.text)) {
                contentBuilder.text(existing.content.text);
            }
            if (android.text.TextUtils.isEmpty(newMsg.content.image_url) && !android.text.TextUtils.isEmpty(existing.content.image_url)) {
                contentBuilder.image_url(existing.content.image_url);
            }
            if (android.text.TextUtils.isEmpty(newMsg.content.file_name) && !android.text.TextUtils.isEmpty(existing.content.file_name)) {
                contentBuilder.file_name(existing.content.file_name);
            }
            if (android.text.TextUtils.isEmpty(newMsg.content.file_url) && !android.text.TextUtils.isEmpty(existing.content.file_url)) {
                contentBuilder.file_url(existing.content.file_url);
            }
            if (android.text.TextUtils.isEmpty(newMsg.content.quote_msg_text) && !android.text.TextUtils.isEmpty(existing.content.quote_msg_text)) {
                contentBuilder.quote_msg_text(existing.content.quote_msg_text);
            }
            builder.content(contentBuilder.build());
        }

        // 7. 设值 edit_time
        long editTime = newMsg.edit_time;
        if (editTime <= 0) {
            editTime = existing.edit_time > 0 ? existing.edit_time : System.currentTimeMillis();
        }
        builder.edit_time(editTime);

        return builder.build();
    }

    /**
     * 将 WsMsg 转为会话列表预览文本（不含发送者名）。
     * 按 content_type 区分消息类型，便于一眼看出是什么类型的消息。
     *
     * content_type: 1-文本, 2-图片, 3-markdown, 4-文件, 5-表单,
     *               6-文章, 7-表情, 8-html, 11-语音, 13-语音通话
     */
    public static String toPreviewText(WsMsg wsMsg, Context ctx) {
        if (wsMsg == null) {
            return ctx.getString(R.string.preview_unknown);
        }

        if (wsMsg.delete_time > 0) {
            long deleteTime = wsMsg.delete_time;
            long tsMs = deleteTime > 100000000000L ? deleteTime : deleteTime * 1000L;
            java.util.Calendar nowCal = java.util.Calendar.getInstance();
            java.util.Calendar msgCal = java.util.Calendar.getInstance();
            msgCal.setTimeInMillis(tsMs);

            String pattern = msgCal.get(java.util.Calendar.YEAR) < nowCal.get(java.util.Calendar.YEAR)
                    ? "yyyy年M月d日 HH:mm" : "M月d日 HH:mm";

            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault());
            return "该消息已于 " + sdf.format(msgCal.getTime()) + " 撤回";
        }

        if (wsMsg.content == null) {
            return ctx.getString(R.string.preview_unknown);
        }

        String text = wsMsg.content.text != null ? wsMsg.content.text : "";
        String fileName = wsMsg.content.file_name != null ? wsMsg.content.file_name : "";
        String postTitle = wsMsg.content.post_title != null ? wsMsg.content.post_title : "";

        switch (wsMsg.content_type) {
            case 1: // 文本
                return text.length() > 0 ? text : ctx.getString(R.string.preview_unknown);
            case 2: // 图片
                return ctx.getString(R.string.preview_image);
            case 3: // markdown
                return ctx.getString(R.string.preview_markdown);
            case 4: // 文件
                return ctx.getString(R.string.preview_file, fileName);
            case 5: // 表单
                return ctx.getString(R.string.preview_form);
            case 6: // 文章
                return postTitle.length() > 0 ? postTitle : ctx.getString(R.string.preview_article);
            case 7: // 表情
                return ctx.getString(R.string.preview_sticker);
            case 8: // html
                return ctx.getString(R.string.preview_html);
            case 11: // 语音
                return ctx.getString(R.string.preview_voice);
            case 13: // 语音通话
                return ctx.getString(R.string.preview_call);
            default:
                if (wsMsg.content.video_url != null && wsMsg.content.video_url.length() > 0) {
                    return ctx.getString(R.string.preview_video);
                }
                return text.length() > 0 ? text : ctx.getString(R.string.preview_unknown);
        }
    }
}

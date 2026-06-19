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
        }

        // 构建 Cmd
        Msg.Cmd cmd = null;
        if (wsMsg.cmd != null) {
            cmd = new Msg.Cmd.Builder()
                    .name(wsMsg.cmd.name != null ? wsMsg.cmd.name : "")
                    .type(0)
                    .build();
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
                .edit_time(wsMsg.edit_time)
                .build();
    }

    /**
     * 将 WsMsg 转为会话列表预览文本（不含发送者名）。
     * 按 content_type 区分消息类型，便于一眼看出是什么类型的消息。
     *
     * content_type: 1-文本, 2-图片, 3-markdown, 4-文件, 5-表单,
     *               6-文章, 7-表情, 8-html, 11-语音, 13-语音通话
     */
    public static String toPreviewText(WsMsg wsMsg, Context ctx) {
        if (wsMsg == null || wsMsg.content == null) {
            return ctx.getString(R.string.preview_unknown);
        }

        String text = wsMsg.content.text != null ? wsMsg.content.text : "";
        String fileName = wsMsg.content.file_name != null ? wsMsg.content.file_name : "";

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
                return ctx.getString(R.string.preview_article);
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

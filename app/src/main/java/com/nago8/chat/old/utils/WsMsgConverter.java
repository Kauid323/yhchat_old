package com.nago8.chat.old.utils;

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
}

package com.nago8.chat.old.model;

import com.nago8.chat.old.proto.Msg;

import java.util.ArrayList;
import java.util.List;

public class MessageGroup {
    private static final long GROUP_WINDOW_MS = 3 * 60 * 1000L;

    public final boolean mine;
    public final String senderId;
    public final String senderName;
    public final String avatarUrl;
    public final long firstSendTime;
    public final List<Msg> messages = new ArrayList<>();

    private MessageGroup(Msg msg) {
        this.mine = isMine(msg);
        this.senderId = msg.sender == null ? "" : msg.sender.chat_id;
        this.senderName = msg.sender == null ? "" : msg.sender.name;
        this.avatarUrl = msg.sender == null ? "" : msg.sender.avatar_url;
        this.firstSendTime = msg.send_time;
        this.messages.add(msg);
    }

    public static List<MessageGroup> fromMessages(List<Msg> source) {
        List<MessageGroup> groups = new ArrayList<>();
        if (source == null) return groups;

        MessageGroup current = null;
        for (Msg msg : source) {
            if (msg == null) continue;
            if (current == null || !current.canAppend(msg)) {
                current = new MessageGroup(msg);
                groups.add(current);
            } else {
                current.messages.add(msg);
            }
        }
        return groups;
    }

    private boolean canAppend(Msg msg) {
        String id = msg.sender == null ? "" : msg.sender.chat_id;
        return mine == isMine(msg) && senderId.equals(id) && Math.abs(msg.send_time - firstSendTime) <= GROUP_WINDOW_MS;
    }

    public static boolean isMine(Msg msg) {
        return msg != null && "right".equalsIgnoreCase(msg.direction);
    }
}

package com.nago8.chat.old.model;

import android.content.Context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EmojiData {

    // 常用 Unicode Emoji 列表
    public static final List<String> UNICODE_EMOJIS = Arrays.asList(
            "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇",
            "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚",
            "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🤩",
            "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣",
            "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬",
            "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🤗",
            "🤔", "🤭", "🤫", "🤥", "😶", "😐", "😑", "😬", "🙄", "😯",
            "😦", "😧", "😮", "😲", "🥱", "😴", "🤤", "😪", "😵", "🤐",
            "🥴", "🤢", "🤮", "🤧", "😷", "🤒", "🤕", "🤑", "🤠", "😈",
            "👿", "👹", "👺", "🤡", "💩", "👻", "💀", "☠️", "👽", "👾",
            "🤖", "🎃", "😺", "😸", "😹", "😻", "😼", "😽", "🙀", "😿",
            "😾", "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎",
            "💔", "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟",
            "👍", "👎", "👊", "✊", "🤛", "🤜", "🤞", "✌️", "🤟", "🤘",
            "👌", "🤏", "👈", "👉", "👆", "👇", "☝️", "✋", "🤚", "🖐️",
            "🖖", "👋", "🤙", "💪", "🦾", "🖕", "✍️", "🙏", "🦶", "🦵",
            "🎉", "🎊", "🎈", "🎁", "🏆", "🥇", "🥈", "🥉", "🏅", "🎖️",
            "🔥", "✨", "🌟", "💫", "💥", "💯", "💢", "💬", "👁️‍🗨️", "🗯️"
    );

    /**
     * 获取 assets/fengtwemoji 目录下的所有内置 Twemoji 标识（如 "[.偷看]"）
     */
    public static List<String> getTwemojiTokens(Context context) {
        List<String> list = new ArrayList<>();
        try {
            String[] files = context.getAssets().list("fengtwemoji");
            if (files != null) {
                for (String file : files) {
                    if (file.endsWith(".svg")) {
                        String token = file.substring(0, file.length() - 4).trim();
                        // 补齐方括号规范
                        if (token.startsWith("[.") && token.endsWith("]")) {
                            list.add(token);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return list;
    }
}

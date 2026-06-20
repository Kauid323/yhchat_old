package com.nago8.chat.old.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.nago8.chat.old.ChatActivity;
import com.nago8.chat.old.R;

public class NotificationHelper {

    private static final String CHANNEL_ID = "chat_messages";
    private static final int NOTIFICATION_ID_BASE = 1000;

    private static boolean channelCreated = false;

    /**
     * 创建通知渠道（Android 8.0+ 必须）
     */
    public static void createChannel(Context ctx) {
        if (channelCreated) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    ctx.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription(ctx.getString(R.string.notification_channel_desc));
            channel.enableVibration(true);
            channel.enableLights(true);
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
        channelCreated = true;
    }

    /**
     * 发送消息通知。
     *
     * @param ctx      上下文
     * @param chatId   会话 ID
     * @param chatType 会话类型（1-用户，2-群聊，3-机器人）
     * @param title    通知标题（发送者名称或群名）
     * @param content  通知内容（消息预览）
     */
    public static void showMessageNotification(Context ctx, String chatId, int chatType,
                                                String title, String content) {
        createChannel(ctx);

        Intent intent = new Intent(ctx, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_CHAT_ID, chatId);
        intent.putExtra(ChatActivity.EXTRA_CHAT_TYPE, chatType);
        intent.putExtra(ChatActivity.EXTRA_CHAT_NAME, title);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                ctx,
                chatId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT |
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);

        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            // 用 chatId.hashCode() 作为通知 ID，同一会话只保留最新一条
            nm.notify(chatId.hashCode(), builder.build());
        }
    }

    /**
     * 取消指定会话的通知。
     */
    public static void cancelNotification(Context ctx, String chatId) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null && chatId != null) {
            nm.cancel(chatId.hashCode());
        }
    }
}

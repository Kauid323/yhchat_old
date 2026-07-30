package com.nago8.chat.old.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.graphics.Bitmap;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;

import com.nago8.chat.old.App;
import com.nago8.chat.old.ChatActivity;
import com.nago8.chat.old.R;

public class NotificationHelper {

    private static final String CHANNEL_ID = "chat_messages";
    private static final int REQUEST_NOTIF_PERMISSION = 3001;

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
     * 检查是否有通知权限（Android 13+ 需要运行时权限）。
     */
    public static boolean hasNotificationPermission(Context ctx) {
        if (Build.VERSION.SDK_INT >= 33) {
            return ContextCompat.checkSelfPermission(ctx,
                    android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
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
        // 不带头像版本
        showMessageNotification(ctx, chatId, chatType, title, content, null);
    }

    /**
     * 发送消息通知（带会话头像）。
     *
     * @param avatarUrl 会话头像 URL，null 则不显示大图标
     */
    public static void showMessageNotification(Context ctx, String chatId, int chatType,
                                                String title, String content, String avatarUrl) {
        if (App.isAppInForeground()) return;
        // Android 13+ 没有通知权限就不发
        if (!hasNotificationPermission(ctx)) return;

        createChannel(ctx);

        Intent intent = new Intent(ctx, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_CHAT_ID, chatId);
        intent.putExtra(ChatActivity.EXTRA_CHAT_TYPE, chatType);
        intent.putExtra(ChatActivity.EXTRA_CHAT_NAME, title);
        // NEW_TASK: 通知启动需要新任务栈
        // CLEAR_TOP: 如果 ChatActivity 已存在则清掉上面的回到它
        // TASK_ON_HOME: 确保 HomeActivity 在 ChatActivity 下面，返回能回到主页
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_TASK_ON_HOME);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                ctx,
                chatId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT |
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));

        final NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                // 用系统自带图标，兼容安卓4（vector drawable 在安卓4通知里会崩溃）
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);

        final NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        final int notifId = chatId.hashCode();

        if (avatarUrl != null && avatarUrl.length() > 0) {
            // 异步加载头像，加载完成后发通知
            GlideUrl glideUrl;
            if (avatarUrl.contains(".jwznb.com")) {
                glideUrl = new GlideUrl(avatarUrl, new LazyHeaders.Builder()
                        .addHeader("Referer", "http://myapp.jwznb.com")
                        .build());
            } else {
                glideUrl = new GlideUrl(avatarUrl);
            }

            Glide.with(ctx.getApplicationContext())
                    .asBitmap()
                    .load(glideUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .circleCrop()
                    .into(new SimpleTarget<Bitmap>(96, 96) {
                        @Override
                        public void onResourceReady(Bitmap resource, Transition<? super Bitmap> transition) {
                            builder.setLargeIcon(resource);
                            if (!App.isAppInForeground() && nm != null) nm.notify(notifId, builder.build());
                        }

                        @Override
                        public void onLoadFailed(android.graphics.drawable.Drawable errorDrawable) {
                            // 头像加载失败，直接发不带大图标的通知
                            if (!App.isAppInForeground() && nm != null) nm.notify(notifId, builder.build());
                        }
                    });
        } else {
            if (!App.isAppInForeground() && nm != null) nm.notify(notifId, builder.build());
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

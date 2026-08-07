package com.nago8.chat.old.utils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class TimeUtils {

    public static String formatChatTime(long timestampMs) {
        if (timestampMs <= 0) return "";
        if (timestampMs < 100000000000L) {
            timestampMs *= 1000L;
        }

        long now = System.currentTimeMillis();
        long diff = now - timestampMs;

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestampMs);
        int year = calendar.get(Calendar.YEAR);
        int dayOfYear = calendar.get(Calendar.DAY_OF_YEAR);

        Calendar nowCalendar = Calendar.getInstance();
        int nowYear = nowCalendar.get(Calendar.YEAR);
        int nowDayOfYear = nowCalendar.get(Calendar.DAY_OF_YEAR);

        if (nowYear == year && nowDayOfYear == dayOfYear) {
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(timestampMs));
        }

        long oneDayMs = 24 * 60 * 60 * 1000L;
        if (diff > 0 && diff < 4 * oneDayMs) {
            int daysAgo = (int) (diff / oneDayMs);
            if (daysAgo >= 1 && daysAgo <= 3) {
                return daysAgo + "天前";
            }
        }

        if (nowYear == year) {
            return new SimpleDateFormat("MM月dd日", Locale.getDefault()).format(new Date(timestampMs));
        }

        return new SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault()).format(new Date(timestampMs));
    }

    public static String formatMessageTime(long timestampMs) {
        if (timestampMs <= 0) return "";
        if (timestampMs < 100000000000L) {
            timestampMs *= 1000L;
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestampMs);
        int year = calendar.get(Calendar.YEAR);
        int dayOfYear = calendar.get(Calendar.DAY_OF_YEAR);

        Calendar nowCalendar = Calendar.getInstance();
        int nowYear = nowCalendar.get(Calendar.YEAR);
        int nowDayOfYear = nowCalendar.get(Calendar.DAY_OF_YEAR);

        if (nowYear == year && nowDayOfYear == dayOfYear) {
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(timestampMs));
        } else if (nowYear == year) {
            return new SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault()).format(new Date(timestampMs));
        } else {
            return new SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.getDefault()).format(new Date(timestampMs));
        }
    }
}

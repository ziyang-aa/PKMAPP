package com.example.pkmapp.study;

import java.util.Locale;
import java.util.Calendar;

public final class StudyFormat {

    private StudyFormat() {}

    public static String clock(long millis) {
        long seconds = millis / 1_000;
        return String.format(
            Locale.ROOT,
            "%02d:%02d:%02d",
            seconds / 3600,
            (seconds / 60) % 60,
            seconds % 60
        );
    }

    public static String duration(long millis) {
        long seconds = millis / 1_000;
        if (seconds < 60) return seconds + "秒";
        long minutes = seconds / 60;
        return minutes < 60
            ? minutes + "分钟"
            : minutes / 60 + "小时" + (minutes % 60 == 0 ? "" : (minutes % 60) + "分钟");
    }

    static long parseClock(String value) {
        String input = value.trim().replace('：', ':');
        if (!input.matches("[0-9]{1,3}:[0-5]?[0-9]:[0-5]?[0-9]"))
            throw new IllegalArgumentException("请输入时:分:秒，例如 00:35:00");
        String[] parts = input.split(":");
        long seconds = Integer.parseInt(parts[0]) * 3600L + Integer.parseInt(parts[1]) * 60L
            + Integer.parseInt(parts[2]);
        if (seconds == 0) throw new IllegalArgumentException("学习时长至少为 1 秒");
        return seconds * 1000L;
    }

    static Calendar day(String value) {
        String date = StudyPlan.date(value);
        Calendar result = Calendar.getInstance();
        if (date.isEmpty()) return result;
        String[] parts = date.split("-");
        result.clear();
        result.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]));
        return result;
    }

    static long deadlineReminder(String day) {
        if (day.isEmpty()) return 0;
        Calendar at = day(day);
        at.add(Calendar.DATE, -1);
        at.set(Calendar.HOUR_OF_DAY, 19);
        return at.getTimeInMillis();
    }
}

package com.example.pkmapp.study;

import java.util.Locale;

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
}

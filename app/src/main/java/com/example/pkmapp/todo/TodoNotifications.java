package com.example.pkmapp.todo;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.example.pkmapp.MainActivity;
import com.example.pkmapp.R;
import java.util.ArrayList;
import java.util.List;

/** Standalone notification scheduler and receiver for independent To-Do reminders. */
public final class TodoNotifications extends BroadcastReceiver {
    public static final String OPEN_TODO = "com.example.pkmapp.todo.OPEN_TODO";
    private static final String TRIGGER = "com.example.pkmapp.todo.TRIGGER";
    private static final String CHANNEL_ID = "forest_todo_reminders";
    private static final int NOTIFY_ID = 8201;
    private static final int FLAGS = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;

    public static boolean permitted(Context context) {
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    public static void refresh(Context context, TodoStore store) {
        try {
            schedule(context, store, 0, false);
        } catch (SecurityException | IllegalStateException error) {
            Log.w("TodoNotifications", "Notification scheduling failed", error);
        }
    }

    private static void schedule(Context context, TodoStore store, long delivered, boolean reschedule) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        if (alarms == null) return;
        SharedPreferences prefs = context.getSharedPreferences("todo_notifications", Context.MODE_PRIVATE);
        long previous = prefs.getLong("nextAlarm", 0);
        long now = System.currentTimeMillis();
        String today = TodoStore.dayKey(now);
        long earliest = Long.MAX_VALUE;
        if (permitted(context) && store.remindEnabled()) {
            for (TodoStore.TodoItem item : store.pending()) {
                if (!item.remind || item.day.isEmpty()) continue;
                long at = item.reminderAt(store.defaultRemindTime());
                if (at != 0 && at != delivered && (at > now || (at > 0 && at == previous
                    && item.day.compareTo(today) >= 0))) {
                    earliest = Math.min(earliest, at);
                }
            }
        }
        long next = earliest == Long.MAX_VALUE ? 0 : earliest;
        int existingFlags = PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE;
        if (previous == next && !reschedule
            && (next == 0 || alarmIntent(context, next, existingFlags) != null)) return;
        if (previous != 0 && previous != next) {
            PendingIntent old = alarmIntent(context, previous, existingFlags);
            if (old != null) {
                alarms.cancel(old);
                old.cancel();
            }
        }
        if (next != 0) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, alarmIntent(context, next, FLAGS));
        }
        prefs.edit().putLong("nextAlarm", next).apply();
    }

    private static PendingIntent alarmIntent(Context context, long trigger, int flags) {
        Intent intent = new Intent(context, TodoNotifications.class)
            .setAction(TRIGGER)
            .setData(new Uri.Builder().scheme("todo").authority("due").appendPath(Long.toString(trigger)).build())
            .putExtra("when", trigger);
        return PendingIntent.getBroadcast(context, 0, intent, flags);
    }

    private static PendingIntent openTodoIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class)
            .setAction(OPEN_TODO)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, 0, intent, FLAGS);
    }

    private static void ensureChannel(NotificationManager manager) {
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, "待办截止提醒", NotificationManager.IMPORTANCE_DEFAULT));
        }
    }

    public static boolean sendImmediate(Context context, String title, String detail) {
        if (!permitted(context)) return false;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return false;
        ensureChannel(manager);
        manager.notify(NOTIFY_ID, new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_list)
            .setContentTitle(title)
            .setContentText(detail)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(openTodoIntent(context))
            .build());
        return true;
    }

    private static void showDue(Context context, TodoStore store, long trigger) {
        if (!permitted(context) || !store.remindEnabled()) return;
        String today = TodoStore.dayKey(System.currentTimeMillis());
        List<String> lines = new ArrayList<>();
        for (TodoStore.TodoItem item : store.pending()) {
            if (!item.remind || item.day.compareTo(today) < 0) continue;
            if (item.reminderAt(store.defaultRemindTime()) == trigger) {
                String due = item.day + (item.dueTime.isEmpty() ? "" : " " + item.dueTime);
                lines.add(item.title + "（截止 " + due + "）");
            }
        }
        if (lines.isEmpty()) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        ensureChannel(manager);
        NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
        for (int i = 0; i < Math.min(5, lines.size()); i++) style.addLine(lines.get(i));
        manager.notify(NOTIFY_ID, new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_list)
            .setContentTitle("待办即将截止")
            .setContentText(lines.get(0))
            .setStyle(style)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(openTodoIntent(context))
            .build());
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        TodoStore store = TodoStore.get(context);
        String action = intent.getAction();
        try {
            if (TRIGGER.equals(action)) {
                long trigger = intent.getLongExtra("when", -1);
                showDue(context, store, trigger);
                schedule(context, store, trigger, false);
            } else {
                schedule(context, store, 0, true);
            }
        } catch (IllegalArgumentException | IllegalStateException | SecurityException error) {
            Log.w("TodoNotifications", "Could not process todo notification", error);
        }
    }
}

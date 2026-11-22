package com.example.pkmapp.study;

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

/** Timers derive their duration from the persisted clock; the notification provides controls. */
public final class StudyNotifications extends BroadcastReceiver {
    public static final String OPEN_TIMER = "com.example.pkmapp.study.OPEN_TIMER";
    public static final String FINISH_TIMER = "com.example.pkmapp.study.FINISH_TIMER";
    public static final String OPEN_AGENDA = "com.example.pkmapp.study.OPEN_AGENDA";
    public static final String SESSION = "study_session";
    private static final String PAUSE = "com.example.pkmapp.study.PAUSE";
    private static final String RESUME = "com.example.pkmapp.study.RESUME";
    private static final String DUE = "com.example.pkmapp.study.DUE";
    private static final String TIMER_CHANNEL = "study_timer";
    private static final String DEADLINE_CHANNEL = "study_deadlines";
    private static final int TIMER_ID = 8101;
    private static final int DEADLINE_ID = 8102;
    private static final int FLAGS = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;

    static boolean permitted(Context context) {
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    static void refresh(Context context, StudyStore store) {
        try {
            refreshTimer(context, store);
            scheduleDeadline(context, store.notebook(), 0, false);
        } catch (SecurityException | IllegalStateException error) {
            Log.w("StudyNotifications", "Notification access changed", error);
        }
    }

    private static void refreshTimer(Context context, StudyStore store) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        StudyNotebook notebook = store.notebook();
        if (!notebook.hasSession() || !permitted(context)) {
            manager.cancel(TIMER_ID);
            return;
        }
        channel(manager, TIMER_CHANNEL, "学习计时", NotificationManager.IMPORTANCE_LOW);
        String id = notebook.sessionId();
        boolean running = notebook.running();
        long elapsed = store.elapsedMillis();
        String title = notebook.activeSubject().title + (running ? " · 正在专注" : " · 已暂停");
        Intent control = new Intent(context, StudyNotifications.class).setAction(running ? PAUSE : RESUME)
            .setData(identity(id)).putExtra(SESSION, id);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, TIMER_CHANNEL)
            .setSmallIcon(R.drawable.ic_study_book).setContentTitle(title)
            .setContentIntent(activity(context, OPEN_TIMER, id))
            .setOnlyAlertOnce(true).setOngoing(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setShowWhen(running).setUsesChronometer(running).setWhen(System.currentTimeMillis() - elapsed)
            .addAction(0, running ? "暂停" : "继续", PendingIntent.getBroadcast(context, 0, control, FLAGS))
            .addAction(0, "结束", activity(context, FINISH_TIMER, id));
        if (!running) builder.setContentText("已专注 " + StudyFormat.duration(elapsed));
        manager.notify(TIMER_ID, builder.build());
    }

    private static Uri identity(String id) {
        return new Uri.Builder().scheme("study").authority("focus").appendPath(id).build();
    }

    private static PendingIntent activity(Context context, String action, String id) {
        Intent intent = new Intent(context, MainActivity.class).setAction(action).setData(identity(id))
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(SESSION, id);
        return PendingIntent.getActivity(context, 0, intent, FLAGS);
    }

    private static void channel(NotificationManager manager, String id, String title, int importance) {
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel(id, title, importance));
    }

    private static void scheduleDeadline(Context context, StudyNotebook notebook, long delivered, boolean reschedule) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        SharedPreferences prefs = context.getSharedPreferences("study_notifications", Context.MODE_PRIVATE);
        long previous = prefs.getLong("deadlineAlarm", 0);
        long now = System.currentTimeMillis();
        String today = StudyNotebook.dayKey(now);
        long earliest = Long.MAX_VALUE;
        if (permitted(context)) {
            for (StudyPlan plan : notebook.plans()) {
                if (plan.archived != 0) continue;
                if (plan.remindDeadlines) {
                    for (StudySubject subject : plan.books()) {
                        long at = StudyFormat.deadlineReminder(subject.due);
                        if (at != delivered && (at > now || (at > 0 && at == previous
                            && subject.due.compareTo(today) >= 0))) earliest = Math.min(earliest, at);
                    }
                }
                for (StudyWork.Task task : notebook.pendingTasks(plan.id)) {
                    if (!task.remind && !plan.remindDeadlines) continue;
                    long at = task.reminderAt(plan.remindTime);
                    if (at != 0 && at != delivered && (at > now || (at > 0 && at == previous
                        && task.day.compareTo(today) >= 0))) earliest = Math.min(earliest, at);
                }
            }
        }
        long next = earliest == Long.MAX_VALUE ? 0 : earliest;
        int existingFlags = PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE;
        if (previous == next && !reschedule
            && (next == 0 || deadlineIntent(context, next, existingFlags) != null)) return;
        if (previous != 0 && previous != next) {
            PendingIntent old = deadlineIntent(context, previous, existingFlags);
            if (old != null) {
                alarms.cancel(old);
                old.cancel();
            }
        }
        if (next != 0) alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, deadlineIntent(context, next, FLAGS));
        prefs.edit().putLong("deadlineAlarm", next).apply();
    }

    private static PendingIntent deadlineIntent(Context context, long trigger, int flags) {
        Intent intent = new Intent(context, StudyNotifications.class).setAction(DUE)
            .setData(identity(Long.toString(trigger))).putExtra("when", trigger);
        return PendingIntent.getBroadcast(context, 0, intent, flags);
    }

    private static void showDeadlines(Context context, StudyNotebook notebook, long trigger) {
        if (!permitted(context)) return;
        String today = StudyNotebook.dayKey(System.currentTimeMillis());
        List<String> titles = new ArrayList<>();
        String planId = "";
        for (StudyPlan plan : notebook.plans()) {
            if (plan.archived != 0) continue;
            if (plan.remindDeadlines) {
                for (StudySubject subject : plan.books()) {
                    if (subject.due.compareTo(today) >= 0 && StudyFormat.deadlineReminder(subject.due) == trigger) {
                        titles.add(subject.title + " · " + subject.due);
                        if (planId.isEmpty()) planId = plan.id;
                    }
                }
            }
            for (StudyWork.Task task : notebook.pendingTasks(plan.id)) {
                if ((!task.remind && !plan.remindDeadlines) || task.day.compareTo(today) < 0) continue;
                if (task.reminderAt(plan.remindTime) == trigger) {
                    String dueInfo = task.day + (task.dueTime.isEmpty() ? "" : " " + task.dueTime);
                    titles.add("待办 · " + task.title + "（截止 " + dueInfo + "）");
                    if (planId.isEmpty()) planId = plan.id;
                }
            }
        }
        if (titles.isEmpty()) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        channel(manager, DEADLINE_CHANNEL, "考试与截止提醒", NotificationManager.IMPORTANCE_DEFAULT);
        NotificationCompat.InboxStyle list = new NotificationCompat.InboxStyle();
        for (int i = 0; i < Math.min(5, titles.size()); i++) list.addLine(titles.get(i));
        manager.notify(DEADLINE_ID, new NotificationCompat.Builder(context, DEADLINE_CHANNEL)
            .setSmallIcon(R.drawable.ic_study_book).setContentTitle("待办与截止提醒")
            .setContentText(titles.get(0)).setStyle(list).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true).setContentIntent(activity(context, OPEN_AGENDA, planId)).build());
    }

    static boolean sendImmediateReminder(Context context, String planId, String title, String detail) {
        if (!permitted(context)) return false;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        channel(manager, DEADLINE_CHANNEL, "考试与截止提醒", NotificationManager.IMPORTANCE_DEFAULT);
        manager.notify(DEADLINE_ID, new NotificationCompat.Builder(context, DEADLINE_CHANNEL)
            .setSmallIcon(R.drawable.ic_study_book)
            .setContentTitle(title)
            .setContentText(detail)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(activity(context, OPEN_AGENDA, planId == null ? "" : planId))
            .build());
        return true;
    }

    public static boolean selectTimerPlan(Context context, String sessionId) {
        StudyStore store = StudyStore.get(context);
        StudyNotebook notebook = store.notebook();
        if (sessionId == null || sessionId.isEmpty() || !sessionId.equals(notebook.sessionId())) return false;
        if (!notebook.currentHasSession()) {
            store.update(n -> { n.switchPlan(n.activePlanId()); return null; });
        }
        return true;
    }

    public static void selectAgendaPlan(Context context, String id) {
        StudyStore store = StudyStore.get(context);
        StudyPlan plan = store.notebook().plan(id);
        if (plan != null && plan.archived == 0) store.update(n -> { n.switchPlan(id); return null; });
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        StudyStore store = StudyStore.get(context);
        StudyNotebook notebook = store.notebook();
        String action = intent.getAction();
        try {
            if (DUE.equals(action)) {
                long trigger = intent.getLongExtra("when", -1);
                showDeadlines(context, notebook, trigger);
                scheduleDeadline(context, notebook, trigger, false);
            } else if (PAUSE.equals(action) || RESUME.equals(action)) {
                if (!notebook.hasSession() || !notebook.sessionId().equals(intent.getStringExtra(SESSION))) return;
                if (PAUSE.equals(action) && notebook.running()) store.pause();
                if (RESUME.equals(action) && !notebook.running()) store.resume();
            } else {
                refreshTimer(context, store);
                scheduleDeadline(context, notebook, 0, true);
            }
        } catch (IllegalArgumentException | IllegalStateException | SecurityException error) {
            Log.w("StudyNotifications", "Could not apply study notification action", error);
        }
    }
}

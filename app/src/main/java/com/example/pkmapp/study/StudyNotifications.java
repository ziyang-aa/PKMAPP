package com.example.pkmapp.study;

import android.app.AlarmManager;
import android.app.Notification;
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
import com.example.pkmapp.core.work.ReminderRescheduleWorker;
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
    private static final String LEGACY_TIMER_CHANNEL = "study_timer";
    private static final String TIMER_CHANNEL = "study_timer_live";
    private static final String DEADLINE_CHANNEL = "study_deadlines";
    static final int TIMER_ID = 8101;
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

    static Notification buildTimerNotification(Context context, StudyStore store) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        StudyNotebook notebook = store.notebook();
        if (manager == null || !notebook.hasSession() || !permitted(context)) {
            return null;
        }
        ensureTimerChannel(manager);
        String id = notebook.sessionId();
        boolean running = notebook.running();
        long elapsed = store.elapsedMillis();
        int goalMin = notebook.activeGoal();
        StudyPlan activePlan = notebook.plan(notebook.activePlanId());
        StudyWork.Task activeTask = notebook.task(notebook.activeTaskId());
        String subjectTitle = notebook.activeSubject().title;
        String title = subjectTitle + (running ? " · 正在专注" : " · 已暂停");
        String elapsedText = formatNotificationElapsed(elapsed);
        StringBuilder detail = new StringBuilder();
        detail.append("已学习 ").append(elapsedText);
        if (!running) detail.append("（已暂停）");
        int progressPermille = -1;
        if (goalMin > 0) {
            long goalMillis = goalMin * 60_000L;
            progressPermille = (int) Math.min(1000L, Math.max(0L, (elapsed * 1000L) / goalMillis));
            int percent = Math.min(100, (int) Math.round((elapsed * 100.0) / goalMillis));
            if (elapsed >= goalMillis) {
                detail.append(" · 已达成 ").append(goalMin).append(" 分钟目标");
            } else {
                detail.append(" / 目标 ").append(goalMin).append(" 分钟（").append(percent).append("%）");
            }
        }
        if (activeTask != null && activeTask.title != null && !activeTask.title.isEmpty()) {
            detail.append(" · ").append(activeTask.title);
        }

        Intent control = new Intent(context, StudyNotifications.class).setAction(running ? PAUSE : RESUME)
            .setData(identity(id)).putExtra(SESSION, id);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, TIMER_CHANNEL)
            .setSmallIcon(R.drawable.ic_study_book)
            .setContentTitle(title)
            .setContentText(detail.toString())
            .setContentIntent(activity(context, OPEN_TIMER, id))
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(running)
            .setUsesChronometer(running)
            .setWhen(System.currentTimeMillis() - elapsed)
            .addAction(0, running ? "暂停" : "继续", PendingIntent.getBroadcast(context, 0, control, FLAGS))
            .addAction(0, "结束", activity(context, FINISH_TIMER, id));
        if (progressPermille >= 0) {
            builder.setProgress(1000, progressPermille, false);
        }
        if (activePlan != null && activePlan.title != null && !activePlan.title.isEmpty()) {
            builder.setSubText(activePlan.title);
        }
        return builder.build();
    }

    private static String formatNotificationElapsed(long elapsedMillis) {
        long totalSeconds = Math.max(0L, elapsedMillis / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        if (hours > 0) {
            return minutes > 0 ? hours + " 小时 " + minutes + " 分钟" : hours + " 小时";
        }
        if (minutes > 0) {
            return minutes + " 分钟";
        }
        return "不足 1 分钟";
    }

    private static void ensureTimerChannel(NotificationManager manager) {
        if (Build.VERSION.SDK_INT < 26) return;
        manager.deleteNotificationChannel(LEGACY_TIMER_CHANNEL);
        NotificationChannel ch = new NotificationChannel(
            TIMER_CHANNEL,
            "专注计时（锁屏可见）",
            NotificationManager.IMPORTANCE_DEFAULT
        );
        ch.setDescription("学习专注期间在通知栏和锁屏界面实时显示已学习时长");
        ch.setSound(null, null);
        ch.enableVibration(false);
        ch.enableLights(false);
        ch.setShowBadge(false);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        manager.createNotificationChannel(ch);
    }

    private static void refreshTimer(Context context, StudyStore store) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        Notification notification = buildTimerNotification(context, store);
        if (notification == null) {
            if (manager != null) manager.cancel(TIMER_ID);
            FocusTimerService.sync(context, store);
            return;
        }
        if (manager != null) manager.notify(TIMER_ID, notification);
        FocusTimerService.sync(context, store);
    }

    public static void rescheduleAll(Context context) {
        try {
            StudyStore store = StudyStore.get(context);
            refreshTimer(context, store);
            scheduleDeadline(context, store.notebook(), 0, true);
        } catch (SecurityException | IllegalStateException error) {
            Log.w("StudyNotifications", "Notification reschedule failed", error);
        }
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
                ReminderRescheduleWorker.enqueuePeriodic(context);
            }
        } catch (IllegalArgumentException | IllegalStateException | SecurityException error) {
            Log.w("StudyNotifications", "Could not apply study notification action", error);
        }
    }
}

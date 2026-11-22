package com.example.pkmapp.study;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

import android.accessibilityservice.AccessibilityService;
import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.view.View;
import android.view.ViewParent;
import android.widget.ScrollView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.pkmapp.MainActivity;
import com.example.pkmapp.R;
import com.example.pkmapp.navigation.AppDestination;
import java.util.Calendar;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class StudyWorkflowUiTest {
    @Test
    public void notificationCorrectionsAndReviewKeepOneLearningThread() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        Assume.assumeTrue(context.getPackageName().endsWith(".studytest"));
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.getUiAutomation().grantRuntimePermission(context.getPackageName(),
                android.Manifest.permission.POST_NOTIFICATIONS);
        }
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> activity.showDestination(AppDestination.STUDY_FOCUS));
            tapPage(scenario, withText("添加本次目标"));
            fill("这次要做什么", "图的遍历");
            tap("以此开始专注");
            assertTrue(instrumentation.getUiAutomation().performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME));
            action(context, "暂停").actionIntent.send();
            action(context, "继续").actionIntent.send();
            action(context, "暂停");
            SystemClock.sleep(1200);
            context.startActivity(new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            onView(withId(R.id.study_finish_button)).check(matches(isDisplayed()));
            action(context, "结束").actionIntent.send();
            onView(withText("保存这段专注")).check(matches(isDisplayed()));
            onView(withText("修正时长或科目")).perform(scrollTo(), click());
            fill("学习时长（时:分:秒）", "00:35:00");
            tap("应用修正");
            onView(withId(R.id.study_note_input)).perform(scrollTo(), replaceText("图论第 4 题"), closeSoftKeyboard());
            onView(withText("本次目标已完成")).perform(scrollTo(), click());
            fill("下次从哪继续（选填）", "接着做第 5 题");
            tap("保存学习");
            tap("继续学习");
            scenario.recreate();
            onView(withText("接着做第 5 题")).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withId(R.id.nav_study_stats)).perform(click());
            tapPage(scenario, withText("补记学习"));
            onView(withText(Matchers.startsWith("学习日期 · "))).perform(scrollTo(), click());
            onView(withText("学习日期")).check(matches(isDisplayed()));
            androidx.test.espresso.Espresso.pressBack();
            fill("学习时长（时:分:秒）", "00:40:00");
            fill("学习手记（选填）", "纸笔复习");
            tap("保存补记");
            tapPage(scenario, withText("查看全部"));
            fill("搜索学习手记", "图论");
            tap("数据结构");
            onView(withText("35分钟")).check(matches(isDisplayed()));
            tap("编辑记录");
            fill("学习时长（时:分:秒）", "00:33:00");
            tap("保存修改");
            onView(withText("33分钟")).check(matches(isDisplayed()));
            tap("加入待回顾");
            fill("回顾问题", "默写遍历步骤");
            tap("保存回顾");
            tap("查看待回顾");
            onView(withText("查看原学习记录")).perform(scrollTo(), click());
            onView(withText("图论第 4 题")).check(matches(isDisplayed()));
            onView(withContentDescription("上一步")).perform(click());
            tap("已掌握，完成回顾");
            onView(withText("再次安排回顾")).check(matches(isDisplayed()));
            scenario.onActivity(activity -> {
                StudyNotebook notebook = StudyStore.get(activity).notebook();
                assertEquals(73 * 60_000L, notebook.totalMillis());
                assertEquals(0, notebook.water());
                assertEquals(1, notebook.seeds());
                assertEquals(1, notebook.completedTasks(notebook.currentPlan().id, 0, Long.MAX_VALUE).size());
                assertEquals(1, notebook.completedReviews(notebook.currentPlan().id, 0, Long.MAX_VALUE).size());
            });
            onView(withContentDescription("关闭弹窗")).perform(click());
            scenario.onActivity(activity -> {
                StudyStore store = StudyStore.get(activity);
                Calendar date = Calendar.getInstance();
                date.add(Calendar.DATE, 2);
                String nearer = StudyNotebook.dayKey(date.getTimeInMillis());
                date.add(Calendar.DATE, 3);
                String later = StudyNotebook.dayKey(date.getTimeInMillis());
                store.update(n -> {
                    String plan = n.currentPlan().id;
                    n.setDeadline(plan, StudySubject.DATA_STRUCTURES.id, nearer);
                    n.setDeadline(plan, StudySubject.ENGLISH.id, later);
                    n.remindDeadlines(plan, true);
                    return null;
                });
                assertEquals(StudyFormat.deadlineReminder(nearer), context
                    .getSharedPreferences("study_notifications", Context.MODE_PRIVATE).getLong("deadlineAlarm", 0));
                activity.showDestination(AppDestination.STUDY_FOCUS);
            });
            tapPage(scenario, withText("最近截止 · 数据结构"));
            onView(withText("近期考试与截止")).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withContentDescription(Matchers.startsWith("数据结构，"))).perform(scrollTo(), click());
            onView(withText("科目 · 数据结构 ▾")).check(matches(isDisplayed()));
            fill("这次要做什么", "做一套摸底卷");
            tap("保存事项");
            onView(withText("做一套摸底卷")).check(matches(isDisplayed()));
        }
    }

    private static Notification.Action action(Context context, String title) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        long end = SystemClock.elapsedRealtime() + 5000;
        do {
            for (StatusBarNotification notice : manager.getActiveNotifications()) {
                Notification.Action[] actions = notice.getNotification().actions;
                if (actions == null) continue;
                for (Notification.Action action : actions) if (title.contentEquals(action.title)) return action;
            }
            SystemClock.sleep(100);
        } while (SystemClock.elapsedRealtime() < end);
        throw new AssertionError("Missing notification action: " + title);
    }

    private static void tap(String label) { onView(withText(label)).perform(click()); }

    private static void fill(String label, String value) {
        onView(withContentDescription(label)).perform(replaceText(value), closeSoftKeyboard());
    }

    private static void tapPage(ActivityScenario<MainActivity> scenario, Matcher<View> matcher) {
        onView(matcher).perform(scrollTo());
        scenario.onActivity(activity -> {
            View navigation = activity.findViewById(R.id.study_bottom_navigation);
            int[] nav = new int[2];
            navigation.getLocationInWindow(nav);
            View target = find(activity.findViewById(R.id.fragment_container), matcher);
            assertNotNull(target);
            int[] at = new int[2];
            target.getLocationInWindow(at);
            if (at[1] + target.getHeight() > nav[1] - 8) {
                ViewParent parent = target.getParent();
                while (!(parent instanceof ScrollView)) parent = parent.getParent();
                ((ScrollView) parent).scrollBy(0, at[1] + target.getHeight() - nav[1] + 10);
            }
        });
        onView(matcher).perform(click());
    }

    private static View find(View view, Matcher<View> matcher) {
        if (matcher.matches(view)) return view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup children = (android.view.ViewGroup) view;
            for (int i = 0; i < children.getChildCount(); i++) {
                View match = find(children.getChildAt(i), matcher);
                if (match != null) return match;
            }
        }
        return null;
    }
}

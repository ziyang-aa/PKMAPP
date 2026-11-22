package com.example.pkmapp.study;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.*;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.pkmapp.MainActivity;
import com.example.pkmapp.R;
import com.example.pkmapp.navigation.AppDestination;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class StudyPlanFlowTest {
    @Test
    public void createCrossDisciplinePlanAndArchivePreviousRound() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Assume.assumeTrue(context.getPackageName().endsWith(".studytest"));
        SharedPreferences prefs = context.getSharedPreferences("forest_study_v1", Context.MODE_PRIVATE);
        String legacy = new JSONObject().put("version", 2).put("selected", "DATA_STRUCTURES")
            .put("sessionGoal", 45).put("dailyGoal", 180)
            .put("water", 4).put("seeds", 0).put("trees", 2).put("planted", true).put("growth", 47)
            .put("records", new JSONArray().put(new JSONObject().put("id", "before-upgrade")
                .put("subject", "DATA_STRUCTURES").put("duration", 90_000)
                .put("completedAt", System.currentTimeMillis() - 100_000).put("note", "保留的学习手记")))
            .put("rewardedDays", new JSONArray()).toString();
        assertTrue(prefs.edit().putString("notebook", legacy).commit());

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> activity.showDestination(AppDestination.STUDY_FOCUS));
            tap("当前计划 · 408 考研 ▾");
            tap("新建计划");
            tap("期末");
            onView(withContentDescription("计划名称")).perform(replaceText("大三上期末"), closeSoftKeyboard());
            tap("从科目库添加");
            onView(withText("5 个专业")).check(matches(isDisplayed()));
            onView(withText("7 个专业")).check(matches(isDisplayed()));
            onView(withContentDescription("搜索科目、代码或专业"))
                .perform(click(), replaceText("不存在的科目"), pressImeActionButton());
            onView(withText("没有找到匹配的科目或专业。")).check(matches(isDisplayed()));
            onView(withContentDescription("清除搜索")).perform(click());
            tap("文科");
            tap("汉语言文学");
            tap("现代汉语");
            tap("加入书架");
            stepBack();
            tap("理科");
            tap("计算机科学与技术");
            tap("找不到科目？自定义添加");
            onView(withContentDescription("科目名称")).perform(replaceText("编译实验"), closeSoftKeyboard());
            tap("保存科目");
            onView(withText("加入书架")).check(matches(org.hamcrest.Matchers.not(isEnabled())));
            tap("数据库原理");
            onView(withText("已选 1 门")).check(matches(isDisplayed()));
            onView(withText("数据库原理")).check(matches(isChecked()));
            tap("数据库原理");
            onView(withText("已选 0 门")).check(matches(isDisplayed()));
            onView(withText("加入书架")).check(matches(org.hamcrest.Matchers.not(isEnabled())));
            tap("数据库原理");
            tap("加入书架");
            stepBack();
            onView(withContentDescription("搜索科目、代码或专业"))
                .perform(replaceText("859"), closeSoftKeyboard());
            tap("通信工程");
            tap("859  信号与系统");
            tap("加入书架");
            pressBack();
            tap("保存计划");
            onView(withId(R.id.study_subject_selector))
                .check(matches(withText(containsString("现代汉语"))));
            tap("当前计划 · 大三上期末 ▾");
            tap("408 考研");
            scenario.onActivity(activity -> StudyStore.get(activity).update(n -> {
                long now = System.currentTimeMillis();
                String plan = n.currentPlan().id;
                n.setDeadline(plan, StudySubject.ENGLISH.id, StudyNotebook.dayKey(now + 5 * 86_400_000L));
                n.putTask(plan, StudySubject.ENGLISH.id, "阅读第二篇", "", now, false);
                n.putTask(plan, StudySubject.NETWORKS.id, "复盘路由题", "", now, false);
                return null;
            }));
            tap("当前计划 · 408 考研 ▾");
            tap("408 考研");
            tap("完成当前计划");
            tap("完成并开启下一轮");
            onView(withContentDescription("下一轮计划名称")).perform(replaceText("408 二轮"), closeSoftKeyboard());
            onView(withText("沿用科目考试或提交日期")).check(matches(isDisplayed()));
            tap("英语 · 阅读第二篇");
            tap("归档并新建");
            tap("上一轮 · 408 考研 ›");
            onView(withText("408 考研 · 已归档 ▾")).check(matches(isDisplayed()));
            onView(withId(R.id.study_date_month)).perform(click()).check(matches(isSelected()));
            onView(withId(R.id.study_chart_bars)).perform(click()).check(matches(isSelected()));
            onView(withId(R.id.study_chart_trend)).perform(click()).check(matches(isSelected()));
            onView(withId(R.id.study_chart_ring)).perform(click()).check(matches(isSelected()));
            scenario.onActivity(activity -> {
                android.view.ViewParent parent = activity.findViewById(R.id.study_records_container).getParent();
                while (!(parent instanceof android.widget.ScrollView)) parent = parent.getParent();
                android.widget.ScrollView page = (android.widget.ScrollView) parent;
                page.scrollTo(0, page.getChildAt(0).getHeight());
            });
            onView(withContentDescription(org.hamcrest.Matchers.startsWith("数据结构，"))).perform(click());
            onView(withText("数据结构 ▾")).check(matches(isDisplayed()));
            tap("数据结构");
            onView(withText("保留的学习手记")).check(matches(isDisplayed()));
            stepBack();
            onView(withContentDescription("关闭弹窗")).perform(click());
            tap("查看全部");
            onView(withText(containsString("全部时间"))).check(matches(isDisplayed()));
            tap("数据结构");
            onView(withText("保留的学习手记")).check(matches(isDisplayed()));
            stepBack();
            onView(withText("全部学习记录")).check(matches(isDisplayed()));
            scenario.onActivity(activity -> {
                StudyNotebook stored = StudyNotebook.fromJson(prefs.getString("notebook", ""));
                assertEquals(legacy, prefs.getString("notebook_before_plans", ""));
                assertEquals("408 二轮", stored.currentPlan().title);
                assertEquals(0, stored.forPlan(stored.currentPlan().id).size());
                assertEquals(1, stored.pendingTasks(stored.currentPlan().id).size());
                assertEquals("阅读第二篇", stored.pendingTasks(stored.currentPlan().id).get(0).title);
                assertEquals("", stored.currentPlan().book(StudySubject.ENGLISH.id).due);
                assertFalse(stored.plan(StudyPlan.LEGACY_ID).book(StudySubject.ENGLISH.id).due.isEmpty());
                assertEquals(47, stored.growth());
                assertEquals("保留的学习手记", stored.forPlan(StudyPlan.LEGACY_ID).get(0).note);
                StudyPlan finals = stored.plans().stream().filter(plan -> plan.kind == StudyPlan.Kind.FINALS)
                    .findFirst().orElseThrow(AssertionError::new);
                assertEquals(4, finals.books().size());
                assertEquals("编译实验", finals.books().get(1).title);
                assertEquals("数据库原理", finals.books().get(2).title);
                assertEquals("859", finals.books().get(3).hint);
            });

            onView(withContentDescription("关闭弹窗")).perform(click());
            onView(withId(R.id.nav_study_focus)).perform(click());
            tap("当前计划 · 408 二轮 ▾");
            tap("已完成");
            tap("408 考研");
            tap("删除计划");
            onView(withText(containsString("1 条学习记录"))).check(matches(isDisplayed()));
            tap("保留计划");
            tap("删除计划");
            tap("确认删除");
            scenario.onActivity(activity -> {
                StudyNotebook stored = StudyNotebook.fromJson(prefs.getString("notebook", ""));
                assertNull(stored.plan(StudyPlan.LEGACY_ID));
                assertEquals("408 二轮", stored.currentPlan().title);
                assertEquals("", stored.currentPlan().previous);
                assertEquals(2, stored.plans().size());
                assertEquals(47, stored.growth());
            });
            deleteCurrent("408 二轮");
            deleteCurrent("大三上期末");
            onView(withText("还没有学习计划")).check(matches(isDisplayed()));
            scenario.recreate();
            onView(withText("还没有学习计划")).check(matches(isDisplayed()));
        }
    }

    private static void deleteCurrent(String name) {
        tap("当前计划 · " + name + " ▾");
        tap(name);
        tap("删除计划");
        tap("确认删除");
    }

    private static void stepBack() {
        onView(withContentDescription("上一步")).perform(click());
    }

    private static void tap(String label) {
        onView(withText(label)).perform(click());
    }
}

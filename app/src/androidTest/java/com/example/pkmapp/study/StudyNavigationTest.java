package com.example.pkmapp.study;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.Bitmap;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.pkmapp.MainActivity;
import com.example.pkmapp.R;
import com.example.pkmapp.navigation.AppDestination;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Run with -PstudyTestIsolation=true; this test never clears or writes the user's installed app. */
@RunWith(AndroidJUnit4.class)
public final class StudyNavigationTest {

    @Test
    public void learningAndLifeFlow_persistsTimerRecordsAndGarden() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Assume.assumeTrue(context.getPackageName().endsWith(".studytest"));
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(
                context.getPackageName(),
                android.Manifest.permission.POST_NOTIFICATIONS
            );
        }
        try (
            ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)
        ) {
            scenario.onActivity(a -> a.showDestination(AppDestination.HOME));
            onView(withId(R.id.home_enter_study)).check(
                matches(withEffectiveVisibility(Visibility.VISIBLE))
            );
            onView(withId(R.id.bottom_navigation)).check(
                matches(withEffectiveVisibility(Visibility.GONE))
            );
            onView(withId(R.id.study_bottom_navigation)).check(
                matches(withEffectiveVisibility(Visibility.GONE))
            );
            Thread.sleep(600);
            capture(context, "forest_home.png");
            onView(withId(R.id.home_enter_study)).perform(scrollTo(), click());
            capture(context, "study_focus.png");
            onView(withId(R.id.study_start_pause_button)).perform(scrollTo()).check(matches(isCompletelyDisplayed()));
            onView(withId(R.id.study_subject_selector)).check(matches(isDisplayed()));
            verifyCancelledPress(scenario, context);
            tapAboveNavigation(scenario, R.id.study_daily_goal_button);
            onView(withContentDescription("每日学习目标分钟")).perform(
                replaceText("13"),
                closeSoftKeyboard()
            );
            onView(withText("保存目标")).perform(click());
            onView(withContentDescription("每日学习目标分钟")).check(matches(isDisplayed()));
            onView(withContentDescription("每日学习目标分钟")).perform(
                replaceText("180"),
                closeSoftKeyboard()
            );
            capture(context, "dialog_daily_goal.png");
            onView(withText("保存目标")).perform(click());
            onView(withId(R.id.bottom_navigation)).check(
                matches(withEffectiveVisibility(Visibility.GONE))
            );
            onView(withId(R.id.study_subject_selector)).perform(scrollTo(), click());
            onView(withId(R.id.study_subject_picker)).check(matches(isDisplayed()));
            capture(context, "study_subjects.png");
            onView(withContentDescription("选择英语")).perform(click());
            onView(withId(R.id.study_subject_selector)).check(
                matches(withText(org.hamcrest.Matchers.containsString("英语")))
            );
            onView(withId(R.id.study_start_pause_button)).perform(scrollTo(), click());
            Thread.sleep(1200);
            onView(withId(R.id.study_start_pause_button)).perform(click());
            String[] clock = new String[1];
            scenario.onActivity(
                a ->
                    clock[0] = ((android.widget.TextView) a.findViewById(R.id.study_timer))
                        .getText()
                        .toString()
            );
            Thread.sleep(1100);
            scenario.onActivity(a ->
                assertEquals(
                    clock[0],
                    ((android.widget.TextView) a.findViewById(R.id.study_timer))
                        .getText()
                        .toString()
                )
            );
            scenario.recreate();
            onView(withText("继续学习")).check(matches(isDisplayed()));
            onView(withId(R.id.study_start_pause_button)).perform(click());
            onView(withId(R.id.page_back_button)).perform(scrollTo(), click());
            onView(withId(R.id.home_resume_study)).perform(scrollTo(), click());
            onView(withId(R.id.study_finish_button)).perform(scrollTo(), click());
            onView(withText("放弃本次")).perform(click());
            capture(context, "dialog_discard_confirmation.png");
            onView(withText("保留计时")).perform(click());
            onView(withId(R.id.study_note_input)).perform(
                typeText("study-flow-note"),
                closeSoftKeyboard()
            );
            capture(context, "dialog_finish_study.png");
            onView(withText("保存学习")).perform(click());
            capture(context, "dialog_study_reward.png");
            onView(withText("去看看我的小树")).perform(click());
            tapAboveNavigation(scenario, R.id.study_water_button);
            awaitCare(scenario);
            scenario.onActivity(a -> {
                StudyStore store = StudyStore.get(a);
                assertEquals(1, store.notebook().records().size());
                assertEquals("study-flow-note", store.notebook().records().get(0).note);
                assertTrue(store.notebook().planted());
                assertEquals(0, store.notebook().seeds());
                assertNotNull(
                    a
                        .getSharedPreferences("forest_study_v1", Context.MODE_PRIVATE)
                        .getString("notebook", null)
                );
                store.update(n -> {
                    long now = System.currentTimeMillis();
                    int i = 0;
                    for (StudySubject subject : StudySubject.values()) {
                        n.select(subject);
                        long duration = (35 + i * 10) * 60_000L;
                        long end = now - i * 86_400_000L;
                        n.start(end - duration, 1000, 11);
                        n.finish(end, 1000 + duration, 11, "instrumentation fixture");
                        i++;
                    }
                    return null;
                });
                a.showDestination(AppDestination.STUDY_STATS);
            });
            onView(withId(R.id.study_chart_view)).check(matches(isDisplayed()));
            capture(context, "study_ring.png");
            onView(withId(R.id.study_chart_bars)).perform(click());
            capture(context, "study_bars.png");
            onView(withId(R.id.study_chart_trend)).perform(click());
            capture(context, "study_trend.png");
            onView(withId(R.id.nav_study_garden)).perform(click());
            scenario.onActivity(a ->
                StudyStore.get(a).update(book -> {
                    for (int i = 0; i < 20; i++) book.addWater();
                    for (int i = 0; i < StudyGrowth.REQUIRED_WATER - 5; i++) book.waterTree();
                    return null;
                })
            );
            for (int i = 0; i < 5; i++) {
                tapAboveNavigation(scenario, R.id.study_water_button);
                if (i == 0) {
                    Thread.sleep(950);
                    capture(context, "study_watering.png");
                }
                awaitCare(scenario);
            }
            capture(context, "dialog_tree_collection.png");
            onView(withText("把这份成长收好")).check(matches(isCompletelyDisplayed()));
            // This sheet opens from the end of the watering animation, outside Espresso's click.
            Thread.sleep(350);
            onView(withText("把这份成长收好")).perform(click());
            scenario.onActivity(a -> {
                assertEquals(1, StudyStore.get(a).notebook().trees());
                assertFalse(StudyStore.get(a).notebook().planted());
            });
            scenario.onActivity(a -> pageScroll(a.findViewById(R.id.page_back_button)).scrollTo(0, 0));
            onView(withId(R.id.page_back_button)).perform(click());
            onView(withId(R.id.home_enter_life)).perform(scrollTo(), click());
            onView(withId(R.id.title_details)).check(matches(isDisplayed()));
            onView(withId(R.id.study_bottom_navigation)).check(
                matches(withEffectiveVisibility(Visibility.GONE))
            );
        }
    }

    private static void tapAboveNavigation(ActivityScenario<MainActivity> scenario, int id) {
        onView(withId(id)).perform(scrollTo());
        scenario.onActivity(activity -> {
            android.view.View control = activity.findViewById(id);
            int[] controlLocation = new int[2];
            int[] navLocation = new int[2];
            control.getLocationInWindow(controlLocation);
            activity.findViewById(R.id.study_bottom_navigation).getLocationInWindow(navLocation);
            int overlap = controlLocation[1] + control.getHeight() - navLocation[1];
            if (overlap >= 0) pageScroll(control).scrollBy(0, overlap + 24);
        });
        onView(withId(id)).perform(click());
    }

    private static android.widget.ScrollView pageScroll(android.view.View control) {
        android.view.ViewParent parent = control.getParent();
        while (!(parent instanceof android.widget.ScrollView)) parent = parent.getParent();
        return (android.widget.ScrollView) parent;
    }

    private static void awaitCare(ActivityScenario<MainActivity> scenario) throws Exception {
        long deadline = android.os.SystemClock.uptimeMillis() + 15_000;
        boolean[] ready = { false };
        while (!ready[0] && android.os.SystemClock.uptimeMillis() < deadline) {
            Thread.sleep(100);
            scenario.onActivity(a -> {
                android.view.View button = a.findViewById(R.id.study_water_button);
                ready[0] = button != null && button.isEnabled();
            });
        }
        assertTrue("Garden animation must finish and enable care", ready[0]);
    }

    private static void verifyCancelledPress(
        ActivityScenario<MainActivity> scenario,
        Context context
    ) throws Exception {
        long downTime = android.os.SystemClock.uptimeMillis();
        scenario.onActivity(a -> {
            android.view.View button = a.findViewById(R.id.study_start_pause_button);
            android.view.MotionEvent event = android.view.MotionEvent.obtain(
                downTime,
                downTime,
                android.view.MotionEvent.ACTION_DOWN,
                button.getWidth() / 2f,
                button.getHeight() / 2f,
                0
            );
            button.dispatchTouchEvent(event);
            event.recycle();
        });
        Thread.sleep(140);
        scenario.onActivity(a -> {
            android.view.View button = a.findViewById(R.id.study_start_pause_button);
            if (com.example.pkmapp.forest.ForestMotion.enabled()) assertTrue(
                button.getScaleX() < 1
            );
            assertFalse(StudyStore.get(a).notebook().hasSession());
        });
        capture(context, "button_pressed.png");
        scenario.onActivity(a -> {
            android.view.View button = a.findViewById(R.id.study_start_pause_button);
            android.view.MotionEvent event = android.view.MotionEvent.obtain(
                downTime,
                android.os.SystemClock.uptimeMillis(),
                android.view.MotionEvent.ACTION_CANCEL,
                button.getWidth() / 2f,
                button.getHeight() / 2f,
                0
            );
            button.dispatchTouchEvent(event);
            event.recycle();
        });
        Thread.sleep(250);
        scenario.onActivity(a -> {
            assertEquals(1f, a.findViewById(R.id.study_start_pause_button).getScaleX(), .001f);
            assertFalse(StudyStore.get(a).notebook().hasSession());
        });
    }

    private static void capture(Context context, String name) throws Exception {
        if (!"true".equals(InstrumentationRegistry.getArguments().getString("captureScreenshots"))) return;
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        Bitmap image = InstrumentationRegistry.getInstrumentation()
            .getUiAutomation()
            .takeScreenshot();
        try (
            FileOutputStream output = new FileOutputStream(
                new File(context.getExternalFilesDir(null), name)
            )
        ) {
            image.compress(Bitmap.CompressFormat.PNG, 100, output);
        }
        image.recycle();
    }
}

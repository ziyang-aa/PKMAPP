package com.example.pkmapp.study;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.view.ViewGroup;
import android.widget.ScrollView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.pkmapp.MainActivity;
import com.example.pkmapp.R;
import com.example.pkmapp.forest.ForestUi;
import com.example.pkmapp.forest.StudyBackdrop;
import com.example.pkmapp.navigation.AppDestination;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class StudyBackdropTest {

    @Test
    public void sceneryStaysFixedWhileContentScrolls_andRestoresOtherModules() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Assume.assumeTrue(context.getPackageName().endsWith(".studytest"));
        try (
            ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)
        ) {
            scenario.onActivity(a -> a.showDestination(AppDestination.STUDY_FOCUS));
            onView(withId(R.id.study_subject_selector)).check(matches(isDisplayed()));
            Thread.sleep(600);
            Bitmap full = screenshot();
            save(context, full, "study_fixed_background.png");
            full.recycle();
            final ScrollView[] scroll = new ScrollView[1];
            final Drawable[] background = new Drawable[1];
            final int[] originalMinimum = new int[1];
            final int[] inset = new int[1];
            final int[] toolbarBefore = new int[2],
                clockBefore = new int[2],
                navBefore = new int[2];
            scenario.onActivity(a -> {
                ViewGroup frame = a.findViewById(R.id.fragment_container);
                ViewGroup root = (ViewGroup) frame.getParent();
                assertTrue(root.getBackground() instanceof StudyBackdrop);
                assertTrue(
                    "Navigation must remain an independent overlay during scroll redraws",
                    a.findViewById(R.id.study_bottom_navigation).getZ() > frame.getZ()
                );
                assertTrue(
                    "Navigation must be above the content layer",
                    root.indexOfChild(a.findViewById(R.id.study_bottom_navigation)) >
                        root.indexOfChild(frame)
                );
                background[0] = root.getBackground();
                inset[0] = root.getPaddingTop();
                scroll[0] = (ScrollView) frame.getChildAt(0);
                assertEquals("No fixed top content band", 0, root.getPaddingTop());
                assertEquals("No fixed bottom content band", 0, root.getPaddingBottom());
                assertEquals(
                    "Content covers the complete screen",
                    root.getHeight(),
                    frame.getHeight()
                );
                assertEquals(
                    0,
                    ((android.widget.FrameLayout.LayoutParams) frame.getLayoutParams()).bottomMargin
                );
                assertFalse("Content can draw inside inset padding", scroll[0].getClipToPadding());
                assertTrue(
                    scroll[0].getBackground() == null ||
                        scroll[0].getBackground().getOpacity() ==
                            android.graphics.PixelFormat.TRANSPARENT
                );
                // Extend content only; the viewport must remain full-screen for this regression.
                originalMinimum[0] = scroll[0].getChildAt(0).getMinimumHeight();
                scroll[0].getChildAt(0).setMinimumHeight(root.getHeight() * 2);
                scroll[0].scrollTo(0, 0);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            Thread.sleep(150);
            scenario.onActivity(a -> {
                a.findViewById(R.id.page_back_button).getLocationOnScreen(toolbarBefore);
                a.findViewById(R.id.study_timer).getLocationOnScreen(clockBefore);
                a.findViewById(R.id.study_bottom_navigation).getLocationOnScreen(navBefore);
            });
            Bitmap before = screenshot();
            scenario.onActivity(a -> scroll[0].scrollTo(0, ForestUi.dp(a, 40)));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            Thread.sleep(150);
            scenario.onActivity(a -> {
                android.graphics.Rect visible = new android.graphics.Rect();
                assertTrue(a.findViewById(R.id.page_back_button).getGlobalVisibleRect(visible));
                assertTrue(
                    "Toolbar must visibly pass beneath status icons",
                    visible.top < scroll[0].getPaddingTop()
                );
            });
            Bitmap underStatus = screenshot();
            save(context, underStatus, "study_content_under_status.png");
            underStatus.recycle();
            scenario.onActivity(a -> scroll[0].scrollTo(0, ForestUi.dp(a, 180)));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            Thread.sleep(150);
            Bitmap after = screenshot();
            scenario.onActivity(a -> {
                assertTrue("Foreground must actually move", scroll[0].getScrollY() > 0);
                int[] toolbarAfter = new int[2],
                    clockAfter = new int[2],
                    navAfter = new int[2];
                a.findViewById(R.id.page_back_button).getLocationOnScreen(toolbarAfter);
                a.findViewById(R.id.study_timer).getLocationOnScreen(clockAfter);
                a.findViewById(R.id.study_bottom_navigation).getLocationOnScreen(navAfter);
                assertEquals(
                    "Toolbar must scroll with the page",
                    toolbarBefore[1] - scroll[0].getScrollY(),
                    toolbarAfter[1]
                );
                assertEquals(
                    "Timer must scroll with the toolbar",
                    clockBefore[1] - scroll[0].getScrollY(),
                    clockAfter[1]
                );
                assertArrayEquals("Bottom navigation must remain stationary", navBefore, navAfter);
                assertSame(
                    background[0],
                    ((android.view.View) scroll[0].getParent().getParent()).getBackground()
                );
            });
            save(context, before, "study_fixed_before_scroll.png");
            save(context, after, "study_fixed_after_scroll.png");
            // Stay outside cards and toolbar controls, but clear of the scroll indicator at the edge.
            int x = ForestUi.dp(context, 8);
            for (int offset = 40; offset <= 175; offset += 15) {
                int y = inset[0] + ForestUi.dp(context, offset);
                assertEquals(
                    "Left scenery moved at " + offset,
                    before.getPixel(x, y),
                    after.getPixel(x, y)
                );
                assertEquals(
                    "Right scenery moved at " + offset,
                    before.getPixel(before.getWidth() - 1 - x, y),
                    after.getPixel(after.getWidth() - 1 - x, y)
                );
            }
            before.recycle();
            after.recycle();
            scenario.onActivity(a -> {
                ViewGroup frame = a.findViewById(R.id.fragment_container);
                scroll[0].getChildAt(0).setMinimumHeight(originalMinimum[0]);
                a.showDestination(AppDestination.STUDY_STATS);
            });
            onView(withId(R.id.study_chart_view)).check(matches(isDisplayed()));
            Bitmap stats = screenshot();
            save(context, stats, "stats_full_screen_overlay.png");
            stats.recycle();
            scenario.onActivity(a -> {
                ViewGroup frame = a.findViewById(R.id.fragment_container);
                assertTrue(
                    ((android.view.View) frame.getParent()).getBackground() instanceof StudyBackdrop
                );
                a.showDestination(AppDestination.HOME);
            });
            onView(withId(R.id.home_enter_study)).check(matches(isDisplayed()));
            scenario.onActivity(a -> {
                ViewGroup frame = a.findViewById(R.id.fragment_container);
                assertFalse(
                    ((android.view.View) frame.getParent()).getBackground() instanceof StudyBackdrop
                );
                a.showDestination(AppDestination.DETAILS);
            });
            onView(withId(R.id.title_details)).check(matches(isDisplayed()));
        }
    }

    private static Bitmap screenshot() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        return InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
    }

    private static void save(Context context, Bitmap bitmap, String name) throws Exception {
        try (
            FileOutputStream output = new FileOutputStream(
                new File(context.getExternalFilesDir(null), name)
            )
        ) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        }
    }
}

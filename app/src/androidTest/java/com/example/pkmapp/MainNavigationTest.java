package com.example.pkmapp;

import android.view.View;
import android.view.ViewGroup;
import com.example.pkmapp.forest.FrostedNavigationBar;
import com.example.pkmapp.navigation.AppDestination;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.ViewMatchers.Visibility;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class MainNavigationTest {
    @Rule
    public ActivityScenarioRule<MainActivity> rule = new ActivityScenarioRule<>(MainActivity.class);

    @Before
    public void enterLifeCottage() {
        rule.getScenario().onActivity(a -> a.showDestination(AppDestination.HOME));
        onView(withId(R.id.home_enter_life)).perform(scrollTo(), click());
    }

    @Test
    public void allFourBottomButtonsAndFabOpenTheirFeaturePage() {
        onView(withId(R.id.nav_details)).perform(click());
        onView(withId(R.id.title_details)).check(matches(isDisplayed()));
        onView(withId(R.id.fab_record)).check(matches(isDisplayed()));
        assertLedgerLayers();

        onView(withId(R.id.nav_charts)).perform(click());
        onView(withId(R.id.title_charts)).check(matches(isDisplayed()));
        onView(withId(R.id.fab_record)).check(matches(isDisplayed()));
        assertLedgerLayers();

        onView(withId(R.id.nav_savings)).perform(click());
        onView(withId(R.id.title_savings)).check(matches(isDisplayed()));
        onView(withId(R.id.fab_record)).check(matches(isDisplayed()));
        assertLedgerLayers();

        onView(withId(R.id.nav_vault)).perform(click());
        onView(withId(R.id.title_vault)).check(matches(isDisplayed()));
        onView(withId(R.id.fab_record)).check(matches(withEffectiveVisibility(Visibility.GONE)));
        assertLedgerLayers();

        onView(withId(R.id.nav_details)).perform(click());
        onView(withId(R.id.fab_record)).perform(click());
        onView(withId(R.id.title_record)).check(matches(isDisplayed()));
        onView(withId(R.id.fab_record)).check(matches(withEffectiveVisibility(Visibility.GONE)));
        assertLedgerLayers();
    }

    private void assertLedgerLayers() {
        rule.getScenario().onActivity(activity -> {
            ViewGroup content = activity.findViewById(R.id.fragment_container);
            ViewGroup root = (ViewGroup) content.getParent();
            ViewGroup scroll = (ViewGroup) content.getChildAt(0);
            View nav = activity.findViewById(R.id.bottom_navigation);

            assertNotNull(root.getBackground());
            assertNull("Only the activity draws the forest background", scroll.getBackground());
            assertEquals(root.getHeight(), scroll.getHeight());
            assertFalse("Content can move through the safe padding", scroll.getClipToPadding());
            assertTrue("Ledger navigation uses the shared frosted surface", nav instanceof FrostedNavigationBar);
            assertNull("No opaque background under the glass", nav.getBackground());
            assertTrue("Bottom navigation floats above the page", nav.getZ() > content.getZ());
            assertTrue("The last row can scroll clear of the bottom navigation",
                    scroll.getPaddingBottom() > nav.getHeight());
        });
    }

    @Test
    public void recordPage_revealsEntryControlsAfterCategorySelection() {
        onView(withId(R.id.fab_record)).perform(click());
        onView(withId(R.id.record_category_group)).check(matches(isDisplayed()));
        onView(withText("餐饮")).perform(scrollTo(), click());
        onView(withId(R.id.record_selected_category)).perform(scrollTo()).check(matches(isDisplayed()));
        onView(withId(R.id.record_calculator_panel)).perform(scrollTo()).check(matches(isDisplayed()));
        onView(withId(R.id.record_calculator_display)).check(matches(isDisplayed()));
        onView(withId(R.id.record_date_button)).perform(scrollTo()).check(matches(isDisplayed()));
        onView(withId(R.id.record_save_button)).perform(scrollTo()).check(matches(isDisplayed()));
    }

    @Test
    public void detailsPage_hasMonthlySummaryAndTransactionList() {
        onView(withId(R.id.nav_details)).perform(click());
        onView(withId(R.id.details_ledger_switch_button)).check(matches(isDisplayed()));
        onView(withId(R.id.details_tools_card)).check(matches(isDisplayed()));
        onView(withId(R.id.details_fx_tool_button)).check(matches(isDisplayed()));
        onView(withId(R.id.details_borrowing_tool_button)).check(matches(isDisplayed()));
        onView(withId(R.id.details_summary_card)).check(matches(isDisplayed()));
        rule.getScenario().onActivity(activity -> {
            View list = activity.findViewById(R.id.details_transaction_list);
            View empty = activity.findViewById(R.id.details_empty_state);
            assertTrue("Expected either transaction list or empty state visible",
                    list.getVisibility() == View.VISIBLE || empty.getVisibility() == View.VISIBLE);
        });
    }

    @Test
    public void vaultPage_showsNetWorthAndToolbox() {
        onView(withId(R.id.nav_vault)).perform(click());
        onView(withId(R.id.vault_net_worth_card)).check(matches(isDisplayed()));
        onView(withId(R.id.vault_ledger_manage_button)).check(matches(isDisplayed()));
        onView(withId(R.id.vault_category_manage_button)).check(matches(isDisplayed()));
        onView(withId(R.id.vault_borrowing_tool_button)).check(matches(isDisplayed()));
        onView(withId(R.id.vault_exchange_tool_button)).check(matches(isDisplayed()));
    }

    @Test
    public void homeDrawer_opensAndNavigatesToFeedback() {
        onView(withId(R.id.page_back_button)).perform(click());
        onView(withId(R.id.home_avatar_trigger)).perform(click());
        onView(withText("意见反馈中心")).perform(scrollTo(), click());
        onView(withId(R.id.title_feedback)).check(matches(isDisplayed()));
        onView(withId(R.id.feedback_submit_button)).perform(scrollTo()).check(matches(isDisplayed()));
    }

    @Test
    public void featurePage_hasBackButton() {
        onView(withId(R.id.nav_details)).perform(click());
        onView(withId(R.id.page_back_button)).check(matches(isDisplayed()));
    }

    @Test
    public void saveExpense_opensDetailsWithNewTransactionVisible() {
        onView(withId(R.id.fab_record)).perform(click());
        onView(withText("餐饮")).perform(scrollTo(), click());
        onView(withId(R.id.record_key_1)).perform(scrollTo(), click());
        onView(withId(R.id.record_key_2)).perform(scrollTo(), click());
        onView(withId(R.id.record_key_dot)).perform(scrollTo(), click());
        onView(withId(R.id.record_key_5)).perform(scrollTo(), click());
        onView(withId(R.id.record_key_8)).perform(scrollTo(), click());
        onView(withId(R.id.record_note_input)).perform(scrollTo(), replaceText("端到端测试"), closeSoftKeyboard());
        onView(withId(R.id.record_save_button)).perform(scrollTo(), click());
        onView(withId(R.id.record_success_details)).perform(click());

        onView(withId(R.id.title_details)).check(matches(isDisplayed()));
        onView(withId(R.id.details_transaction_list)).perform(scrollTo()).check(matches(isDisplayed()));
        onView(withText("端到端测试")).check(matches(isDisplayed()));
        onView(withId(R.id.details_summary_card)).check(matches(isDisplayed()));
    }
}

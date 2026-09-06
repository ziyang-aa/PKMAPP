package com.example.pkmapp;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

@RunWith(AndroidJUnit4.class)
public final class MainNavigationTest {
    @Rule
    public ActivityScenarioRule<MainActivity> rule = new ActivityScenarioRule<>(MainActivity.class);

    @Test
    public void allFiveButtonsOpenTheirFeaturePage() {
        onView(withId(R.id.nav_details)).perform(click());
        onView(withId(R.id.title_details)).check(matches(isDisplayed()));
        onView(withId(R.id.nav_charts)).perform(click());
        onView(withId(R.id.title_charts)).check(matches(isDisplayed()));
        onView(withId(R.id.nav_record)).perform(click());
        onView(withId(R.id.title_record)).check(matches(isDisplayed()));
        onView(withId(R.id.nav_savings)).perform(click());
        onView(withId(R.id.title_savings)).check(matches(isDisplayed()));
        onView(withId(R.id.nav_profile)).perform(click());
        onView(withId(R.id.title_profile)).check(matches(isDisplayed()));
    }
}

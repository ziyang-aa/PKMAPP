package com.example.pkmapp.account

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.isNotEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pkmapp.R
import com.example.pkmapp.forest.SquirrelMascotView
import com.google.android.material.textfield.TextInputLayout
import org.hamcrest.Matchers.containsString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthActivityTest {

    @Test
    fun choiceToLoginForm_usesSeparatedFloatingLabelsWithoutPlaceholders_andShowsMascotErrorBubble() {
        val intent = AuthActivity.createIntent(
            ApplicationProvider.getApplicationContext(),
            returnToCaller = true,
            initialStep = AuthActivity.STEP_CHOICE
        )
        ActivityScenario.launch<AuthActivity>(intent).use { scenario ->
            onView(withText("已有账号？")).check(matches(isDisplayed()))
            onView(withText("初次来到小松果？")).check(matches(isDisplayed()))
            onView(withId(R.id.auth_choice_mascot)).check(matches(isDisplayed()))
            onView(withId(R.id.auth_choice_guest_button)).check(matches(isDisplayed()))

            onView(withId(R.id.auth_go_login_button)).perform(click())

            onView(withText("输入账号信息")).check(matches(isDisplayed()))
            onView(withId(R.id.auth_form_mascot)).check(matches(isDisplayed()))
            onView(withId(R.id.auth_form_bubble)).check(matches(isDisplayed()))
            onView(withId(R.id.auth_submit_button)).check(matches(isNotEnabled()))

            scenario.onActivity { activity ->
                val idLayout = activity.findViewById<TextInputLayout>(R.id.auth_identifier_layout)
                val passLayout = activity.findViewById<TextInputLayout>(R.id.auth_password_layout)
                assertTrue(idLayout.isHintEnabled)
                assertTrue(passLayout.isHintEnabled)
                assertEquals("手机号或邮箱", idLayout.hint?.toString())
                assertEquals("密码", passLayout.hint?.toString())
                assertNull(idLayout.placeholderText)
                assertNull(passLayout.placeholderText)
                assertTrue(
                    "Account and password boxes must be separated vertically",
                    passLayout.top > idLayout.bottom
                )
            }

            onView(withId(R.id.auth_identifier_input)).perform(replaceText("13800138000"))
            onView(withId(R.id.auth_submit_button)).check(matches(isNotEnabled()))

            onView(withId(R.id.auth_password_input)).perform(replaceText("short"), closeSoftKeyboard())
            onView(withId(R.id.auth_submit_button)).check(matches(isEnabled()))
            onView(withId(R.id.auth_submit_button)).perform(click())

            onView(withId(R.id.auth_form_bubble)).check(matches(isDisplayed()))
            onView(withId(R.id.auth_form_bubble)).check(matches(withText(containsString("8 位"))))
            scenario.onActivity { activity ->
                val mascot = activity.findViewById<SquirrelMascotView>(R.id.auth_form_mascot)
                assertEquals(SquirrelMascotView.Mood.ERROR, mascot.currentMood())
            }
        }
    }

    @Test
    fun registerFlow_showsNicknameField_andAllowsGuestEntry() {
        val intent = AuthActivity.createIntent(
            ApplicationProvider.getApplicationContext(),
            returnToCaller = false,
            initialStep = AuthActivity.STEP_CHOICE
        )
        ActivityScenario.launch<AuthActivity>(intent).use {
            onView(withId(R.id.auth_go_register_button)).perform(click())
            onView(withText("创建小松果账号")).check(matches(isDisplayed()))
            onView(withId(R.id.auth_nickname_layout)).check(matches(isDisplayed()))
            onView(withId(R.id.auth_form_guest_button)).perform(click())
            val deadline = System.currentTimeMillis() + 4000L
            var reachedHome = false
            while (System.currentTimeMillis() < deadline) {
                try {
                    onView(withId(R.id.home_enter_life)).check(matches(isDisplayed()))
                    reachedHome = true
                    break
                } catch (_: Throwable) {
                    Thread.sleep(100L)
                }
            }
            assertTrue("Expected guest entry to open MainActivity Home", reachedHome)
        }
    }
}

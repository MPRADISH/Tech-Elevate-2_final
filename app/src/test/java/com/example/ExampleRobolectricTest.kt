package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("CampusGuard", appName)
    }

    @Test
    fun `initial setup screen opens without blocking when server is unreachable`() {
        composeTestRule.onNodeWithText("CampusGuard").assertIsDisplayed()
        composeTestRule.onNodeWithTag("connection_status_pill").assertIsDisplayed()

        // Verify initial setup screen controls exist and can be scrolled into view
        composeTestRule.onNodeWithTag("server_url_input").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("student_name_input").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("hostel_select_1").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("hostel_select_2").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("hostel_select_3").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("hostel_select_4").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("register_button").performScrollTo().assertIsDisplayed()

        // Switch to Safety & Alarm tab and verify monitoring controls render
        composeTestRule.onNodeWithTag("tab_safety").performClick()
        composeTestRule.onNodeWithTag("test_alarm_button").assertExists()
    }
}

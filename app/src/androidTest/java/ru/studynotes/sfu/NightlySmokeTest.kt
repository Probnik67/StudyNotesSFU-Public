package ru.studynotes.sfu

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NightlySmokeTest {

    @get:org.junit.Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchAppAndCaptureFirstFrame() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS"
        ).close()
        composeRule.waitForIdle()
        Thread.sleep(1600)
        capture(instrumentation, "nightly-first-frame.png")
        composeRule.onNodeWithContentDescription("Заметки").assertExists().performClick()
        composeRule.waitForIdle()
        capture(instrumentation, "nightly-notes.png")
        composeRule.onNodeWithContentDescription("Домашние задания").assertExists().performClick()
        composeRule.waitForIdle()
        capture(instrumentation, "nightly-homework.png")
    }

    private fun capture(instrumentation: android.app.Instrumentation, filename: String) {
        Thread.sleep(700)
        val output = File(File("/sdcard/Download"), filename)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        assertTrue("Screenshot must have a non-zero size", bitmap.width > 0 && bitmap.height > 0)
        output.outputStream().use { stream ->
            assertTrue("Screenshot must be encoded as PNG", bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream))
        }
        assertTrue("Screenshot file must exist", output.isFile && output.length() > 0)
    }
}

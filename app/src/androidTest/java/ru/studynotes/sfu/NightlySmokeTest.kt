package ru.studynotes.sfu

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NightlySmokeTest {

    @Test
    fun launchAppAndCaptureFirstFrame() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS"
        ).close()
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }

        ActivityScenario.launch<MainActivity>(intent).use {
            instrumentation.waitForIdleSync()
            Thread.sleep(1600)

            val output = File(
                File("/sdcard/Download"),
                "nightly-first-frame.png"
            )
            val uiAutomation = instrumentation.uiAutomation
            val bitmap = uiAutomation.takeScreenshot()
            assertTrue("Screenshot must have a non-zero size", bitmap.width > 0 && bitmap.height > 0)
            output.outputStream().use { stream ->
                assertTrue("Screenshot must be encoded as PNG", bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream))
            }
            assertTrue("Screenshot file must exist", output.isFile && output.length() > 0)
        }
    }
}

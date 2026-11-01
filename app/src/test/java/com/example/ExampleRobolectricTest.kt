package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.TimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Alarm", appName)
  }

  @Test
  fun `time formatter 12h format verification`() {
    val (time1, amPm1) = TimeFormatter.formatClockTime(5, 10)
    assertEquals("5:10", time1)
    assertEquals("AM", amPm1)

    val (time2, amPm2) = TimeFormatter.formatClockTime(17, 30)
    assertEquals("5:30", time2)
    assertEquals("PM", amPm2)

    val (time3, amPm3) = TimeFormatter.formatClockTime(0, 0)
    assertEquals("12:00", time3)
    assertEquals("AM", amPm3)
  }
}

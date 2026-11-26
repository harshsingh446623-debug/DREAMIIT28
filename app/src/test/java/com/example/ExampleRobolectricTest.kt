package com.example

import com.example.ui.viewmodel.DreamiitStudioHelpers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ExampleRobolectricTest {

  @Test
  fun `verify helper functions work correctly`() {
    val clamped = DreamiitStudioHelpers.clampLiveDurationMinutes(240)
    assertEquals(180, clamped)
    
    val masked = DreamiitStudioHelpers.maskStreamKeyUrl("rtmps://a.rtmps.youtube.com/live2?key=abcdef1234567890")
    assertNotNull(masked)
  }
}

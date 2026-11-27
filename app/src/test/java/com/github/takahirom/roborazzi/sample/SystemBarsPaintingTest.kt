package com.github.takahirom.roborazzi.sample

import com.github.takahirom.roborazzi.RoboCanvas
import com.github.takahirom.roborazzi.SystemBarGeometry
import com.github.takahirom.roborazzi.SystemBarsOptions
import com.github.takahirom.roborazzi.drawSystemBars

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Exercises the painting half of the system bar overlay with hand-built geometry, matching the
 * sizes Robolectric's SystemUi reports for a 1080x2400 xxhdpi-ish (2.625) phone.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SystemBarsPaintingTest {
  private val width = 1080
  private val height = 2400
  private val density = 2.625f
  private val statusBarHeight = 63 // 24dp

  @Test
  fun gesturalNavigation() {
    val canvas = paint(navBarHeight = 63) // 24dp -> Auto picks the gesture handle
    val content = canvas.getPixel(width / 2, height / 2)
    assertNotEquals("status bar scrim", content, canvas.getPixel(width / 2, 5))
    assertEquals("handle centre", Color.WHITE or 0xFF000000.toInt(), canvas.getPixel(width / 2, height - 32) or 0xFF000000.toInt())
    canvas.save(File("build/outputs/roborazzi/system_bars_gestural.png"), 1.0)
  }

  @Test
  fun threeButtonNavigation() {
    val canvas = paint(navBarHeight = 126) // 48dp -> Auto picks three buttons
    val content = canvas.getPixel(width / 2, height / 2)
    assertNotEquals("nav bar scrim", content, canvas.getPixel(5, height - 5))
    canvas.save(File("build/outputs/roborazzi/system_bars_three_button.png"), 1.0)
  }

  @Test
  fun gesturalSideNavigationStaysInsideTheBar() {
    val navBarWidth = 63 // 24dp on the right
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).drawColor(Color.rgb(0xE8, 0xDE, 0xF8))
    val canvas = RoboCanvas(width, height, true).apply {
      drawImage(Rect(0, 0, width, height), bitmap)
    }
    val content = canvas.getPixel(width / 2, height / 2)
    canvas.drawSystemBars(
      SystemBarGeometry(
        density = density,
        statusBar = null,
        navigationBar = Rect(width - navBarWidth, 0, width, height),
        navigationBarSide = SystemBarGeometry.Side.Right,
      ),
      SystemBarsOptions(),
    )
    // Just left of the bar, level with the handle's centre: untouched content.
    assertEquals(content, canvas.getPixel(width - navBarWidth - 2, height / 2))
  }

  private fun paint(navBarHeight: Int): RoboCanvas {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).drawColor(Color.rgb(0xE8, 0xDE, 0xF8))
    val canvas = RoboCanvas(width, height, true).apply {
      drawImage(Rect(0, 0, width, height), bitmap)
    }
    canvas.drawSystemBars(
      SystemBarGeometry(
        density = density,
        statusBar = Rect(0, 0, width, statusBarHeight),
        navigationBar = Rect(0, height - navBarHeight, width, height),
        navigationBarSide = SystemBarGeometry.Side.Bottom,
      ),
      SystemBarsOptions(),
    )
    return canvas
  }
}

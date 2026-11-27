package com.github.takahirom.roborazzi

import android.graphics.Rect
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.roundToInt

/**
 * Paints placeholder system bars (status bar and navigation bar) onto a captured image, after
 * capture, at the positions given by the window's system bar insets.
 *
 * The app's view tree never contains real system bars (on a device they are SystemUI windows), so
 * without this a screenshot shows only the empty space the insets reserved. Under Robolectric the
 * insets come from `SystemUi`, which reserves nothing by default. To get non-zero insets, configure
 * a status bar and navigation bar behaviour there (for example `STANDARD_STATUS_BAR` and
 * `GESTURAL_NAVIGATION`).
 *
 * The overlay is deterministic (fixed clock text, no live state) so goldens are stable. It is only
 * applied for [RoborazziOptions.CaptureType.Screenshot].
 */
data class SystemBarsOptions(
  /** ARGB fill for the status bar area. Defaults to a translucent black scrim. */
  val statusBarColor: Int = 0x66000000,
  /** ARGB fill for the navigation bar area. */
  val navigationBarColor: Int = 0x66000000,
  /** ARGB colour for the clock, status icons, gesture handle and nav buttons. */
  val foregroundColor: Int = 0xFFFFFFFF.toInt(),
  /** Fixed clock text, so screenshots don't change with the time of day. */
  val clockText: String = "12:00",
  val drawStatusBarContent: Boolean = true,
  val navigationStyle: NavigationStyle = NavigationStyle.Auto,
) {
  enum class NavigationStyle {
    /** Gesture handle if the navigation bar is thinner than 32dp, otherwise three buttons. */
    Auto,
    Gestural,
    ThreeButton,
    /** Only fill the navigation bar area with [navigationBarColor]. */
    BackgroundOnly,
  }
}

/** Where the system bars sit, in the captured component's image coordinates. */
class SystemBarGeometry(
  val density: Float,
  /** Status bar rect, or null when there is no status bar inset. May extend past the image. */
  val statusBar: Rect?,
  /** Navigation bar rect, or null when there is no navigation bar inset. */
  val navigationBar: Rect?,
  val navigationBarSide: Side,
) {
  enum class Side { Bottom, Left, Right }

  companion object {
    /**
     * Reads the root window insets of [view] and maps the bars into the coordinates of a
     * component whose bounds in the window are [componentInWindow].
     */
    internal fun of(view: android.view.View, componentInWindow: Rect): SystemBarGeometry? {
      val insets = ViewCompat.getRootWindowInsets(view) ?: return null
      val root = view.rootView
      val windowWidth = root.width
      val windowHeight = root.height
      if (windowWidth == 0 || windowHeight == 0) return null

      val status = insets.getInsets(WindowInsetsCompat.Type.statusBars())
      val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())

      val statusBar = if (status.top > 0) Rect(0, 0, windowWidth, status.top) else null
      val (navigationBar, side) = when {
        nav.bottom > 0 -> Rect(0, windowHeight - nav.bottom, windowWidth, windowHeight) to Side.Bottom
        nav.left > 0 -> Rect(0, 0, nav.left, windowHeight) to Side.Left
        nav.right > 0 -> Rect(windowWidth - nav.right, 0, windowWidth, windowHeight) to Side.Right
        else -> null to Side.Bottom
      }

      val componentBounds = Rect(0, 0, componentInWindow.width(), componentInWindow.height())
      fun Rect.toComponent(): Rect? = Rect(this).apply {
        offset(-componentInWindow.left, -componentInWindow.top)
      }.takeIf { Rect.intersects(it, componentBounds) }

      return SystemBarGeometry(
        density = view.resources.displayMetrics.density,
        statusBar = statusBar?.toComponent(),
        navigationBar = navigationBar?.toComponent(),
        navigationBarSide = side,
      )
    }
  }
}

/**
 * Paints placeholder bars described by [geometry] onto this canvas. Called automatically when
 * [RoborazziOptions.systemBarsOptions] is set; public so callers capturing a [android.graphics.Bitmap]
 * can apply it themselves.
 */
fun RoboCanvas.drawSystemBars(geometry: SystemBarGeometry, options: SystemBarsOptions) {
  fun dp(value: Float): Int = (value * geometry.density).roundToInt()
  val fg = options.foregroundColor

  geometry.statusBar?.let { bar ->
    fillRoundRect(bar, 0, options.statusBarColor)
    if (options.drawStatusBarContent) {
      val textSize = dp(14f).toFloat()
      val baseline = bar.top + (bar.height() + (textSize * 0.7f).roundToInt()) / 2
      drawString(options.clockText, bar.left + dp(16f), baseline, textSize, fg)

      // Battery: a filled body plus terminal, right-aligned.
      val batteryHeight = dp(12f)
      val batteryWidth = dp(7f)
      val batteryRight = bar.right - dp(16f)
      val batteryTop = bar.centerY() - batteryHeight / 2
      fillRoundRect(
        Rect(batteryRight - batteryWidth, batteryTop, batteryRight, batteryTop + batteryHeight),
        dp(1f), fg
      )
      // Signal: a right-angled triangle to the left of the battery.
      val signalRight = batteryRight - batteryWidth - dp(6f)
      val signalSize = dp(12f)
      val signalBottom = batteryTop + batteryHeight
      fillPolygon(
        intArrayOf(signalRight - signalSize, signalRight, signalRight),
        intArrayOf(signalBottom, signalBottom, signalBottom - signalSize),
        fg
      )
    }
  }

  geometry.navigationBar?.let { bar ->
    fillRoundRect(bar, 0, options.navigationBarColor)
    val thickness = if (geometry.navigationBarSide == SystemBarGeometry.Side.Bottom) {
      bar.height()
    } else {
      bar.width()
    }
    val style = when (options.navigationStyle) {
      SystemBarsOptions.NavigationStyle.Auto ->
        if (thickness < dp(32f)) SystemBarsOptions.NavigationStyle.Gestural
        else SystemBarsOptions.NavigationStyle.ThreeButton

      else -> options.navigationStyle
    }
    when (style) {
      SystemBarsOptions.NavigationStyle.Gestural -> {
        val handleWidth = dp(108f)
        val handleHeight = dp(4f)
        fillRoundRect(
          Rect(
            bar.centerX() - handleWidth / 2, bar.centerY() - handleHeight / 2,
            bar.centerX() + handleWidth / 2, bar.centerY() + handleHeight / 2
          ),
          handleHeight / 2, fg
        )
      }

      SystemBarsOptions.NavigationStyle.ThreeButton -> drawNavButtons(geometry, bar, ::dp, fg)
      else -> Unit
    }
  }
}

private fun RoboCanvas.drawNavButtons(
  geometry: SystemBarGeometry,
  bar: Rect,
  dp: (Float) -> Int,
  fg: Int,
) {
  val horizontal = geometry.navigationBarSide == SystemBarGeometry.Side.Bottom
  val glyph = dp(16f)
  val half = glyph / 2
  val stroke = dp(2f).toFloat()
  // Back, home, recents — left to right along the bottom, or bottom to top along a side bar.
  val centres = listOf(0.25f, 0.5f, 0.75f).map { fraction ->
    if (horizontal) {
      (bar.left + bar.width() * fraction).roundToInt() to bar.centerY()
    } else {
      bar.centerX() to (bar.bottom - bar.height() * fraction).roundToInt()
    }
  }
  val (back, home, recents) = centres
  fillPolygon(
    intArrayOf(back.first - half, back.first + half, back.first + half),
    intArrayOf(back.second, back.second - half, back.second + half),
    fg
  )
  strokeRoundRect(
    Rect(home.first - half, home.second - half, home.first + half, home.second + half),
    half, stroke, fg
  )
  strokeRoundRect(
    Rect(recents.first - half, recents.second - half, recents.first + half, recents.second + half),
    dp(2f), stroke, fg
  )
}

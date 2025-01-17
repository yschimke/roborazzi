package com.github.takahirom.roborazzi

import com.dropbox.differ.Color
import com.dropbox.differ.Image
import java.awt.image.BufferedImage

internal class DifferBufferedImage(private val bufferedImage: BufferedImage) : Image {
  override val height: Int
    get() = bufferedImage.height
  override val width: Int
    get() = bufferedImage.width

  override fun getPixel(x: Int, y: Int): Color {
    return Color(bufferedImage.getRGB(x, y))
  }
}
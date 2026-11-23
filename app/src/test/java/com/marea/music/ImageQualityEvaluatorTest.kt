package com.marea.music

import com.marea.music.core.image.ImageQualityEvaluator
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageQualityEvaluatorTest {

    @Test
    fun `computePsnr returns high value for nearly identical images`() {
        val ref = IntArray(100) { 0xFF808080.toInt() }
        val test = IntArray(100) { 0xFF818080.toInt() }
        val psnr = ImageQualityEvaluator.computePsnr(ref, test)
        assertTrue("Expected PSNR >= 45 dB, got $psnr", psnr >= 45.0)
    }

    @Test
    fun `computePsnr drops below 20 dB for heavily noisy or pixelated diffs`() {
        val ref = IntArray(100) { 0xFF202020.toInt() }
        val test = IntArray(100) { 0xFF606060.toInt() }
        val psnr = ImageQualityEvaluator.computePsnr(ref, test)
        assertTrue("Expected PSNR < 20 dB, got $psnr", psnr < 20.0)
    }
}

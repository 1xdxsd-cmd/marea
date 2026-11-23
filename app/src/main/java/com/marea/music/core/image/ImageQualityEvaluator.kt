package com.marea.music.core.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import kotlin.math.log10
import kotlin.math.min

data class CoverQualityResult(
    val psnrDb: Double,
    val width: Int,
    val height: Int,
    val isAcceptable: Boolean
)

object ImageQualityEvaluator {

    const val MIN_ACCEPTABLE_PSNR_DB = 20.0
    private const val EVAL_SIZE = 256
    private const val DOWNSAMPLED_SIZE = 128

    /**
     * Standard Peak Signal-to-Noise Ratio (dB) between two same-sized ARGB pixel buffers.
     */
    fun computePsnr(referencePixels: IntArray, testPixels: IntArray): Double {
        require(referencePixels.size == testPixels.size) {
            "Pixel arrays must have equal length"
        }
        if (referencePixels.isEmpty()) return 0.0

        var sumSqError = 0.0
        for (i in referencePixels.indices) {
            val p1 = referencePixels[i]
            val p2 = testPixels[i]

            val dr = ((p1 shr 16) and 0xFF) - ((p2 shr 16) and 0xFF)
            val dg = ((p1 shr 8) and 0xFF) - ((p2 shr 8) and 0xFF)
            val db = (p1 and 0xFF) - (p2 and 0xFF)

            sumSqError += (dr * dr + dg * dg + db * db).toDouble()
        }

        val mse = sumSqError / (3.0 * referencePixels.size)
        if (mse <= 1e-6) return 60.0
        return 10.0 * log10((255.0 * 255.0) / mse)
    }

    /**
     * Evaluates a downloaded cover image's effective PSNR (dB) to reject pixelated,
     * tiny, or heavily artifacted images (< 20 dB).
     */
    fun evaluateBitmap(
        bitmap: Bitmap,
        minPsnrDb: Double = MIN_ACCEPTABLE_PSNR_DB
    ): CoverQualityResult {
        val width = bitmap.width
        val height = bitmap.height
        val minDim = min(width, height)

        val evalBitmap = Bitmap.createScaledBitmap(bitmap, EVAL_SIZE, EVAL_SIZE, true)
        val downBitmap = Bitmap.createScaledBitmap(evalBitmap, DOWNSAMPLED_SIZE, DOWNSAMPLED_SIZE, true)
        val reconstructed = Bitmap.createScaledBitmap(downBitmap, EVAL_SIZE, EVAL_SIZE, true)

        val evalPixels = IntArray(EVAL_SIZE * EVAL_SIZE)
        val reconPixels = IntArray(EVAL_SIZE * EVAL_SIZE)
        evalBitmap.getPixels(evalPixels, 0, EVAL_SIZE, 0, 0, EVAL_SIZE, EVAL_SIZE)
        reconstructed.getPixels(reconPixels, 0, EVAL_SIZE, 0, 0, EVAL_SIZE, EVAL_SIZE)

        if (evalBitmap !== bitmap) evalBitmap.recycle()
        downBitmap.recycle()
        reconstructed.recycle()

        val rawPsnr = computePsnr(evalPixels, reconPixels)
        // Penalize low-resolution sources (< 300px) that become pixelated when displayed as HD cover art
        val resolutionPenalty = if (minDim < 300) (300 - minDim) / 15.0 else 0.0
        val effectivePsnr = (rawPsnr - resolutionPenalty).coerceAtLeast(0.0)

        return CoverQualityResult(
            psnrDb = effectivePsnr,
            width = width,
            height = height,
            isAcceptable = effectivePsnr >= minPsnrDb && minDim >= 200
        )
    }

    fun evaluateFile(
        file: File,
        minPsnrDb: Double = MIN_ACCEPTABLE_PSNR_DB
    ): CoverQualityResult? {
        if (!file.exists() || file.length() == 0L) return null
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        return try {
            evaluateBitmap(bitmap, minPsnrDb)
        } finally {
            bitmap.recycle()
        }
    }
}

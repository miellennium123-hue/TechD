package com.guardianangel.core

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.roundToInt

/** Checks a proof photo entirely on the phone. Nothing is ever uploaded. */
object PhotoVerifier {
    /** Returns null if the photo passes. Call off the main thread. */
    fun verify(context: Context, file: File, explicit: Boolean): PhotoIssue? {
        val bitmap = ProofFiles.load(file, 640) ?: return PhotoIssue.UNREADABLE
        val w = 256
        val h = (w * bitmap.height.toFloat() / bitmap.width).roundToInt().coerceAtLeast(3)
        val small = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        val gray = IntArray(pixels.size) { i ->
            val c = pixels[i]
            (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)).toInt()
        }
        PhotoQuality.check(gray, w, h)?.let { return it }

        if (explicit) {
            // If the detector can't run, don't punish the user for it.
            val isExplicit = ExplicitDetector.isExplicit(context, bitmap) ?: return null
            if (!isExplicit) return PhotoIssue.NOT_EXPLICIT
        }
        return null
    }
}

/** NudeNet 320n (YOLOv8) running through ONNX Runtime. Model lives in assets/models. */
object ExplicitDetector {
    private const val MODEL = "models/nudenet_320n.onnx"
    private const val SIZE = 320
    private var session: OrtSession? = null

    @Synchronized
    private fun session(context: Context): OrtSession? {
        if (session == null) {
            session = runCatching {
                val bytes = context.assets.open(MODEL).use { it.readBytes() }
                OrtEnvironment.getEnvironment().createSession(bytes, OrtSession.SessionOptions())
            }.getOrNull()
        }
        return session
    }

    /** True or false, or null if the model couldn't run. */
    fun isExplicit(context: Context, bitmap: Bitmap): Boolean? = runCatching {
        val s = session(context) ?: return null
        // Pad to a square (bottom and right, black), then scale, matching NudeNet's preprocessing.
        val side = max(bitmap.width, bitmap.height)
        val square = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        Canvas(square).apply {
            drawColor(Color.BLACK)
            drawBitmap(bitmap, 0f, 0f, null)
        }
        val input = Bitmap.createScaledBitmap(square, SIZE, SIZE, true)
        val pixels = IntArray(SIZE * SIZE)
        input.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        val plane = SIZE * SIZE
        val data = FloatArray(3 * plane)
        for (i in 0 until plane) {
            val c = pixels[i]
            data[i] = Color.red(c) / 255f
            data[plane + i] = Color.green(c) / 255f
            data[2 * plane + i] = Color.blue(c) / 255f
        }
        val env = OrtEnvironment.getEnvironment()
        OnnxTensor.createTensor(env, FloatBuffer.wrap(data), longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong())).use { tensor ->
            s.run(mapOf(s.inputNames.first() to tensor)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val output = (result[0].value as Array<Array<FloatArray>>)[0]
                ExplicitScore.isExplicit(output)
            }
        }
    }.getOrNull()
}

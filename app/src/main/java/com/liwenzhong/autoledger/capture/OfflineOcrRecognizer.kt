package com.liwenzhong.autoledger.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

internal class OfflineOcrRecognizer(private val context: Context) {
    fun recognize(bitmap: Bitmap): List<String> {
        val dataDirectory = File(context.filesDir, "tesseract")
        val model = File(dataDirectory, "tessdata/chi_sim.traineddata")
        if (!model.exists()) {
            model.parentFile?.mkdirs()
            val temporaryModel = File(model.parentFile, "${model.name}.tmp")
            context.assets.open("tessdata/chi_sim.traineddata").use { input ->
                temporaryModel.outputStream().use { output -> input.copyTo(output) }
            }
            if (!temporaryModel.renameTo(model)) {
                temporaryModel.copyTo(model, overwrite = true)
                temporaryModel.delete()
            }
        }

        val engine = TessBaseAPI()
        var prepared: Bitmap? = null
        try {
            check(engine.init(dataDirectory.absolutePath, "chi_sim")) {
                "本地 OCR 模型初始化失败"
            }
            // Payment screens contain isolated large amounts, not a continuous document block.
            engine.pageSegMode = TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT
            // Normalize dark UI to dark text on white without persisting another image.
            val invert = shouldInvertOcrBackground(bitmap.getPixel(0, bitmap.height / 2),
                bitmap.getPixel(bitmap.width - 1, bitmap.height / 2))
            val input = if (invert) {
                Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).also {
                    prepared = it
                    Canvas(it).drawBitmap(bitmap, 0f, 0f, Paint().apply {
                        colorFilter = ColorMatrixColorFilter(floatArrayOf(
                            -1f, 0f, 0f, 0f, 255f,
                            0f, -1f, 0f, 0f, 255f,
                            0f, 0f, -1f, 0f, 255f,
                            0f, 0f, 0f, 1f, 0f,
                        ))
                    })
                }
            } else bitmap
            engine.setImage(input)
            return OcrTextFormatter.lines(engine.utF8Text.orEmpty())
        } finally {
            engine.recycle()
            prepared?.recycle()
        }
    }
}

internal fun shouldInvertOcrBackground(left: Int, right: Int): Boolean {
    fun isDark(color: Int): Boolean =
        ((color shr 16 and 255) * 299 + (color shr 8 and 255) * 587 + (color and 255) * 114) < 100_000
    return isDark(left) && isDark(right)
}

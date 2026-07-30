package com.liwenzhong.autoledger.capture

import android.content.Context
import android.graphics.Bitmap
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
        try {
            check(engine.init(dataDirectory.absolutePath, "chi_sim")) {
                "本地 OCR 模型初始化失败"
            }
            engine.setImage(bitmap)
            return OcrTextFormatter.lines(engine.utF8Text.orEmpty())
        } finally {
            engine.recycle()
        }
    }
}

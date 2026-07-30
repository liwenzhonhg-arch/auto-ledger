package com.liwenzhong.autoledger.capture

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import com.liwenzhong.autoledger.MainActivity
import com.liwenzhong.autoledger.R
import java.util.concurrent.Executors

private const val CHANNEL_ID = "one_shot_capture"
private const val NOTIFICATION_ID = 1101
private const val CAPTURE_DELAY_MS = 1_500L
private const val CAPTURE_TIMEOUT_MS = 12_000L

class OneShotCaptureService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ocrExecutor = Executors.newSingleThreadExecutor()
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captured = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val resultData = intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        if (resultCode != Activity.RESULT_OK || resultData == null) {
            finishWithError("屏幕捕获授权无效")
            return START_NOT_STICKY
        }

        startCaptureForeground()
        OcrCaptureStore.capturing()

        runCatching {
            beginCapture(resultCode, resultData)
        }.onFailure {
            finishWithError("无法启动屏幕捕获")
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseProjection()
        mainHandler.removeCallbacksAndMessages(null)
        ocrExecutor.shutdown()
        super.onDestroy()
    }

    private fun startCaptureForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.capture_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.capture_notification_title))
            .setContentText("只处理一帧，不保存截图")
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
        )
    }

    private fun beginCapture(resultCode: Int, resultData: Intent) {
        val bounds = getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        val width = bounds.width()
        val height = bounds.height()
        val density = resources.configuration.densityDpi
        val captureAfter = SystemClock.elapsedRealtime() + CAPTURE_DELAY_MS

        val manager = getSystemService(MediaProjectionManager::class.java)
        projection = requireNotNull(manager.getMediaProjection(resultCode, resultData)) {
            "MediaProjection unavailable"
        }.also {
            it.registerCallback(
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        if (!captured) finishWithError("屏幕捕获已停止")
                    }
                },
                mainHandler,
            )
        }

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).also { reader ->
            reader.setOnImageAvailableListener(
                { source ->
                    val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
                    if (captured || SystemClock.elapsedRealtime() < captureAfter) {
                        image.close()
                        return@setOnImageAvailableListener
                    }
                    captured = true
                    source.setOnImageAvailableListener(null, null)
                    processImage(image, width, height)
                },
                mainHandler,
            )
        }

        virtualDisplay = projection?.createVirtualDisplay(
            "AutoLedgerOneShotCapture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            mainHandler,
        )

        mainHandler.postDelayed(
            { if (!captured) finishWithError("等待微信页面超时，请重试") },
            CAPTURE_TIMEOUT_MS,
        )
    }

    private fun processImage(image: Image, width: Int, height: Int) {
        val bitmap = runCatching { image.toBitmap(width, height) }
            .also { image.close() }
            .getOrElse {
                finishWithError("无法读取屏幕图像")
                return
            }
        releaseProjection()
        OcrCaptureStore.recognizing()

        ocrExecutor.execute {
            val result = runCatching { OfflineOcrRecognizer(applicationContext).recognize(bitmap) }
            bitmap.recycle()
            mainHandler.post {
                result.onSuccess(OcrCaptureStore::success)
                    .onFailure { OcrCaptureStore.fail("本地文字识别失败") }
                stopSelf()
            }
        }
    }

    private fun finishWithError(message: String) {
        mainHandler.post {
            OcrCaptureStore.fail(message)
            stopSelf()
        }
    }

    private fun releaseProjection() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        projection?.stop()
        projection = null
    }

    companion object {
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"

        fun start(context: Context, resultCode: Int, resultData: Intent) {
            context.startForegroundService(
                Intent(context, OneShotCaptureService::class.java)
                    .putExtra(EXTRA_RESULT_CODE, resultCode)
                    .putExtra(EXTRA_RESULT_DATA, resultData),
            )
        }
    }
}

private fun Image.toBitmap(width: Int, height: Int): Bitmap {
    val plane = planes.first()
    val paddedWidth = width + (plane.rowStride - plane.pixelStride * width) / plane.pixelStride
    val padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
    plane.buffer.rewind()
    padded.copyPixelsFromBuffer(plane.buffer)
    if (paddedWidth == width) return padded

    return Bitmap.createBitmap(padded, 0, 0, width, height).also { padded.recycle() }
}

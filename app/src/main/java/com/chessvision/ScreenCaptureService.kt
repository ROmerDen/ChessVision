package com.chessvision

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock

class ScreenCaptureService : Service() {

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "projection_data"

        private const val CHANNEL_ID = "screen_capture"
        private const val NOTIFICATION_ID = 101
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var workerThread: HandlerThread? = null
    private var workerHandler: Handler? = null
    private var projectionCallback: MediaProjection.Callback? = null

    private var lastFrameTime = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)

        val data = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_DATA)
        }

        if (data == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            startForegroundCompat()

            val manager =
                getSystemService(MediaProjectionManager::class.java)

            val mediaProjection =
                manager.getMediaProjection(resultCode, data)

            projection = mediaProjection

            workerThread = HandlerThread("ChessVisionCapture").also {
                it.start()
            }

            workerHandler = Handler(workerThread!!.looper)

            val callback = object : MediaProjection.Callback() {
                override fun onStop() {
                    stopSelf()
                }
            }

            projectionCallback = callback
            mediaProjection.registerCallback(callback, workerHandler)

            startCapture(mediaProjection)

        } catch (e: Exception) {
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun startCapture(mediaProjection: MediaProjection) {
        val metrics = resources.displayMetrics

        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val reader = ImageReader.newInstance(
            width,
            height,
            android.graphics.PixelFormat.RGBA_8888,
            2
        )

        imageReader = reader

        reader.setOnImageAvailableListener({ source ->
            processImage(source)
        }, workerHandler)

        virtualDisplay = mediaProjection.createVirtualDisplay(
            "ChessVisionDisplay",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            workerHandler
        )
    }

    private fun processImage(reader: ImageReader) {
        var image: Image? = null

        try {
            image = reader.acquireLatestImage() ?: return

            val now = SystemClock.elapsedRealtime()

            if (now - lastFrameTime < 1000L) {
                return
            }

            lastFrameTime = now

            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride

            val paddedWidth = rowStride / pixelStride

            val paddedBitmap = Bitmap.createBitmap(
                paddedWidth,
                image.height,
                Bitmap.Config.ARGB_8888
            )

            paddedBitmap.copyPixelsFromBuffer(buffer)

            val frame = Bitmap.createBitmap(
                paddedBitmap,
                0,
                0,
                image.width,
                image.height
            )

            if (frame !== paddedBitmap) {
                paddedBitmap.recycle()
            }

            ScreenCaptureState.publish(frame)

        } catch (_: Exception) {
            // Ошибки отдельных кадров не должны останавливать сервис.
        } finally {
            image?.close()
        }
    }

    private fun startForegroundCompat() {
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("ChessVision")
            .setContentText("Захват экрана активен")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Захват экрана",
                NotificationManager.IMPORTANCE_LOW
            )

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        imageReader?.setOnImageAvailableListener(null, null)

        virtualDisplay?.release()
        imageReader?.close()

        projection?.let { p ->
            projectionCallback?.let {
                p.unregisterCallback(it)
            }
            p.stop()
        }

        workerThread?.quitSafely()

        virtualDisplay = null
        imageReader = null
        projection = null
        workerHandler = null
        workerThread = null

        ScreenCaptureState.clear()

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

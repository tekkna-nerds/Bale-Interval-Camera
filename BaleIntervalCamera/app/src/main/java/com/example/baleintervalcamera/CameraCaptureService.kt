package com.example.baleintervalcamera

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageCapture.OutputFileOptions
import androidx.camera.core.ImageCapture.OutputFileResults
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraCaptureService : LifecycleService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val uploadExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var orientationListener: OrientationEventListener? = null
    private var targetRotation = Surface.ROTATION_0
    private var botToken: String = ""
    private var chatId: String = ""
    private var active = false
    private var captureBusy = false
    private var capturedPhotoCount = 0
    private var sentPhotoCount = 0
    private var hasStartedSession = false
    private var captureIntervalMillis = AppSettings.DEFAULT_INTERVAL_MILLIS

    private val captureTicker = object : Runnable {
        override fun run() {
            if (!active) return
            if (!captureBusy) captureAndSend()
            mainHandler.postDelayed(this, captureIntervalMillis)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_UPDATE_INTERVAL) {
            updateCaptureInterval(intent.getLongExtra(EXTRA_INTERVAL_MILLIS, captureIntervalMillis))
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            stopSession()
            return START_NOT_STICKY
        }

        botToken = BuildConfig.BALE_BOT_TOKEN
        chatId = intent?.getStringExtra(EXTRA_CHAT_ID).orEmpty()
        captureIntervalMillis = intent?.getLongExtra(EXTRA_INTERVAL_MILLIS, AppSettings.intervalMillis(this))
            ?: AppSettings.intervalMillis(this)
        if (botToken.isBlank() || chatId.isBlank() ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        active = true
        capturedPhotoCount = 0
        sentPhotoCount = 0
        hasStartedSession = true
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putBoolean(PREF_RUNNING, true)
            .putInt(PREF_PROCESS_ID, android.os.Process.myPid())
            .putInt(PREF_CURRENT_PHOTO_COUNT, 0)
            .apply()
        val notification = buildNotification(AppSettings.text(this, "در حال باز کردن دوربین…", "Opening camera…"))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startOrientationTracking()
        openCamera()
        return START_NOT_STICKY
    }

    private fun startOrientationTracking() {
        if (orientationListener != null) return
        orientationListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                targetRotation = when (orientation) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                imageCapture?.targetRotation = targetRotation
            }
        }.also { listener ->
            if (listener.canDetectOrientation()) listener.enable()
        }
    }

    private fun stopOrientationTracking() {
        orientationListener?.disable()
        orientationListener = null
    }

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            if (!active) return@addListener
            try {
                val provider = providerFuture.get()
                cameraProvider = provider
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setJpegQuality(75)
                    .setTargetRotation(targetRotation)
                    .build()
                imageCapture = capture
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, capture)
                updateNotification(captureStatusMessage())
                mainHandler.removeCallbacks(captureTicker)
                captureTicker.run()
            } catch (_: Exception) {
                updateNotification(AppSettings.text(
                    this,
                    "دوربین راه‌اندازی نشد. برنامه را باز کنید و مجوز دوربین را بررسی کنید.",
                    "Camera could not start. Open the app and check camera permission."
                ))
                stopSession()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureAndSend() {
        val capture = imageCapture ?: return
        captureBusy = true
        val photoFile = File(cacheDir, "bale_capture_${System.currentTimeMillis()}.jpg")
        val output = OutputFileOptions.Builder(photoFile).build()
        capture.takePicture(output, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: OutputFileResults) {
                capturedPhotoCount += 1
                saveCapturedPhotoCount()
                if (!active) {
                    photoFile.delete()
                    captureBusy = false
                    return
                }
                uploadExecutor.execute {
                    val result = try {
                        sendPhoto(photoFile)
                        val sentCount = ++sentPhotoCount
                        AppSettings.text(
                            this@CameraCaptureService,
                            "تصویر شمارهٔ ${toPersianDigits(sentCount)} به بله فرستاده شد",
                            "Photo $sentCount sent to Bale"
                        )
                    } catch (_: Exception) {
                        AppSettings.text(this@CameraCaptureService, "ارسال ناموفق بود. اتصال اینترنت و اتصال ربات بله را بررسی کنید.", "Send failed. Check your internet connection and Bale bot connection.")
                    } finally {
                        photoFile.delete()
                    }
                    mainHandler.post {
                        captureBusy = false
                        if (active) updateNotification(result)
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                photoFile.delete()
                captureBusy = false
                if (active) updateNotification(AppSettings.text(this@CameraCaptureService, "گرفتن تصویر ناموفق بود. مجوز دوربین را بررسی کنید.", "Photo capture failed. Check camera permission."))
            }
        })
    }

    @Throws(IOException::class)
    private fun sendPhoto(photoFile: File) {
        val boundary = "BaleBoundary${System.currentTimeMillis()}"
        val connection = (URL("https://tapi.bale.ai/bot$botToken/sendPhoto").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        try {
            DataOutputStream(connection.outputStream).use { output ->
                writeTextPart(output, boundary, "chat_id", chatId)
                val capturedAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val caption = AppSettings.text(this, "تصویر ثبت‌شده · $capturedAt", "Photo captured · $capturedAt")
                writeTextPart(output, boundary, "caption", caption)
                output.writeBytes("--$boundary\r\n")
                output.writeBytes("Content-Disposition: form-data; name=\"photo\"; filename=\"capture.jpg\"\r\n")
                output.writeBytes("Content-Type: image/jpeg\r\n\r\n")
                FileInputStream(photoFile).use { input -> input.copyTo(output) }
                output.writeBytes("\r\n--$boundary--\r\n")
                output.flush()
            }

            val code = connection.responseCode
            val responseStream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseBody = responseStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val ok = runCatching { JSONObject(responseBody).optBoolean("ok", false) }.getOrDefault(false)
            if (code !in 200..299 || !ok) throw IOException("Bale sendPhoto request failed")
        } finally {
            connection.disconnect()
        }
    }

    private fun writeTextPart(output: DataOutputStream, boundary: String, name: String, value: String) {
        output.writeBytes("--$boundary\r\n")
        output.writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
        output.write(value.toByteArray(Charsets.UTF_8))
        output.writeBytes("\r\n")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                AppSettings.text(this, "جلسهٔ تصویربرداری", "Camera capture session"),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = AppSettings.text(
                    this@CameraCaptureService,
                    "وضعیت قابل‌مشاهده و امکان توقف هنگام فعال بودن دوربین",
                    "Visible status and stop control while the camera is active"
                )
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = Intent(this, CameraCaptureService::class.java).apply { action = ACTION_STOP }
        val stopAction = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle(AppSettings.text(this, "تصویربرداری فعال است", "Capture is active"))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_pause, AppSettings.text(this, "توقف", "Stop"), stopAction)
            .build()
    }

    private fun updateNotification(message: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(message))
    }

    private fun captureStatusMessage(): String = AppSettings.text(
        this,
        "در حال تصویربرداری و ارسال به بله · ${AppSettings.intervalLabel(this, captureIntervalMillis)}",
        "Capturing and sending to Bale · ${AppSettings.intervalLabel(this, captureIntervalMillis)}"
    )

    private fun updateCaptureInterval(intervalMillis: Long) {
        if (!active || intervalMillis !in AppSettings.INTERVAL_OPTIONS) return
        captureIntervalMillis = intervalMillis
        mainHandler.removeCallbacks(captureTicker)
        mainHandler.postDelayed(captureTicker, captureIntervalMillis)
        updateNotification(captureStatusMessage())
    }

    private fun toPersianDigits(value: Int): String = AppSettings.persianDigits(value)

    private fun saveCapturedPhotoCount() {
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val edit = preferences.edit().putInt(PREF_CURRENT_PHOTO_COUNT, capturedPhotoCount)
        val sessionHasEnded = !active || !hasStartedSession
        if (sessionHasEnded) edit.putInt(PREF_LAST_PHOTO_COUNT, capturedPhotoCount)
        edit.apply()
        if (sessionHasEnded) notifyLastSessionCountChanged()
    }

    private fun finishSession() {
        if (!hasStartedSession) return
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putInt(PREF_LAST_PHOTO_COUNT, capturedPhotoCount)
            .putInt(PREF_CURRENT_PHOTO_COUNT, capturedPhotoCount)
            .putBoolean(PREF_RUNNING, false)
            .putInt(PREF_PROCESS_ID, -1)
            .commit()
        hasStartedSession = false
        notifyLastSessionCountChanged()
    }

    private fun notifyLastSessionCountChanged() {
        sendBroadcast(Intent(ACTION_SESSION_COUNT_UPDATED).setPackage(packageName))
    }

    private fun stopSession() {
        active = false
        finishSession()
        mainHandler.removeCallbacks(captureTicker)
        stopOrientationTracking()
        cameraProvider?.unbindAll()
        imageCapture = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        active = false
        finishSession()
        mainHandler.removeCallbacks(captureTicker)
        stopOrientationTracking()
        cameraProvider?.unbindAll()
        uploadExecutor.shutdownNow()
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putBoolean(PREF_RUNNING, false)
            .putInt(PREF_PROCESS_ID, -1)
            .apply()
        botToken = ""
        chatId = ""
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.example.baleintervalcamera.STOP"
        const val ACTION_UPDATE_INTERVAL = "com.example.baleintervalcamera.UPDATE_INTERVAL"
        const val ACTION_SESSION_COUNT_UPDATED = "com.example.baleintervalcamera.SESSION_COUNT_UPDATED"
        const val EXTRA_CHAT_ID = "chat_id"
        const val EXTRA_INTERVAL_MILLIS = "interval_millis"
        const val PREFS_NAME = "capture_session"
        const val PREF_RUNNING = "running"
        const val PREF_PROCESS_ID = "process_id"
        const val PREF_LAST_PHOTO_COUNT = "last_photo_count"
        const val PREF_CURRENT_PHOTO_COUNT = "current_photo_count"
        private const val CHANNEL_ID = "camera_capture"
        private const val NOTIFICATION_ID = 4103
    }
}

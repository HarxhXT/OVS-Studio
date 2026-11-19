package com.harxhxt.ovsstudio

import android.app.*
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingService : Service() {

    private var recorder: MediaRecorder? = null
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var outputUri: Uri? = null
    private var outputDescriptor: ParcelFileDescriptor? = null

    private var elapsedSeconds = 0L
    private var isPaused = false
    private var isRecording = false

    private val handler = Handler(Looper.getMainLooper())

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isRecording && !isPaused) {
                elapsedSeconds++
                sendStatus("RECORDING", "Recording")
                handler.postDelayed(this, 1000)
            }
        }
    }

    private val projectionCallback =
        object : MediaProjection.Callback() {
            override fun onStop() {
                stopRecording()
            }
        }

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {

            RecordingManager.ACTION_START -> {
                val resultCode = intent.getIntExtra(
                    RecordingManager.EXTRA_RESULT_CODE,
                    Activity.RESULT_CANCELED
                )

                val data = getProjectionIntent(intent)

                if (data != null) {
                    startForegroundSafely()
                    startRecording(resultCode, data)
                } else {
                    stopSelf()
                }
            }

            RecordingManager.ACTION_PAUSE -> pauseRecording()

            RecordingManager.ACTION_RESUME -> resumeRecording()

            RecordingManager.ACTION_STOP -> stopRecording()
        }

        return START_NOT_STICKY
    }

    private fun getProjectionIntent(intent: Intent): Intent? {
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(
                RecordingManager.EXTRA_DATA,
                Intent::class.java
            )
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(
                RecordingManager.EXTRA_DATA
            )
        }
    }

    private fun startForegroundSafely() {
        val notification = buildNotification("Preparing recording")

        if (Build.VERSION.SDK_INT >= 29) {
            var type =
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION

            if (Build.VERSION.SDK_INT >= 34) {
                type = type or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }

            startForeground(
                101,
                notification,
                type
            )
        } else {
            startForeground(101, notification)
        }
    }

    private fun startRecording(
        resultCode: Int,
        data: Intent
    ) {
        try {
            val manager = getSystemService(
                MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager

            projection = manager.getMediaProjection(
                resultCode,
                data
            )

            val metrics = DisplayMetrics()

            val displayManager =
                getSystemService(WINDOW_SERVICE) as WindowManager

            @Suppress("DEPRECATION")
            displayManager.defaultDisplay.getRealMetrics(metrics)

            val width = metrics.widthPixels and -2
            val height = metrics.heightPixels and -2
            val density = metrics.densityDpi

            createOutputFile()

            val fileDescriptor = outputDescriptor
                ?: throw IllegalStateException(
                    "Could not create video file"
                )

            val videoRecorder = if (
                Build.VERSION.SDK_INT >= 31
            ) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder = videoRecorder

            videoRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)

                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)

                setVideoSize(width, height)
                setVideoFrameRate(30)
                setVideoEncodingBitRate(5_000_000)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44100)

                setOutputFile(fileDescriptor.fileDescriptor)

                prepare()
            }

            projection?.registerCallback(
                projectionCallback,
                handler
            )

            virtualDisplay = projection?.createVirtualDisplay(
                "OVS Studio Recording",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                videoRecorder.surface,
                null,
                handler
            )

            videoRecorder.start()

            elapsedSeconds = 0
            isPaused = false
            isRecording = true

            handler.post(timerRunnable)

            updateNotification("Recording screen")
            sendStatus("RECORDING", "Recording started")

        } catch (e: Exception) {
            sendStatus(
                "ERROR",
                e.message ?: "Recording could not start"
            )
            cleanup(true)
            stopSelf()
        }
    }

    private fun createOutputFile() {
        val timestamp = SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            Locale.US
        ).format(Date())

        val values = ContentValues().apply {
            put(
                MediaStore.Video.Media.DISPLAY_NAME,
                "OVS_$timestamp.mp4"
            )
            put(
                MediaStore.Video.Media.MIME_TYPE,
                "video/mp4"
            )

            if (Build.VERSION.SDK_INT >= 29) {
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    "Movies/OVS Studio"
                )
            } else {
                @Suppress("DEPRECATION")
                val directory = File(
                    Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_MOVIES
                    ),
                    "OVS Studio"
                )

                if (!directory.exists()) {
                    directory.mkdirs()
                }

                put(
                    MediaStore.Video.Media.DATA,
                    File(
                        directory,
                        "OVS_$timestamp.mp4"
                    ).absolutePath
                )
            }
        }

        outputUri = contentResolver.insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            values
        ) ?: throw IllegalStateException(
            "Gallery file could not be created"
        )

        outputDescriptor = contentResolver.openFileDescriptor(
            outputUri!!,
            "w"
        ) ?: throw IllegalStateException(
            "Video file could not be opened"
        )
    }

    private fun pauseRecording() {
        if (!isRecording || isPaused) return

        try {
            if (Build.VERSION.SDK_INT >= 24) {
                recorder?.pause()
                isPaused = true
                handler.removeCallbacks(timerRunnable)
                updateNotification("Recording paused")
                sendStatus("PAUSED", "Recording paused")
            }
        } catch (e: Exception) {
            sendStatus("ERROR", "Pause failed")
        }
    }

    private fun resumeRecording() {
        if (!isRecording || !isPaused) return

        try {
            if (Build.VERSION.SDK_INT >= 24) {
                recorder?.resume()
                isPaused = false
                handler.post(timerRunnable)
                updateNotification("Recording screen")
                sendStatus("RECORDING", "Recording resumed")
            }
        } catch (e: Exception) {
            sendStatus("ERROR", "Resume failed")
        }
    }

    private fun stopRecording() {
        if (!isRecording) {
            cleanup(false)
            stopSelf()
            return
        }

        isRecording = false
        handler.removeCallbacks(timerRunnable)

        var saved = false

        try {
            recorder?.stop()
            saved = true
        } catch (_: Exception) {
            saved = false
        }

        cleanup(!saved)

        sendStatus(
            if (saved) "STOPPED" else "ERROR",
            if (saved) "Video saved to Movies/OVS Studio"
            else "Recording was too short or could not be saved"
        )

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanup(deleteFile: Boolean) {
        handler.removeCallbacks(timerRunnable)

        try {
            virtualDisplay?.release()
        } catch (_: Exception) {
        }

        virtualDisplay = null

        try {
            recorder?.reset()
            recorder?.release()
        } catch (_: Exception) {
        }

        recorder = null

        try {
            projection?.unregisterCallback(projectionCallback)
            projection?.stop()
        } catch (_: Exception) {
        }

        projection = null

        try {
            outputDescriptor?.close()
        } catch (_: Exception) {
        }

        outputDescriptor = null

        if (deleteFile) {
            outputUri?.let {
                contentResolver.delete(it, null, null)
            }
        }

        outputUri = null
        isRecording = false
        isPaused = false
    }

    private fun sendStatus(
        status: String,
        message: String
    ) {
        val intent = Intent(
            RecordingManager.ACTION_STATUS
        ).apply {
            setPackage(packageName)
            putExtra(RecordingManager.EXTRA_STATUS, status)
            putExtra(RecordingManager.EXTRA_SECONDS, elapsedSeconds)
            putExtra(RecordingManager.EXTRA_MESSAGE, message)
        }

        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                "ovs_recording",
                "OVS Recording",
                NotificationManager.IMPORTANCE_LOW
            )

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        message: String
    ): Notification {
        val stopIntent = Intent(
            this,
            RecordingService::class.java
        ).apply {
            action = RecordingManager.ACTION_STOP
        }

        val stopPendingIntent = PendingIntent.getService(
            this,
            102,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, "ovs_recording")
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("OVS Studio")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop",
                stopPendingIntent
            )
            .build()
    }

    private fun updateNotification(message: String) {
        val notification = buildNotification(message)

        getSystemService(NotificationManager::class.java)
            .notify(101, notification)
    }

    override fun onDestroy() {
        cleanup(false)
        super.onDestroy()
    }
}

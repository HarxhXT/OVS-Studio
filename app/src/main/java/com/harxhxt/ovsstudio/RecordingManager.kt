package com.harxhxt.ovsstudio

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build

object RecordingManager {

    const val ACTION_START =
        "com.harxhxt.ovsstudio.ACTION_START_RECORDING"

    const val ACTION_PAUSE =
        "com.harxhxt.ovsstudio.ACTION_PAUSE_RECORDING"

    const val ACTION_RESUME =
        "com.harxhxt.ovsstudio.ACTION_RESUME_RECORDING"

    const val ACTION_STOP =
        "com.harxhxt.ovsstudio.ACTION_STOP_RECORDING"

    const val ACTION_STATUS =
        "com.harxhxt.ovsstudio.ACTION_RECORDING_STATUS"

    const val EXTRA_RESULT_CODE = "result_code"
    const val EXTRA_DATA = "projection_data"
    const val EXTRA_STATUS = "status"
    const val EXTRA_SECONDS = "seconds"
    const val EXTRA_MESSAGE = "message"

    fun createScreenCaptureIntent(
        context: Context
    ): Intent {
        val manager = context.getSystemService(
            Context.MEDIA_PROJECTION_SERVICE
        ) as MediaProjectionManager

        return manager.createScreenCaptureIntent()
    }

    fun startRecording(
        context: Context,
        resultCode: Int,
        data: Intent
    ) {
        val intent = Intent(
            context,
            RecordingService::class.java
        ).apply {
            action = ACTION_START
            putExtra(EXTRA_RESULT_CODE, resultCode)
            putExtra(EXTRA_DATA, data)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun pause(context: Context) {
        sendCommand(context, ACTION_PAUSE)
    }

    fun resume(context: Context) {
        sendCommand(context, ACTION_RESUME)
    }

    fun stop(context: Context) {
        sendCommand(context, ACTION_STOP)
    }

    private fun sendCommand(
        context: Context,
        action: String
    ) {
        val intent = Intent(
            context,
            RecordingService::class.java
        ).apply {
            this.action = action
        }

        context.startService(intent)
    }
}

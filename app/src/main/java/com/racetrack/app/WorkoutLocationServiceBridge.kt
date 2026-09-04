package com.racetrack.app

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Starts/stops the foreground location tracking owned by StepCountingService. */
object WorkoutLocationServiceBridge {
    fun start(context: Context) {
        val intent = Intent(context, StepCountingService::class.java).setAction(StepCountingService.ACTION_START_WORKOUT)
        ContextCompat.startForegroundService(context, intent)
    }

    fun pause(context: Context) {
        context.startService(Intent(context, StepCountingService::class.java).setAction(StepCountingService.ACTION_PAUSE_WORKOUT))
    }

    fun resume(context: Context) {
        context.startService(Intent(context, StepCountingService::class.java).setAction(StepCountingService.ACTION_RESUME_WORKOUT))
    }

    fun stop(context: Context) {
        context.startService(Intent(context, StepCountingService::class.java).setAction(StepCountingService.ACTION_STOP_WORKOUT))
    }
}

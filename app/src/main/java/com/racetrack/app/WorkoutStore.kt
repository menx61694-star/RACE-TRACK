package com.racetrack.app

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WorkoutStore(context: Context) {
    private val prefs = context.getSharedPreferences("workouts", Context.MODE_PRIVATE)

    fun saveStepSession(steps: Int, durationSeconds: Long) {
        val count = prefs.getInt("count", 0)
        val index = count + 1
        prefs.edit()
            .putInt("count", index)
            .putString("session_${index}_date", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()))
            .putInt("session_${index}_steps", steps)
            .putLong("session_${index}_duration", durationSeconds)
            .apply()
    }

    fun sessionCount(): Int = prefs.getInt("count", 0)
}

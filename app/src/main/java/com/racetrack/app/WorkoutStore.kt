package com.racetrack.app

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class WorkoutStore(context: Context) {
    private val prefs = context.getSharedPreferences("workouts", Context.MODE_PRIVATE)

    data class Summary(val steps: Int, val distanceMeters: Double, val calories: Double, val durationSeconds: Long)

    fun saveSession(steps: Int, durationSeconds: Long, distanceMeters: Double, calories: Double) {
        val count = prefs.getInt("count", 0) + 1
        prefs.edit()
            .putInt("count", count)
            .putLong("session_${count}_time", System.currentTimeMillis())
            .putString("session_${count}_date", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()))
            .putInt("session_${count}_steps", steps)
            .putLong("session_${count}_duration", durationSeconds)
            .putFloat("session_${count}_distance", distanceMeters.toFloat())
            .putFloat("session_${count}_calories", calories.toFloat())
            .apply()
    }

    fun saveStepSession(steps: Int, durationSeconds: Long) =
        saveSession(steps, durationSeconds, 0.0, 0.0)

    fun summary(period: Period): Summary {
        val now = System.currentTimeMillis()
        val start = Calendar.getInstance().apply {
            timeInMillis = now
            when (period) {
                Period.DAY -> { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
                Period.WEEK -> { set(Calendar.DAY_OF_WEEK, firstDayOfWeek); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
                Period.MONTH -> { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
                Period.YEAR -> { set(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
            }
        }.timeInMillis
        var result = Summary(0, 0.0, 0.0, 0L)
        val count = sessionCount()
        for (i in 1..count) {
            val time = prefs.getLong("session_${i}_time", parseLegacyDate(i))
            if (time >= start && time <= now) {
                result = Summary(
                    result.steps + prefs.getInt("session_${i}_steps", 0),
                    result.distanceMeters + prefs.getFloat("session_${i}_distance", 0f),
                    result.calories + prefs.getFloat("session_${i}_calories", 0f),
                    result.durationSeconds + prefs.getLong("session_${i}_duration", 0L)
                )
            }
        }
        return result
    }

    fun sessionCount(): Int = prefs.getInt("count", 0)

    private fun parseLegacyDate(index: Int): Long {
        val raw = prefs.getString("session_${index}_date", null) ?: return 0L
        return runCatching { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse(raw)?.time ?: 0L }.getOrDefault(0L)
    }
}

enum class Period { DAY, WEEK, MONTH, YEAR }

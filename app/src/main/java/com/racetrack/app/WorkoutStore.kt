package com.racetrack.app

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class WorkoutStore(context: Context) {
    data class Session(
        val date: Long,
        val steps: Int,
        val durationSeconds: Long,
        val distanceMeters: Float,
        val calories: Float,
        val activity: String
    )

    private val prefs = context.getSharedPreferences("workouts", Context.MODE_PRIVATE)

    fun saveSession(steps: Int, durationSeconds: Long, distanceMeters: Float, calories: Float, activity: String = "Walk") {
        if (durationSeconds <= 0L && steps <= 0 && distanceMeters <= 0f) return
        val count = prefs.getInt("count", 0) + 1
        prefs.edit()
            .putInt("count", count)
            .putLong("session_${count}_date", System.currentTimeMillis())
            .putInt("session_${count}_steps", steps)
            .putLong("session_${count}_duration", durationSeconds)
            .putFloat("session_${count}_distance", distanceMeters)
            .putFloat("session_${count}_calories", calories)
            .putString("session_${count}_activity", activity)
            .apply()
    }

    fun saveStepSession(steps: Int, durationSeconds: Long) = saveSession(steps, durationSeconds, 0f, 0f, "Walk")

    fun sessions(): List<Session> {
        val count = prefs.getInt("count", 0)
        return (1..count).map { i ->
            Session(
                prefs.getLong("session_${i}_date", 0L),
                prefs.getInt("session_${i}_steps", 0),
                prefs.getLong("session_${i}_duration", 0L),
                prefs.getFloat("session_${i}_distance", 0f),
                prefs.getFloat("session_${i}_calories", 0f),
                prefs.getString("session_${i}_activity", "Walk") ?: "Walk"
            )
        }.sortedBy { it.date }
    }

    fun totalSteps(): Int = sessions().sumOf { it.steps }
    fun totalDistanceMeters(): Float = sessions().sumOf { it.distanceMeters.toDouble() }.toFloat()

    fun forRange(start: Long, end: Long): List<Session> = sessions().filter { it.date in start until end }

    fun dayBuckets(days: Int): List<Int> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val today = cal.timeInMillis
        return (days - 1 downTo 0).map { offset ->
            val start = today - offset * 86_400_000L
            val end = start + 86_400_000L
            forRange(start, end).sumOf { it.steps }
        }
    }

    fun formatDate(time: Long): String = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(time))
}

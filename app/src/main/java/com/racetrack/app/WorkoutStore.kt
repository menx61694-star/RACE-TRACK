package com.racetrack.app

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WorkoutStore(context: Context) {
    data class Session(
        val date: Long,
        val steps: Int,
        val durationSeconds: Long,
        val distanceMeters: Float,
        val calories: Float,
        val activity: String,
        val laps: List<LapRecord> = emptyList()
    )

    private val prefs = context.getSharedPreferences("workouts", Context.MODE_PRIVATE)

    fun saveSession(steps: Int, durationSeconds: Long, distanceMeters: Float, calories: Float, activity: String = "Walk", laps: List<LapRecord> = emptyList()) {
        if (durationSeconds <= 0L && steps <= 0 && distanceMeters <= 0f) return
        val count = prefs.getInt("count", 0) + 1
        val lapString = laps.joinToString(";") { "${it.number},${it.distanceMeters},${it.elapsedSeconds}" }
        prefs.edit()
            .putInt("count", count)
            .putLong("session_${count}_date", System.currentTimeMillis())
            .putInt("session_${count}_steps", steps)
            .putLong("session_${count}_duration", durationSeconds)
            .putFloat("session_${count}_distance", distanceMeters)
            .putFloat("session_${count}_calories", calories)
            .putString("session_${count}_activity", activity)
            .putString("session_${count}_laps", lapString)
            .apply()
    }

    fun saveStepSession(steps: Int, durationSeconds: Long) = saveSession(steps, durationSeconds, 0f, 0f, "Walk")

    fun sessions(): List<Session> {
        val count = prefs.getInt("count", 0)
        return (1..count).map { i ->
            val lapString = prefs.getString("session_${i}_laps", "") ?: ""
            val laps = lapString.split(';').mapNotNull { raw ->
                val p = raw.split(',')
                if (p.size != 3) null else runCatching { LapRecord(p[0].toInt(), p[1].toFloat(), p[2].toLong()) }.getOrNull()
            }
            Session(
                prefs.getLong("session_${i}_date", 0L),
                prefs.getInt("session_${i}_steps", 0),
                prefs.getLong("session_${i}_duration", 0L),
                prefs.getFloat("session_${i}_distance", 0f),
                prefs.getFloat("session_${i}_calories", 0f),
                prefs.getString("session_${i}_activity", "Walk") ?: "Walk",
                laps
            )
        }.sortedBy { it.date }
    }

    fun forRange(start: Long, end: Long): List<Session> = sessions().filter { it.date in start until end }
    fun formatDate(time: Long): String = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(time))
}

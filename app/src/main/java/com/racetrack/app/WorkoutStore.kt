package com.racetrack.app

import android.content.Context
import android.location.Location
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class WorkoutStore(context: Context) {
    data class Session(
        val date: Long,
        val steps: Int,
        val durationSeconds: Long,
        val distanceMeters: Float,
        val calories: Float,
        val activity: String,
        val laps: List<LapRecord> = emptyList(),
        val route: List<Location> = emptyList(),
        val id: String = ""
    )

    private val prefs = context.getSharedPreferences("workouts", Context.MODE_PRIVATE)

    fun saveSession(steps: Int, durationSeconds: Long, distanceMeters: Float, calories: Float, activity: String = "Walk", laps: List<LapRecord> = emptyList(), route: List<Location> = emptyList()) {
        if (durationSeconds <= 0L && steps <= 0 && distanceMeters <= 0f) return
        val count = prefs.getInt("count", 0) + 1
        val id = UUID.randomUUID().toString()
        saveAtIndex(count, id, System.currentTimeMillis(), steps, durationSeconds, distanceMeters, calories, activity, laps, route)
        prefs.edit().putInt("count", count).apply()
    }

    private fun saveAtIndex(index: Int, id: String, date: Long, steps: Int, durationSeconds: Long, distanceMeters: Float, calories: Float, activity: String, laps: List<LapRecord>, route: List<Location>) {
        val savedRoute = if (route.isNotEmpty()) route else RouteReplaySession.route
        val lapString = laps.joinToString(";") { "${it.number},${it.distanceMeters},${it.elapsedSeconds}" }
        val routeString = savedRoute.joinToString(";") { "${it.latitude},${it.longitude}" }
        prefs.edit()
            .putString("session_${index}_id", id)
            .putLong("session_${index}_date", date)
            .putInt("session_${index}_steps", steps)
            .putLong("session_${index}_duration", durationSeconds)
            .putFloat("session_${index}_distance", distanceMeters)
            .putFloat("session_${index}_calories", calories)
            .putString("session_${index}_activity", activity)
            .putString("session_${index}_laps", lapString)
            .putString("session_${index}_route", routeString)
            .apply()
    }

    fun upsertSession(session: Session): Boolean {
        if (session.id.isBlank()) return false
        val existingIndex = (1..prefs.getInt("count", 0)).firstOrNull { index ->
            prefs.getString("session_${index}_id", null) == session.id
        }
        val index = existingIndex ?: (prefs.getInt("count", 0) + 1)
        saveAtIndex(index, session.id, session.date, session.steps, session.durationSeconds, session.distanceMeters, session.calories, session.activity, session.laps, session.route)
        if (existingIndex == null) prefs.edit().putInt("count", index).apply()
        return true
    }

    fun saveStepSession(steps: Int, durationSeconds: Long) = saveSession(steps, durationSeconds, 0f, 0f, "Walk", emptyList(), emptyList())

    fun sessions(): List<Session> {
        val count = prefs.getInt("count", 0)
        return (1..count).map { i ->
            val lapString = prefs.getString("session_${i}_laps", "") ?: ""
            val laps = lapString.split(';').mapNotNull { raw ->
                val p = raw.split(',')
                if (p.size != 3) null else runCatching { LapRecord(p[0].toInt(), p[1].toFloat(), p[2].toLong()) }.getOrNull()
            }
            val routeString = prefs.getString("session_${i}_route", "") ?: ""
            val route = routeString.split(';').mapNotNull { raw ->
                val p = raw.split(',')
                if (p.size != 2) null else runCatching { Location("history").apply { latitude = p[0].toDouble(); longitude = p[1].toDouble() } }.getOrNull()
            }
            val date = prefs.getLong("session_${i}_date", 0L)
            val steps = prefs.getInt("session_${i}_steps", 0)
            val duration = prefs.getLong("session_${i}_duration", 0L)
            val distance = prefs.getFloat("session_${i}_distance", 0f)
            val legacyId = "${date}_${steps}_${duration}_${distance.toBits()}"
            Session(
                date,
                steps,
                duration,
                distance,
                prefs.getFloat("session_${i}_calories", 0f),
                prefs.getString("session_${i}_activity", "Walk") ?: "Walk",
                laps,
                route,
                prefs.getString("session_${i}_id", null) ?: legacyId
            )
        }.sortedBy { it.date }
    }

    fun forRange(start: Long, end: Long): List<Session> = sessions().filter { it.date in start until end }
    fun formatDate(time: Long): String = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(time))
}

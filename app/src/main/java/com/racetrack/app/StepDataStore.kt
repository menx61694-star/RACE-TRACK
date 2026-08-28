package com.racetrack.app

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StepDataStore(context: Context) {
    private val prefs = context.getSharedPreferences("step_data", Context.MODE_PRIVATE)

    fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun todaySteps(): Int = prefs.getInt("today_steps", 0)

    fun updateFromSensor(totalSinceReboot: Float): Int {
        val today = todayKey()
        val storedDate = prefs.getString("date", null)
        val lastSensor = prefs.getFloat("last_sensor", -1f)

        if (storedDate != today) {
            prefs.edit()
                .putString("date", today)
                .putFloat("baseline", totalSinceReboot)
                .putFloat("last_sensor", totalSinceReboot)
                .putInt("today_steps", 0)
                .apply()
            return 0
        }

        var baseline = prefs.getFloat("baseline", totalSinceReboot)
        if (lastSensor >= 0f && totalSinceReboot < lastSensor) {
            // Device reboot/reset: the hardware counter starts over.
            baseline = totalSinceReboot
        }

        val steps = (totalSinceReboot - baseline).coerceAtLeast(0f).toInt()
        prefs.edit()
            .putFloat("baseline", baseline)
            .putFloat("last_sensor", totalSinceReboot)
            .putInt("today_steps", steps)
            .apply()
        return steps
    }

    fun resetTodayBaseline(currentSensorValue: Float) {
        prefs.edit()
            .putString("date", todayKey())
            .putFloat("baseline", currentSensorValue)
            .putFloat("last_sensor", currentSensorValue)
            .putInt("today_steps", 0)
            .apply()
    }
}

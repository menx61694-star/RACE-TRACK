package com.racetrack.app

import android.content.Context

class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)

    val isComplete: Boolean get() = prefs.getBoolean("complete", false)
    val name: String get() = prefs.getString("name", "") ?: ""
    val heightCm: Float get() = prefs.getFloat("height_cm", 0f)
    val weightKg: Float get() = prefs.getFloat("weight_kg", 0f)
    val age: Int get() = prefs.getInt("age", 0)
    val dailyGoal: Int get() = prefs.getInt("daily_goal", 10000)

    fun save(name: String, heightCm: Float, weightKg: Float, age: Int, dailyGoal: Int) {
        prefs.edit()
            .putString("name", name.trim())
            .putFloat("height_cm", heightCm)
            .putFloat("weight_kg", weightKg)
            .putInt("age", age)
            .putInt("daily_goal", dailyGoal.coerceIn(1000, 100000))
            .putBoolean("complete", true)
            .apply()
    }

    fun bmi(): Float? = if (heightCm > 0f && weightKg > 0f) {
        weightKg / ((heightCm / 100f) * (heightCm / 100f))
    } else null
}

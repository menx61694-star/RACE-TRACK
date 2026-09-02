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
    val updatedAt: Long get() = prefs.getLong("updated_at", 0L)

    fun save(name: String, heightCm: Float, weightKg: Float, age: Int, dailyGoal: Int) {
        require(name.isNotBlank()) { "Name cannot be blank" }
        require(heightCm in 100f..250f) { "Height must be between 100 and 250 cm" }
        require(weightKg in 25f..300f) { "Weight must be between 25 and 300 kg" }
        require(age in 5..120) { "Age must be between 5 and 120" }
        require(dailyGoal in 1000..100000) { "Daily goal must be between 1,000 and 100,000" }

        prefs.edit()
            .putString("name", name.trim())
            .putFloat("height_cm", heightCm)
            .putFloat("weight_kg", weightKg)
            .putInt("age", age)
            .putInt("daily_goal", dailyGoal)
            .putLong("updated_at", System.currentTimeMillis())
            .putBoolean("complete", true)
            .apply()
    }

    fun bmi(): Float? = if (heightCm > 0f && weightKg > 0f) {
        weightKg / ((heightCm / 100f) * (heightCm / 100f))
    } else null
}

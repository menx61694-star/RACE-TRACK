package com.racetrack.app

import android.content.Context

/** Small local profile store used until account/cloud sync is introduced. */
class ProfileDataStore(context: Context) {
    private val prefs = context.getSharedPreferences("race_track_profile", Context.MODE_PRIVATE)

    var name: String
        get() = prefs.getString("name", "") ?: ""
        set(value) = prefs.edit().putString("name", value.trim()).apply()

    var heightCm: Float
        get() = prefs.getFloat("height_cm", 0f)
        set(value) = prefs.edit().putFloat("height_cm", value).apply()

    var weightKg: Float
        get() = prefs.getFloat("weight_kg", 0f)
        set(value) = prefs.edit().putFloat("weight_kg", value).apply()

    var age: Int
        get() = prefs.getInt("age", 0)
        set(value) = prefs.edit().putInt("age", value).apply()

    var dailyGoal: Int
        get() = prefs.getInt("daily_goal", 10000)
        set(value) = prefs.edit().putInt("daily_goal", value).apply()

    var onboardingComplete: Boolean
        get() = prefs.getBoolean("onboarding_complete", false)
        set(value) = prefs.edit().putBoolean("onboarding_complete", value).apply()

    fun bmi(): Float? {
        if (heightCm <= 0f || weightKg <= 0f) return null
        val meters = heightCm / 100f
        return weightKg / (meters * meters)
    }
}

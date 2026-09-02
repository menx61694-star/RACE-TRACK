package com.racetrack.app

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/**
 * Cloud-sync boundary for Phase 2.
 *
 * Local SharedPreferences remain the source of truth while offline. This class
 * only writes after Firebase authentication has produced a current user.
 */
class FirestoreSyncManager {
    private val db = FirebaseFirestore.getInstance()

    private fun userId(): String? = FirebaseAuth.getInstance().currentUser?.uid

    fun syncProfile(profile: ProfileStore, onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        val uid = userId()
        if (uid == null) {
            onComplete(false, "Sign in to sync your profile")
            return
        }

        val data = hashMapOf<String, Any>(
            "name" to profile.name,
            "heightCm" to profile.heightCm,
            "weightKg" to profile.weightKg,
            "age" to profile.age,
            "dailyGoal" to profile.dailyGoal,
            "updatedAt" to profile.updatedAt
        )

        db.collection("users").document(uid)
            .set(data, SetOptions.merge())
            .addOnSuccessListener { onComplete(true, "Profile synced") }
            .addOnFailureListener { e -> onComplete(false, e.localizedMessage ?: "Profile sync failed") }
    }

    fun syncWorkouts(workoutStore: WorkoutStore, onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        val uid = userId()
        if (uid == null) {
            onComplete(false, "Sign in to sync workouts")
            return
        }

        val workouts = workoutStore.sessions()
        if (workouts.isEmpty()) {
            onComplete(true, "No workouts to sync")
            return
        }

        val batch = db.batch()
        val collection = db.collection("users").document(uid).collection("workouts")

        workouts.forEach { session ->
            val workoutId = listOf(
                session.date,
                session.steps,
                session.durationSeconds,
                session.distanceMeters.toBits()
            ).joinToString("_")

            val data = hashMapOf<String, Any>(
                "date" to session.date,
                "steps" to session.steps,
                "durationSeconds" to session.durationSeconds,
                "distanceMeters" to session.distanceMeters,
                "calories" to session.calories,
                "activity" to session.activity,
                "laps" to session.laps.map { lap ->
                    mapOf(
                        "number" to lap.number,
                        "distanceMeters" to lap.distanceMeters,
                        "elapsedSeconds" to lap.elapsedSeconds
                    )
                }
            )
            batch.set(collection.document(workoutId), data, SetOptions.merge())
        }

        batch.commit()
            .addOnSuccessListener { onComplete(true, "${workouts.size} workout(s) synced") }
            .addOnFailureListener { e -> onComplete(false, e.localizedMessage ?: "Workout sync failed") }
    }

    fun syncAll(profile: ProfileStore, workoutStore: WorkoutStore, onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        syncProfile(profile) { profileOk, profileMessage ->
            if (!profileOk) {
                onComplete(false, profileMessage)
                return@syncProfile
            }
            syncWorkouts(workoutStore, onComplete)
        }
    }
}

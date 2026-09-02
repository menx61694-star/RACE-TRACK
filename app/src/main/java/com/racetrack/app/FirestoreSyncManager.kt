package com.racetrack.app

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/**
 * Offline-first cloud sync boundary.
 * Local stores remain usable without a network connection. After authentication,
 * cloud data is restored/merged first and the resulting local state is uploaded.
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

        val collection = db.collection("users").document(uid).collection("workouts")
        val chunks = workouts.chunked(450)
        commitWorkoutChunk(collection, chunks, 0, onComplete)
    }

    private fun commitWorkoutChunk(
        collection: com.google.firebase.firestore.CollectionReference,
        chunks: List<List<WorkoutStore.Session>>,
        index: Int,
        onComplete: (Boolean, String) -> Unit
    ) {
        if (index >= chunks.size) {
            onComplete(true, "${chunks.flatten().size} workout(s) synced")
            return
        }

        val batch = db.batch()
        chunks[index].forEach { session ->
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
            batch.set(collection.document(session.id), data, SetOptions.merge())
        }

        batch.commit()
            .addOnSuccessListener { commitWorkoutChunk(collection, chunks, index + 1, onComplete) }
            .addOnFailureListener { e -> onComplete(false, e.localizedMessage ?: "Workout sync failed") }
    }

    fun restoreAndSyncAll(profile: ProfileStore, workoutStore: WorkoutStore, onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        val uid = userId()
        if (uid == null) {
            onComplete(false, "Sign in to sync your data")
            return
        }

        val userRef = db.collection("users").document(uid)
        userRef.get()
            .addOnSuccessListener { snapshot ->
                val cloudUpdatedAt = snapshot.getLong("updatedAt") ?: 0L
                val localUpdatedAt = profile.updatedAt
                if (cloudUpdatedAt > localUpdatedAt && snapshot.exists()) {
                    val name = snapshot.getString("name") ?: ""
                    val height = (snapshot.getDouble("heightCm") ?: 0.0).toFloat()
                    val weight = (snapshot.getDouble("weightKg") ?: 0.0).toFloat()
                    val age = (snapshot.getLong("age") ?: 0L).toInt()
                    val goal = (snapshot.getLong("dailyGoal") ?: 10000L).toInt()
                    if (name.isNotBlank()) {
                        runCatching { profile.save(name, height, weight, age, goal) }
                    }
                }
                restoreWorkoutsAndUpload(profile, workoutStore, onComplete)
            }
            .addOnFailureListener { e ->
                onComplete(false, e.localizedMessage ?: "Could not read cloud profile")
            }
    }

    private fun restoreWorkoutsAndUpload(profile: ProfileStore, workoutStore: WorkoutStore, onComplete: (Boolean, String) -> Unit) {
        val uid = userId()
        if (uid == null) {
            onComplete(false, "Sign in to sync your data")
            return
        }

        db.collection("users").document(uid).collection("workouts").get()
            .addOnSuccessListener { snapshot ->
                snapshot.documents.forEach { document ->
                    val date = document.getLong("date") ?: return@forEach
                    val steps = (document.getLong("steps") ?: 0L).toInt()
                    val duration = document.getLong("durationSeconds") ?: 0L
                    val distance = (document.getDouble("distanceMeters") ?: 0.0).toFloat()
                    val calories = (document.getDouble("calories") ?: 0.0).toFloat()
                    val activity = document.getString("activity") ?: "Walk"
                    val laps = (document.get("laps") as? List<*>)?.mapNotNull { raw ->
                        val map = raw as? Map<*, *> ?: return@mapNotNull null
                        val number = (map["number"] as? Number)?.toInt() ?: return@mapNotNull null
                        val lapDistance = (map["distanceMeters"] as? Number)?.toFloat() ?: return@mapNotNull null
                        val elapsed = (map["elapsedSeconds"] as? Number)?.toLong() ?: return@mapNotNull null
                        LapRecord(number, lapDistance, elapsed)
                    } ?: emptyList()
                    workoutStore.upsertSession(
                        WorkoutStore.Session(
                            date = date,
                            steps = steps,
                            durationSeconds = duration,
                            distanceMeters = distance,
                            calories = calories,
                            activity = activity,
                            laps = laps,
                            route = emptyList(),
                            id = document.id
                        )
                    )
                }
                syncAll(profile, workoutStore, onComplete)
            }
            .addOnFailureListener { e -> onComplete(false, e.localizedMessage ?: "Could not restore workouts") }
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

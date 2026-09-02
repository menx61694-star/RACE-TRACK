package com.racetrack.app

import android.location.Location

/** In-memory handoff used by the experimental route-replay prototype. */
object RouteReplaySession {
    var route: List<Location> = emptyList()
        private set
    var distanceMeters: Float = 0f
        private set
    var durationSeconds: Long = 0L
        private set
    var activity: String = "Walk"
        private set

    fun update(route: List<Location>, distanceMeters: Float, durationSeconds: Long) {
        this.route = route.map { Location(it) }
        this.distanceMeters = distanceMeters
        this.durationSeconds = durationSeconds
    }

    fun setActivity(activity: String) {
        this.activity = activity
    }

    fun clear() {
        route = emptyList()
        distanceMeters = 0f
        durationSeconds = 0L
        activity = "Walk"
    }
}

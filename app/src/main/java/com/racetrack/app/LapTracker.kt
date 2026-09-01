package com.racetrack.app

data class LapRecord(
    val number: Int,
    val distanceMeters: Float,
    val elapsedSeconds: Long
)

class LapTracker(private val lapDistanceMeters: Float) {
    private var completedDistance = 0f
    private var nextLapAt = lapDistanceMeters
    private var lapStartTime = 0L
    private var lapNumber = 1

    val records = mutableListOf<LapRecord>()

    fun reset() {
        completedDistance = 0f
        nextLapAt = lapDistanceMeters
        lapStartTime = 0L
        lapNumber = 1
        records.clear()
    }

    fun update(totalDistanceMeters: Float, elapsedSeconds: Long): List<LapRecord> {
        if (lapStartTime == 0L) lapStartTime = elapsedSeconds
        val newRecords = mutableListOf<LapRecord>()
        while (totalDistanceMeters >= nextLapAt) {
            val lapTime = (elapsedSeconds - lapStartTime).coerceAtLeast(0L)
            val record = LapRecord(lapNumber, lapDistanceMeters, lapTime)
            records += record
            newRecords += record
            lapNumber++
            nextLapAt += lapDistanceMeters
            lapStartTime = elapsedSeconds
        }
        completedDistance = totalDistanceMeters
        return newRecords
    }

    fun currentLapDistance(totalDistanceMeters: Float): Float = (totalDistanceMeters - (nextLapAt - lapDistanceMeters)).coerceAtLeast(0f)
    fun selectedDistanceMeters(): Float = lapDistanceMeters
}

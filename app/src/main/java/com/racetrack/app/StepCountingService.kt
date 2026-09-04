package com.racetrack.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.util.concurrent.CopyOnWriteArrayList

class StepCountingService : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private lateinit var store: StepDataStore
    private var stepSensor: Sensor? = null
    private var locationTracker: LocationTracker? = null
    private var workoutActive = false
    private var workoutPaused = false
    private var workoutElapsedSeconds = 0L
    private var lastElapsedRealtime = 0L

    override fun onCreate() {
        super.onCreate()
        store = StepDataStore(this)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        createNotificationChannel()
        startAsForeground()

        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_WORKOUT -> startWorkoutTracking()
            ACTION_PAUSE_WORKOUT -> pauseWorkoutTracking()
            ACTION_RESUME_WORKOUT -> resumeWorkoutTracking()
            ACTION_STOP_WORKOUT -> stopWorkoutTracking()
        }
        return START_STICKY
    }

    private fun startWorkoutTracking() {
        if (workoutActive) return
        workoutActive = true
        workoutPaused = false
        workoutElapsedSeconds = 0L
        lastElapsedRealtime = android.os.SystemClock.elapsedRealtime()
        workoutSnapshot = LocationTracker.Snapshot()
        workoutStatus = "Starting GPS…"

        val tracker = LocationTracker(applicationContext)
        locationTracker = tracker
        tracker.start(
            onUpdate = { snapshot ->
                workoutSnapshot = snapshot
                workoutStatus = if (snapshot.accuracyMeters > 0f) {
                    "GPS ± ${snapshot.accuracyMeters.toInt()} m"
                } else "Waiting for GPS"
                publishWorkoutState()
            },
            onStatus = { status ->
                workoutStatus = status
                publishWorkoutState()
            }
        )
        updateNotification(store.todaySteps(), "Running • GPS active")
    }

    private fun pauseWorkoutTracking() {
        if (!workoutActive || workoutPaused) return
        updateWorkoutElapsed()
        workoutPaused = true
        locationTracker?.pause()
        updateNotification(store.todaySteps(), "Workout paused")
        publishWorkoutState()
    }

    private fun resumeWorkoutTracking() {
        if (!workoutActive || !workoutPaused) return
        workoutPaused = false
        lastElapsedRealtime = android.os.SystemClock.elapsedRealtime()
        locationTracker?.resume()
        updateNotification(store.todaySteps(), "Running • GPS active")
        publishWorkoutState()
    }

    private fun stopWorkoutTracking() {
        if (!workoutActive) return
        updateWorkoutElapsed()
        locationTracker?.addElapsedSeconds(workoutElapsedSeconds)
        locationTracker?.stop()
        locationTracker = null
        workoutActive = false
        workoutPaused = false
        updateNotification(store.todaySteps(), "Today's steps: ${store.todaySteps()}")
        publishWorkoutState()
    }

    private fun updateWorkoutElapsed() {
        if (!workoutActive || workoutPaused) return
        val now = android.os.SystemClock.elapsedRealtime()
        val delta = ((now - lastElapsedRealtime) / 1000L).coerceAtLeast(0L)
        if (delta > 0L) {
            workoutElapsedSeconds += delta
            locationTracker?.addElapsedSeconds(workoutElapsedSeconds)
        }
        lastElapsedRealtime = now
    }

    private fun publishWorkoutState() {
        if (workoutActive) updateWorkoutElapsed()
        val base = workoutSnapshot
        val elapsed = workoutElapsedSeconds
        val published = if (base.distanceMeters >= 0f) {
            base.copy(
                averageSpeedMps = if (elapsed > 0) base.distanceMeters / elapsed else 0f
            )
        } else base
        workoutSnapshot = published
        listeners.forEach { it(published, workoutStatus, workoutActive, workoutPaused, elapsed) }
        if (workoutActive) RouteReplaySession.update(published.route, published.distanceMeters, elapsed)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return
        val total = event.values.firstOrNull() ?: return
        val steps = store.updateFromSensor(total)
        updateNotification(steps, if (workoutActive) "Running • GPS active" else "Today's steps: $steps")
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        locationTracker?.stop()
        locationTracker = null
        sensorManager.unregisterListener(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground() {
        val notification = buildNotification(0, "Today's steps: 0")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            startForeground(NOTIFICATION_ID, notification, types)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(steps: Int, text: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(steps, text))
    }

    private fun buildNotification(steps: Int, text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle("Race Track")
            .setContentText(text)
            .setSubText("Steps: $steps")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Race Track tracking", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Keeps step and active workout tracking running."
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_START_WORKOUT = "com.racetrack.app.action.START_WORKOUT"
        const val ACTION_PAUSE_WORKOUT = "com.racetrack.app.action.PAUSE_WORKOUT"
        const val ACTION_RESUME_WORKOUT = "com.racetrack.app.action.RESUME_WORKOUT"
        const val ACTION_STOP_WORKOUT = "com.racetrack.app.action.STOP_WORKOUT"
        private const val CHANNEL_ID = "step_tracking"
        private const val NOTIFICATION_ID = 1001

        @Volatile var workoutSnapshot: LocationTracker.Snapshot = LocationTracker.Snapshot()
        @Volatile var workoutStatus: String = "Waiting for GPS"

        private val listeners = CopyOnWriteArrayList<(LocationTracker.Snapshot, String, Boolean, Boolean, Long) -> Unit>()

        fun addWorkoutListener(listener: (LocationTracker.Snapshot, String, Boolean, Boolean, Long) -> Unit) {
            listeners.add(listener)
            listener(workoutSnapshot, workoutStatus, false, false, workoutSnapshotDuration)
        }

        fun removeWorkoutListener(listener: (LocationTracker.Snapshot, String, Boolean, Boolean, Long) -> Unit) {
            listeners.remove(listener)
        }

        private var workoutSnapshotDuration: Long = 0L
    }
}

package com.racetrack.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** Session step tracker backed by Android's hardware step counter. */
class StepTracker(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    var steps by mutableIntStateOf(0)
        private set
    val isAvailable: Boolean = stepSensor != null
    var isRunning: Boolean = false
        private set

    private var baseline: Float? = null
    private var pausedSensorValue: Float? = null

    fun start() {
        if (stepSensor == null) return
        baseline = null
        pausedSensorValue = null
        steps = 0
        isRunning = true
        sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun pause() {
        if (!isRunning) return
        isRunning = false
        sensorManager.unregisterListener(this)
    }

    fun resume() {
        if (stepSensor == null || isRunning) return
        isRunning = true
        pausedSensorValue?.let { baseline = it }
        sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() {
        isRunning = false
        sensorManager.unregisterListener(this)
        baseline = null
        pausedSensorValue = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!isRunning || event.sensor.type != Sensor.TYPE_STEP_COUNTER) return
        val total = event.values.firstOrNull() ?: return
        if (baseline == null) baseline = total
        steps = (total - (baseline ?: total)).coerceAtLeast(0f).toInt()
        pausedSensorValue = total
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    fun release() {
        sensorManager.unregisterListener(this)
    }
}

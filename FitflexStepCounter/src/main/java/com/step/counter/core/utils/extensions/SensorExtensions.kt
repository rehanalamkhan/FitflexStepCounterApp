package com.step.counter.core.utils.extensions

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager

/** True when [Sensor.TYPE_STEP_COUNTER] is available on this device. */
fun Context.hasStepCounterSensor(): Boolean {
    val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
    return sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
}

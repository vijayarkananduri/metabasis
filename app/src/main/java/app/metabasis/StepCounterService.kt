package app.metabasis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import kotlin.math.abs
import kotlin.math.sqrt

class StepCounterService : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private val timer = Handler(Looper.getMainLooper())
    private var gravity = 9.81f
    private var lastPeakAt = 0L
    private var aboveThreshold = false
    private var lastNotifiedSteps = -25
    private var lastNotifiedAt = 0L

    private val evolutionCheck = object : Runnable {
        override fun run() {
            StepLedger.checkEvolution(this@StepCounterService)
            timer.postDelayed(this, 60_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, notification("Counting steps on this phone"))
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        val counter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        val detector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val registered = when {
            counter != null && sensorManager.registerListener(this, counter, SensorManager.SENSOR_DELAY_NORMAL) -> {
                StepLedger.setSensorMode(this, "Hardware step counter · best available")
                true
            }
            detector != null && sensorManager.registerListener(this, detector, SensorManager.SENSOR_DELAY_NORMAL) -> {
                StepLedger.setSensorMode(this, "Hardware step detector · live counting")
                true
            }
            accelerometer != null && sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME) -> {
                StepLedger.setSensorMode(this, "Accelerometer fallback · approximate")
                true
            }
            else -> {
                StepLedger.setSensorMode(this, "No compatible motion sensor found")
                false
            }
        }
        if (!registered) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        timer.postDelayed(evolutionCheck, 60_000L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> StepLedger.recordHardwareTotal(this, event.values[0].toLong())
            Sensor.TYPE_STEP_DETECTOR -> StepLedger.recordDetectedStep(this, event.values[0].toInt().coerceAtLeast(1))
            Sensor.TYPE_ACCELEROMETER -> detectAccelerometerStep(event)
        }
        val state = StepLedger.load(this)
        val now = System.currentTimeMillis()
        if (abs(state.todaySteps - lastNotifiedSteps) >= 25 || now - lastNotifiedAt >= 60_000L) {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification("${state.todaySteps} steps today · ${state.sensorMode}"))
            lastNotifiedSteps = state.todaySteps
            lastNotifiedAt = now
        }
    }

    private fun detectAccelerometerStep(event: SensorEvent) {
        val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
        val magnitude = sqrt(x*x + y*y + z*z)
        gravity = 0.88f*gravity + 0.12f*magnitude
        val linear = abs(magnitude - gravity)
        val now = event.timestamp / 1_000_000L
        if (linear > 1.35f && !aboveThreshold && now - lastPeakAt >= 300L) {
            aboveThreshold = true
            lastPeakAt = now
            StepLedger.recordDetectedStep(this)
        } else if (linear < 0.45f) aboveThreshold = false
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDestroy() {
        timer.removeCallbacks(evolutionCheck)
        if (::sensorManager.isInitialized) sensorManager.unregisterListener(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Step counting", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Ongoing local step counting status"
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun notification(text: String): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_compass)
        .setContentTitle("Metabasis is counting")
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .build()

    companion object {
        const val CHANNEL_ID = "metabasis_steps"
        const val NOTIFICATION_ID = 6183
    }
}

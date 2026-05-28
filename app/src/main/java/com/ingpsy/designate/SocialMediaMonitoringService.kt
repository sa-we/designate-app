package com.ingpsy.designate

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.ingpsy.designate.Config.NOTIFICATION_DELAY_COUNTER
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds


class SocialMediaMonitoringService : Service() {

    companion object {
        @Volatile
        var isServiceRunning = false
        var isOverlayShowing = false

        private val SOCIAL_MEDIA_PACKAGES = mapOf(
            "Facebook" to "com.facebook.katana",
            "Instagram" to "com.instagram.android",
            "X" to "com.twitter.android",
            "YouTube" to "com.google.android.youtube"
        )
    }

    private val prefs by lazy {
        getSharedPreferences("service_status", MODE_PRIVATE) // SharedPreferences for running state -> restart after system reboot
    }

    private var appLogger: AppLogger? = null
    private lateinit var serviceScope: CoroutineScope
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val monitoringFlags = SOCIAL_MEDIA_PACKAGES.keys.associateWith { AtomicBoolean(false) }.toMutableMap()

    private var notificationCounter = NOTIFICATION_DELAY_COUNTER
    private var lastNotificationText: String? = null


    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val userId = loadPrefString(this, "user_id")
        appLogger = AppLogger(this, userId)
        appLogger?.logAppEvent(this, "SocialMediaMonitoringService", "", "", "created")
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ServiceAction.START.name -> startService()
            ServiceAction.STOP.name -> stopService()
        }
        // save start state
        prefs.edit { putBoolean("service_active", true) }
        Log.d("SocialMediaMonitoringService", "service_active = true")
        return START_STICKY
    }

    private fun startService() {
        if (!isServiceRunning) {
            isServiceRunning = true
            appLogger?.logAppEvent(this, "SocialMediaMonitoringService", "", "", "started")
            serviceScope.launch {
                monitorSocialMediaUsage()
            }
        }
    }

    private suspend fun monitorSocialMediaUsage() {
        while (isServiceRunning) {
            coroutineContext.ensureActive()

            try {
                val remaining = getRemainingTime(this)
                if (remaining.minutes > 0) {

                    notificationCounter  ++
                    if (notificationCounter >= NOTIFICATION_DELAY_COUNTER) {
                        notificationCounter = 0

                        var timeString = when {
                            remaining.days > 0 -> getString(R.string.txt_study_runtime_days, remaining.days)//"${remaining.days} Tage"
                            remaining.hours > 0 -> getString(R.string.txt_study_runtime_hours, remaining.hours)
                            remaining.minutes > 1 -> getString(R.string.txt_study_runtime_minutes, remaining.minutes)
                            else -> getString(R.string.txt_study_runtime_one_minute, remaining.minutes)
                        }

                        timeString = "$timeString ${getString(R.string.txt_please_send_data_daily)}"
                        Log.d("RemainingTime", timeString)

                        setNotification(timeString)
                    }
                    checkSocialMediaUsage()
                } else {
                    setNotification(getString(R.string.txt_study_is_over))
                    break
                }
                delay(Config.MONITORING_DELAY_SEC.seconds)
            } catch (e: Exception) {
                Log.e("SocialMediaService", "Monitoring error", e)
            }
        }
    }


    private fun checkSocialMediaUsage() {
        val usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val beginTime = endTime - 5000 // last 5 seconds

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, beginTime, endTime
        )

        if (stats.isNullOrEmpty()) return

        stats.maxByOrNull { it.lastTimeUsed }?.packageName

        SOCIAL_MEDIA_PACKAGES.forEach { (appName, packageName) ->
            val isRunning = stats.any {
                it.packageName == packageName && it.lastTimeUsed > beginTime
            }

            val flag = monitoringFlags[appName]!!

            when {
                isRunning && !flag.get() -> {
                    // native app opened
                    flag.set(true)
                    appLogger?.logAppEvent(this, "NATIVE", appName, "", "foreground")
                    handler.post { showSocialMediaDialog(appName) }
                }
                !isRunning && flag.get() -> {
                    // native app closed
                    flag.set(false)
                    appLogger?.logAppEvent(this, "NATIVE", appName, "", "background")
                }
            }
        }
    }

    @SuppressLint("InflateParams")
    private fun showSocialMediaDialog(socialMediaChannel: String) {

        if (isOverlayShowing) {
            Log.d("SocialMediaMonitoringService", "Overlay already exists, skip it.")
            return
        }

        try {
            isOverlayShowing = true
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
            overlayView = inflater.inflate(R.layout.overlay_layout, null)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 100
                y = 100
            }

            windowManager.addView(overlayView, params)

            overlayView?.findViewById<Button>(R.id.closeButton)?.setOnClickListener {
                removeOverlay()
            }

            overlayView?.findViewById<Button>(R.id.okButton)?.setOnClickListener {
                val intent = Intent(this, FullscreenWebViewActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("selected_social_media", socialMediaChannel)
                    putExtra("recording_option", true)
                }
                startActivity(intent)
                removeOverlay()
            }

            checkDataAmount()
        } catch (e: Exception) {
            Log.e("SocialMediaService", "Dialog error", e)
            isOverlayShowing = false
        }
    }

    private fun removeOverlay() {
        try {
            overlayView?.let {
                if (it.windowToken != null && it.parent != null) {
                    windowManager.removeView(it)
                }
            }
            overlayView = null
        } catch (e: Exception) {
            Log.e("SocialMediaService", "Remove overlay error", e)
        } finally {
            isOverlayShowing = false
        }
    }

    private fun setNotification(state: String) {
        if (lastNotificationText != state) {
            lastNotificationText = state

            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(this, "social_media_monitoring_channel")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Designate")
                .setContentText(state)
                .setStyle(NotificationCompat.BigTextStyle())
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW) // Batterie-saving
                .build()

            startForeground(1, notification)
        }
    }

    private fun stopService() {
        isServiceRunning = false
        serviceScope.cancel()
        appLogger?.logAppEvent(this, "SocialMediaMonitoringService", "", "", "stopped")
        stopSelf()
    }

    override fun onDestroy() {

        prefs.edit { putBoolean("service_active", false) } // save stop state
        Log.d("SocialMediaMonitoringService", "service_active = true")

        isServiceRunning = false
        isOverlayShowing = false
        serviceScope.cancel()
        removeOverlay()

        appLogger?.logAppEvent(this, "SocialMediaMonitoringService", "", "", "destroyed")
        super.onDestroy()
    }

    fun getSentDataSize(): Long {
        return try {
            File(filesDir, "sentKB.txt").takeIf { it.exists() }?.readText()?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            Log.d("SocialMediaMonitoringService", "getSentDataSize error = ", e)
            0L }
    }

    fun getCollectedCsvSizeInKB(): Long {
        return try {
            filesDir.listFiles { _, name -> name.endsWith(".csv", ignoreCase = true) }
                ?.sumOf { it.length() }?.div(1024) ?: 0L
        } catch (e: Exception) {
            Log.d("SocialMediaMonitoringService", "getCollectedCsvSize error = ", e)
            0L }
    }

    fun checkDataAmount() {
        val sentDataSize = getSentDataSize()
        val collectedDataSize = getCollectedCsvSizeInKB()
        val daysSinceInstallation = maxOf(1, getDaysSinceInstallation(this).toInt())

        if ((sentDataSize + collectedDataSize / 10) / daysSinceInstallation < 10) {
            overlayView?.findViewById<TextView>(R.id.txt_overlayText)?.text =
                getString(R.string.txt_need_more_data)
        }
    }

    enum class ServiceAction { START, STOP }
}
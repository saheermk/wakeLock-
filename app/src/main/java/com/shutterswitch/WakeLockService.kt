package com.shutterswitch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import android.content.ComponentName
import android.service.quicksettings.TileService
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager

class WakeLockService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    companion object {
        const val CHANNEL_ID = "shutter_switch_channel"
        const val NOTIFICATION_ID = 1001
        
        val isServiceRunning = MutableStateFlow(false)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isServiceRunning.value = true
        TileService.requestListeningState(this, ComponentName(this, ShutterTileService::class.java))

        // Build and display the persistent foreground notification
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Show invisible overlay with FLAG_KEEP_SCREEN_ON
        addInvisibleOverlay()

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning.value = false
        TileService.requestListeningState(this, ComponentName(this, ShutterTileService::class.java))

        // Remove the invisible overlay
        removeInvisibleOverlay()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun addInvisibleOverlay() {
        if (overlayView != null) return
        
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(
            1, 1,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSPARENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        overlayView = View(this).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        
        try {
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeInvisibleOverlay() {
        if (overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayView = null
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "No Sleep",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps screen on via overlay"
            setShowBadge(false)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("No Sleep — Active")
            .setContentText("Screen will remain on. Device will not sleep.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}

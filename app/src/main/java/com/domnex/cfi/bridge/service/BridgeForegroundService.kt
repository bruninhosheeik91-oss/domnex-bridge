package com.domnex.cfi.bridge.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.domnex.cfi.bridge.R

class BridgeForegroundService : Service() {

    companion object {
        private const val TAG = "CFIBridge"
        private const val CHANNEL_ID = "cfi_bridge_monitor"
        private const val NOTIFICATION_ID = 1
        private const val POLL_INTERVAL_MS = 2000L

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, BridgeForegroundService::class.java)
            )
        }

        /** Parada controlada: remove ticker/notificação sem tocar em configurações. */
        fun stop(context: Context) {
            context.stopService(Intent(context, BridgeForegroundService::class.java))
        }
    }

    private val tickerHandler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            // ── DIAG TEMP ──
            Log.d(TAG, "[FGS] tick instance=${TonAccessibilityService.instance != null}")
            // ── FIM DIAG ──
            val accessibilityService = TonAccessibilityService.instance
            if (accessibilityService != null) {
                accessibilityService.runPollCycle()
                // ── DIAG TEMP ──
                Log.d(TAG, "[FGS] tick runPollCycle chamado")
                // ── FIM DIAG ──
            } else {
                // ── DIAG TEMP ──
                Log.d(TAG, "[FGS] tick instance=null ciclo nao executado")
                // ── FIM DIAG ──
            }
            tickerHandler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        tickerHandler.postDelayed(tickRunnable, POLL_INTERVAL_MS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        tickerHandler.removeCallbacks(tickRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "CFI Bridge",
                NotificationManager.IMPORTANCE_LOW
            )
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("CFI Bridge")
            .setContentText("Monitoramento TON ativo")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }
}

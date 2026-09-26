package com.example.translator.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.translator.MainActivity
import com.example.translator.R
import com.example.translator.viewmodel.TranslatorSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Foreground service that keeps speech recognition and translation alive while
 * the screen is off. Exposes Start, Pause, and Stop via a persistent notification.
 */
class TranslatorForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var session: TranslatorSession

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        session = TranslatorSession(
            context = this,
            scope = serviceScope,
            onStateChanged = { updateForegroundNotification() },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart()
            ACTION_PAUSE -> handlePause()
            ACTION_STOP -> handleStop()
            else -> handleStart()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        session.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun handleStart() {
        promoteToForeground()
        session.start()
        updateForegroundNotification()
    }

    private fun handlePause() {
        if (session.sessionState != TranslatorSessionState.RUNNING) {
            stopSelf()
            return
        }
        promoteToForeground()
        session.pause()
        updateForegroundNotification()
    }

    private fun handleStop() {
        session.stop()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun promoteToForeground() {
        val notification = buildNotification(session.sessionState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateForegroundNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(session.sessionState))
    }

    private fun buildNotification(state: TranslatorSessionState): Notification {
        val contentText = when (state) {
            TranslatorSessionState.RUNNING -> getString(R.string.notification_running)
            TranslatorSessionState.PAUSED -> getString(R.string.notification_paused)
            TranslatorSessionState.STOPPED -> getString(R.string.notification_stopped)
        }

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(contentText)
            .setContentIntent(openAppIntent)
            .setOngoing(state != TranslatorSessionState.STOPPED)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .addAction(
                R.drawable.ic_launcher_foreground,
                getString(R.string.action_start),
                actionPendingIntent(ACTION_START, 1),
            )
            .addAction(
                R.drawable.ic_launcher_foreground,
                getString(R.string.action_pause),
                actionPendingIntent(ACTION_PAUSE, 2),
            )
            .addAction(
                R.drawable.ic_launcher_foreground,
                getString(R.string.action_stop),
                actionPendingIntent(ACTION_STOP, 3),
            )
            .build()
    }

    private fun actionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, TranslatorForegroundService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "translator_foreground"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.translator.action.START"
        const val ACTION_PAUSE = "com.example.translator.action.PAUSE"
        const val ACTION_STOP = "com.example.translator.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, TranslatorForegroundService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun pause(context: Context) {
            val intent = Intent(context, TranslatorForegroundService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, TranslatorForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}

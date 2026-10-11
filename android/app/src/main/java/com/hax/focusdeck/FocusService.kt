package com.hax.focusdeck

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** User starts this service from the visible activity; no background auto-launch. */
class FocusService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var network: DeckNetwork
    override fun onCreate() {
        super.onCreate()
        val app = application as DeckApplication
        val repo = app.repository
        val sounds = app.sounds
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("focus", "Focus session", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        fun notification(text: String, state: DeckState? = null): Notification {
            val builder = NotificationCompat.Builder(this, "focus")
                .setSmallIcon(com.hax.focusdeck.R.drawable.ic_focusdeck).setContentTitle("FocusDeck")
                .setContentText(text).setContentIntent(open).setOngoing(true).setSilent(true)
                .setOnlyAlertOnce(true)
            if (state?.phase == "active") {
                val now = repo.readClock()
                val timer = TimerEngine(state)
                val overtime = timer.isOvertime(now)
                builder.setUsesChronometer(true).setChronometerCountDown(state.budgetMs != null && !overtime)
                    .setWhen(now.wall + if (state.budgetMs == null) -timer.currentActive(now) else if (overtime) -timer.overtime(now) else timer.remaining(now)!!)
            } else builder.setShowWhen(false)
            return builder.build()
        }
        startForeground(1, notification("LAN cockpit connected while you work"))
        network = DeckNetwork(repo, scope)
        network.start()
        scope.launch {
            repo.display.map { it.pairing?.viewer to it.state.revision }.distinctUntilChanged().collect {
                val s = repo.display.value.state
                val viewer = repo.isViewer
                val task = s.session?.blocks?.getOrNull(s.index)?.title ?: "Ready for a schedule"
                if (viewer) manager.notify(1, notification("Read-only tablet viewer · ${s.phase} · $task"))
                else manager.notify(1, notification("${if (s.phase == "active" && s.overtimeNotified) "overtime" else s.phase} · $task", s))
            }
        }
        scope.launch {
            repo.boundaries.collect {
                getSystemService(VibratorManager::class.java).defaultVibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
                sounds.play("completion")
            }
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        network.stop(); scope.cancel(); super.onDestroy() }
}

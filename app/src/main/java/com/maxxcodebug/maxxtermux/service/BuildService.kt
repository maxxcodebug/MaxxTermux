package com.maxxcodebug.maxxtermux.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.maxxcodebug.maxxtermux.build.BuildEvent
import com.maxxcodebug.maxxtermux.build.BuildTracker
import com.maxxcodebug.maxxtermux.model.BuildJob
import com.maxxcodebug.maxxtermux.notify.BuildNotifier

/**
 * Foreground service that shows real build progress in the notification panel.
 * BuildTracker feeds it with events parsed from the SSH output.
 */
class BuildService : Service() {

    private var running = false
    private var startedAt = 0L
    private var job = "Build"
    private var host = ""
    private var lastPercent = -1
    private var lastProgress: BuildEvent.Progress? = null

    private val nm: NotificationManager
        get() = getSystemService(NotificationManager::class.java)

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val i = intent
        if (i == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        when (i.action) {
            ACTION_START -> {
                job = i.getStringExtra(EXTRA_JOB) ?: job
                host = i.getStringExtra(EXTRA_HOST) ?: host
                val pct = i.getIntExtra(EXTRA_PERCENT, -1)
                enterForeground()
                if (pct >= 0) {
                    update(BuildEvent.Progress(pct, i.getIntExtra(EXTRA_DONE, 0), i.getIntExtra(EXTRA_TOTAL, 0)))
                }
            }
            ACTION_STOP -> {
                BuildTracker.interrupt()
                stopTracking()
            }
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    /** Called on the main thread while the service is already running. */
    fun handle(event: BuildEvent, jobName: String, hostName: String) {
        job = jobName
        host = hostName
        when (event) {
            is BuildEvent.Progress -> {
                if (!running) enterForeground()
                update(event)
            }
            BuildEvent.Success -> if (running) finish(true, "took ${elapsed()}")
            is BuildEvent.Failure -> if (running) finish(false, event.reason)
        }
    }

    private fun enterForeground() {
        BuildNotifier.ensureChannels(this)
        if (!running) {
            running = true
            startedAt = System.currentTimeMillis()
            lastPercent = -1
            lastProgress = null
        }
        val p = lastProgress
        val n = if (p != null) {
            BuildNotifier.running(this, job, host, p, startedAt)
        } else {
            BuildNotifier.starting(this, job, host, startedAt)
        }
        ServiceCompat.startForeground(
            this,
            BuildNotifier.NOTIFICATION_ID,
            n,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun update(p: BuildEvent.Progress) {
        if (p.percent == lastPercent) return
        lastPercent = p.percent
        lastProgress = p
        nm.notify(BuildNotifier.NOTIFICATION_ID, BuildNotifier.running(this, job, host, p, startedAt))
        BuildTracker.current = BuildJob(job, host, p.percent, p.done, p.total, startedAt)
    }

    private fun finish(ok: Boolean, detail: String) {
        running = false
        BuildTracker.current = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        nm.notify(BuildNotifier.NOTIFICATION_ID, BuildNotifier.finished(this, job, host, ok, detail))
        stopSelf()
    }

    private fun stopTracking() {
        running = false
        BuildTracker.current = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun elapsed(): String {
        val s = (System.currentTimeMillis() - startedAt) / 1000
        return if (s >= 3600) "%dh %02dm".format(s / 3600, (s % 3600) / 60) else "%dm %02ds".format(s / 60, s % 60)
    }

    companion object {
        const val ACTION_START = "com.maxxcodebug.maxxtermux.action.START"
        const val ACTION_STOP = "com.maxxcodebug.maxxtermux.action.STOP"
        const val EXTRA_JOB = "job"
        const val EXTRA_HOST = "host"
        const val EXTRA_PERCENT = "percent"
        const val EXTRA_DONE = "done"
        const val EXTRA_TOTAL = "total"

        @Volatile
        var instance: BuildService? = null

        fun report(ctx: Context, event: BuildEvent, job: String, host: String) {
            val svc = instance
            if (svc != null) {
                svc.handle(event, job, host)
                return
            }
            if (event is BuildEvent.Progress) {
                try {
                    ContextCompat.startForegroundService(
                        ctx,
                        Intent(ctx, BuildService::class.java)
                            .setAction(ACTION_START)
                            .putExtra(EXTRA_JOB, job)
                            .putExtra(EXTRA_HOST, host)
                            .putExtra(EXTRA_PERCENT, event.percent)
                            .putExtra(EXTRA_DONE, event.done)
                            .putExtra(EXTRA_TOTAL, event.total),
                    )
                } catch (e: Exception) {
                    // Android may refuse to start a foreground service from the background.
                }
            }
        }
    }
}

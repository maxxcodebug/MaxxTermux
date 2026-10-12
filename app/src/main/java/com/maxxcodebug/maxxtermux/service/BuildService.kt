package com.maxxcodebug.maxxtermux.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.maxxcodebug.maxxtermux.build.BuildEvent
import com.maxxcodebug.maxxtermux.build.BuildOutputParser
import com.maxxcodebug.maxxtermux.notify.BuildNotifier

/**
 * Foreground service that shows build progress in the notification panel.
 * The SSH layer will call [feed] with every output line later.
 * For now [startDemo] simulates a 30 second build so the notification can be tested.
 *
 * Note: "Stop" only stops tracking for now. Once SSH exists it will also send Ctrl+C.
 */
class BuildService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var startedAt = 0L
    private var job = "mka bacon"
    private var host = "ServerHive"
    private var lastPercent = -1
    private var simPercent = 0

    private val nm: NotificationManager
        get() = getSystemService(NotificationManager::class.java)

    private val tick = object : Runnable {
        override fun run() {
            simPercent += 1
            val total = 50020
            if (simPercent >= 100) {
                onLine("#### build completed successfully (00:00:30 (hh:mm:ss)) ####")
            } else {
                onLine("[%3d%% %d/%d] //frameworks:services".format(simPercent, total * simPercent / 100, total))
                handler.postDelayed(this, 300)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> begin(intent)
            ACTION_DEMO -> {
                begin(intent)
                simPercent = 0
                handler.removeCallbacks(tick)
                handler.post(tick)
            }
            ACTION_LINE -> {
                begin(intent)
                intent.getStringExtra(EXTRA_LINE)?.let(::onLine)
            }
            ACTION_STOP -> stopTracking()
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun begin(intent: Intent) {
        intent.getStringExtra(EXTRA_JOB)?.let { job = it }
        intent.getStringExtra(EXTRA_HOST)?.let { host = it }
        if (!running) {
            running = true
            startedAt = System.currentTimeMillis()
            lastPercent = -1
            BuildNotifier.ensureChannels(this)
            ServiceCompat.startForeground(
                this,
                BuildNotifier.NOTIFICATION_ID,
                BuildNotifier.starting(this, job, host, startedAt),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        }
    }

    private fun onLine(line: String) {
        if (!running) return
        when (val e = BuildOutputParser.parse(line)) {
            is BuildEvent.Progress -> if (e.percent != lastPercent) {
                lastPercent = e.percent
                nm.notify(BuildNotifier.NOTIFICATION_ID, BuildNotifier.running(this, job, host, e, startedAt))
            }
            BuildEvent.Success -> finish(true, "took ${elapsed()}")
            is BuildEvent.Failure -> finish(false, e.reason)
            null -> Unit
        }
    }

    private fun finish(ok: Boolean, detail: String) {
        running = false
        handler.removeCallbacksAndMessages(null)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        nm.notify(BuildNotifier.NOTIFICATION_ID, BuildNotifier.finished(this, job, host, ok, detail))
        stopSelf()
    }

    private fun stopTracking() {
        running = false
        handler.removeCallbacksAndMessages(null)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun elapsed(): String {
        val s = (System.currentTimeMillis() - startedAt) / 1000
        return if (s >= 3600) "%dh %02dm".format(s / 3600, (s % 3600) / 60) else "%dm %02ds".format(s / 60, s % 60)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.maxxcodebug.maxxtermux.action.START"
        const val ACTION_DEMO = "com.maxxcodebug.maxxtermux.action.DEMO"
        const val ACTION_LINE = "com.maxxcodebug.maxxtermux.action.LINE"
        const val ACTION_STOP = "com.maxxcodebug.maxxtermux.action.STOP"
        const val EXTRA_LINE = "line"
        const val EXTRA_JOB = "job"
        const val EXTRA_HOST = "host"

        fun startDemo(ctx: Context) {
            ContextCompat.startForegroundService(
                ctx,
                Intent(ctx, BuildService::class.java)
                    .setAction(ACTION_DEMO)
                    .putExtra(EXTRA_JOB, "mka bacon")
                    .putExtra(EXTRA_HOST, "ServerHive"),
            )
        }

        /** Called by the SSH layer with each line of terminal output. */
        fun feed(ctx: Context, line: String, job: String, host: String) {
            ContextCompat.startForegroundService(
                ctx,
                Intent(ctx, BuildService::class.java)
                    .setAction(ACTION_LINE)
                    .putExtra(EXTRA_LINE, line)
                    .putExtra(EXTRA_JOB, job)
                    .putExtra(EXTRA_HOST, host),
            )
        }
    }
}

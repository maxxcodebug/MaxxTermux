package com.maxxcodebug.maxxtermux.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.maxxcodebug.maxxtermux.MainActivity
import com.maxxcodebug.maxxtermux.R
import com.maxxcodebug.maxxtermux.build.BuildEvent
import com.maxxcodebug.maxxtermux.service.BuildService

object BuildNotifier {
    const val CHANNEL_PROGRESS = "build_progress"
    const val CHANNEL_RESULT = "build_result"
    const val NOTIFICATION_ID = 1001

    private const val TEAL = 0xFF2B5566.toInt()

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PROGRESS, "Build progress", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Live progress of running builds"
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RESULT, "Build results", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Shown when a build finishes or fails"
            },
        )
    }

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun stopTracking(ctx: Context): PendingIntent = PendingIntent.getService(
        ctx, 1,
        Intent(ctx, BuildService::class.java).setAction(BuildService.ACTION_STOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun base(ctx: Context, channel: String) =
        NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat_build)
            .setColor(TEAL)
            .setContentIntent(openApp(ctx))

    fun starting(ctx: Context, job: String, host: String, startedAt: Long): Notification =
        base(ctx, CHANNEL_PROGRESS)
            .setContentTitle("Starting · $job")
            .setContentText(host)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setWhen(startedAt).setShowWhen(true).setUsesChronometer(true)
            .addAction(0, "Open session", openApp(ctx))
            .addAction(0, "Stop", stopTracking(ctx))
            .build()

    fun running(ctx: Context, job: String, host: String, p: BuildEvent.Progress, startedAt: Long): Notification =
        base(ctx, CHANNEL_PROGRESS)
            .setContentTitle("${p.percent}% · $job")
            .setContentText("$host · ${p.done} / ${p.total} targets")
            .setProgress(100, p.percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setWhen(startedAt).setShowWhen(true).setUsesChronometer(true)
            .addAction(0, "Open session", openApp(ctx))
            .addAction(0, "Stop", stopTracking(ctx))
            .build()

    fun finished(ctx: Context, job: String, host: String, ok: Boolean, detail: String): Notification =
        base(ctx, CHANNEL_RESULT)
            .setContentTitle(if (ok) "Build finished · $job" else "Build failed · $job")
            .setContentText("$host · $detail")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$host · $detail"))
            .setProgress(if (ok) 100 else 0, if (ok) 100 else 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .addAction(0, "Open session", openApp(ctx))
            .build()
}

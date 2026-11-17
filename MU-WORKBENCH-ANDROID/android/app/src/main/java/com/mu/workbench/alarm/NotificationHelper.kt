package com.mu.workbench.alarm

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.mu.workbench.MainActivity
import com.mu.workbench.R
import com.mu.workbench.TodoAlarmReceiver
import com.mu.workbench.data.HolidayEntry
import com.mu.workbench.data.WorkbenchState
import com.mu.workbench.domain.TodoEngine
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.exp
import kotlin.math.sin

object NotificationHelper {

    const val CH_TODO = "mu-todo2"
    const val CH_POMO = "mu-pomo"

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val soundUri = Uri.parse("android.resource://${ctx.packageName}/${R.raw.mu_chime}")
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        if (nm.getNotificationChannel(CH_TODO) == null) {
            nm.createNotificationChannel(NotificationChannel(CH_TODO, "待办到点提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "每条待办设定的准点提醒与每日汇总"
                enableVibration(true)
                setSound(soundUri, attrs)
            })
        }
        if (nm.getNotificationChannel(CH_POMO) == null) {
            nm.createNotificationChannel(NotificationChannel(CH_POMO, "番茄钟与倒计时", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "专注结束、休息结束与倒计时提醒"
                enableVibration(true)
                setSound(soundUri, attrs)
            })
        }
    }

    fun showNotification(ctx: Context, id: Int, channel: String, title: String, body: String) {
        ensureChannels(ctx)
        val openIntent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            ctx, id, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat_mu)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(id, n)
    }

    fun canScheduleExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    fun openExactAlarmSettings(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val i = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${ctx.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(i)
            } catch (_: Throwable) {
            }
        }
    }

    fun rescheduleAll(ctx: Context, state: WorkbenchState, builtinHolidays: Map<String, HolidayEntry>) {
        ensureChannels(ctx)
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Cancel up to 80 previous slots
        for (req in 1000..1080) {
            val pi = PendingIntent.getBroadcast(
                ctx, req, Intent(ctx, TodoAlarmReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) { am.cancel(pi); pi.cancel() }
        }

        if (!state.settings.remindOn) return

        val now = LocalDateTime.now()
        var slot = 1000

        // Schedule next 7 days of todo reminders
        for (dayOffset in 0..6) {
            val d = LocalDate.now().plusDays(dayOffset.toLong())
            val dk = TodoEngine.formatDate(d)
            for (t in state.todos) {
                if (t.remindTime.isBlank()) continue
                if (!TodoEngine.hitsOnDate(t, dk, state, builtinHolidays)) continue
                if (TodoEngine.isDoneOnDate(t, dk)) continue
                val parts = t.remindTime.split(":")
                val h = parts.getOrNull(0)?.toIntOrNull() ?: continue
                val m = parts.getOrNull(1)?.toIntOrNull() ?: continue
                val fireAt = LocalDateTime.of(d, LocalTime.of(h.coerceIn(0, 23), m.coerceIn(0, 59)))
                if (fireAt.isAfter(now) && slot <= 1075) {
                    scheduleExact(ctx, am, slot++, fireAt, CH_TODO, "待办提醒 · ${t.title}", "${t.tag} · ${t.priority.label}优先级")
                }
            }
        }

        // Daily summary alarm
        if (state.settings.summaryOn && state.settings.summaryTime.isNotBlank()) {
            val parts = state.settings.summaryTime.split(":")
            val h = parts.getOrNull(0)?.toIntOrNull() ?: 8
            val m = parts.getOrNull(1)?.toIntOrNull() ?: 30
            var fireAt = LocalDateTime.of(LocalDate.now(), LocalTime.of(h.coerceIn(0, 23), m.coerceIn(0, 59)))
            if (!fireAt.isAfter(now)) fireAt = fireAt.plusDays(1)
            scheduleExact(ctx, am, 1079, fireAt, CH_TODO, "今日待办汇总", "点击查看今天待办与课程安排")
        }
    }

    fun schedulePomoOrTimer(ctx: Context, reqId: Int, triggerAtMs: Long, title: String, body: String) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(ctx, TodoAlarmReceiver::class.java).apply {
            putExtra("id", reqId)
            putExtra("ch", CH_POMO)
            putExtra("title", title)
            putExtra("body", body)
        }
        val pi = PendingIntent.getBroadcast(
            ctx, reqId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (canScheduleExact(ctx)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
        }
    }

    fun cancelAlarm(ctx: Context, reqId: Int) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            ctx, reqId, Intent(ctx, TodoAlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pi != null) { am.cancel(pi); pi.cancel() }
    }

    private fun scheduleExact(
        ctx: Context,
        am: AlarmManager,
        reqId: Int,
        fireAt: LocalDateTime,
        ch: String,
        title: String,
        body: String
    ) {
        val ms = fireAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = Intent(ctx, TodoAlarmReceiver::class.java).apply {
            putExtra("id", reqId)
            putExtra("ch", ch)
            putExtra("title", title)
            putExtra("body", body)
        }
        val pi = PendingIntent.getBroadcast(
            ctx, reqId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (canScheduleExact(ctx)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        }
    }

    fun playChime(soundLevel: Int) {
        if (soundLevel <= 0) return
        Thread {
            try {
                val sr = 22050
                val freqs = doubleArrayOf(587.33, 880.0, 1174.66) // D5 - A5 - D6 柔和提示音
                val amp = if (soundLevel == 1) 0.16 else 0.36
                val samplesPerNote = (sr * 0.18).toInt()
                val total = samplesPerNote * freqs.size
                val pcm = ShortArray(total)
                for (nIdx in freqs.indices) {
                    val f = freqs[nIdx]
                    val offset = nIdx * samplesPerNote
                    for (i in 0 until samplesPerNote) {
                        val t = i.toDouble() / sr
                        val env = exp(-t * 14.0)
                        val v = sin(2.0 * Math.PI * f * t) * env * amp
                        pcm[offset + i] = (v * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    }
                }
                val track = AudioTrack(
                    AudioManager.STREAM_NOTIFICATION,
                    sr,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    pcm.size * 2,
                    AudioTrack.MODE_STATIC
                )
                track.write(pcm, 0, pcm.size)
                track.play()
                Thread.sleep(700)
                track.release()
            } catch (_: Throwable) {
            }
        }.start()
    }
}

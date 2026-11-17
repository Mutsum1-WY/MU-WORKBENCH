package com.mu.workbench

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mu.workbench.alarm.NotificationHelper
import com.mu.workbench.data.AssetsLoader
import com.mu.workbench.data.WorkbenchRepository

class TodoAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        ) {
            try {
                val repo = WorkbenchRepository(context)
                val holidays = AssetsLoader.loadBuiltinHolidays(context)
                NotificationHelper.rescheduleAll(context, repo.load(), holidays)
            } catch (_: Throwable) {
            }
            return
        }
        val id = intent.getIntExtra("id", 2001)
        val ch = intent.getStringExtra("ch") ?: NotificationHelper.CH_TODO
        val title = intent.getStringExtra("title") ?: "MU WORKBENCH 提醒"
        val body = intent.getStringExtra("body") ?: "你有一条待办到了设定时间"
        NotificationHelper.showNotification(context, id, ch, title, body)
    }
}

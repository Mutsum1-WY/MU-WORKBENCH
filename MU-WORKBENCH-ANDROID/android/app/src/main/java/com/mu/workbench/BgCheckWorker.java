package com.mu.workbench;

import android.content.Context;

import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * WorkManager 的定期任务入口：每 15 分钟跑一次 BgCheck.run()。
 *
 * 为什么是 15 分钟：WorkManager 的 PeriodicWorkRequest 最小周期就是 15 分钟；
 * 在 Doze / 省电模式下系统会把它推迟，实际可能 15~60 分钟一次 —— 这是 Android
 * 对后台任务的硬限制，不是实现取舍。真正「秒级」只能靠服务器推送，而这个 App
 * 的设计前提是数据只在本机、不需要服务器。
 */
public class BgCheckWorker extends Worker {

    public BgCheckWorker(Context ctx, WorkerParameters params) {
        super(ctx, params);
    }

    @Override
    public Result doWork() {
        try {
            BgCheck.run(getApplicationContext(), false);
        } catch (Throwable ignored) {
            /* 失败留给下一次；连续失败会在 BgCheck.run 里累计并提示 */
        }
        /* 链式续排：这次跑完再排 15 分钟后的一次（不用周期任务，见 BgCheck.scheduleNext 注释） */
        try { BgCheck.scheduleNext(getApplicationContext(), BgCheck.intervalMinutes(getApplicationContext())); } catch (Throwable ignored) {}
        return Result.success();
    }
}

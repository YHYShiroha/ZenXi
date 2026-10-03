package com.zenxi.yogatimer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

/**
 * 前台服务：让计时在后台稳定运行，并把倒计时放在通知栏（含暂停/结束快捷按钮）。
 */
public final class TimerService extends Service {

    private static final String TAG = "ZenXi/Service";

    public static final String ACTION_START = "com.zenxi.yogatimer.action.START";
    public static final String ACTION_PAUSE = "com.zenxi.yogatimer.action.PAUSE";
    public static final String ACTION_RESUME = "com.zenxi.yogatimer.action.RESUME";
    public static final String ACTION_STOP = "com.zenxi.yogatimer.action.STOP";

    private static final String CHANNEL_ID = "zenxi_running";
    private static final int NOTIF_ID = 0x2E21;
    private static final int DONE_ID = 0x2E22;

    private TimerEngine engine;
    private NotificationManager nm;
    private TimerEngine.Listener listener;
    private long lastSecond = -1L;
    private TimerEngine.State lastState;

    public static void send(Context c, String action) {
        try {
            Intent i = new Intent(c, TimerService.class);
            i.setAction(action);
            if (ACTION_START.equals(action) && Build.VERSION.SDK_INT >= 26) {
                c.startForegroundService(i);
            } else {
                c.startService(i);
            }
        } catch (Throwable t) {
            Log.w(TAG, "send " + action, t);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        engine = TimerEngine.get();
        nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createChannel();
        listener = new TimerEngine.Listener() {
            @Override
            public void onTick(TimerEngine.Status s) {
                update(s, false);
            }

            @Override
            public void onState(TimerEngine.State s) {
                if (s == TimerEngine.State.IDLE) {
                    shutdown();
                }
            }

            @Override
            public void onFinished(long workedMs, int rounds) {
                postDone(workedMs, rounds);
            }
        };
        engine.addListener(listener);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        try {
            if (action == null) {
                // 进程被系统回收后的重启：尽量把剩下的时间跑完
                if (!restoreAfterKill()) {
                    stopSelf();
                    return START_NOT_STICKY;
                }
            } else if (ACTION_START.equals(action)) {
                startForegroundSafe();
            } else if (ACTION_PAUSE.equals(action)) {
                startForegroundSafe();
                engine.pause();
            } else if (ACTION_RESUME.equals(action)) {
                startForegroundSafe();
                engine.resume();
            } else if (ACTION_STOP.equals(action)) {
                startForegroundSafe();
                engine.stop();
                shutdown();
                return START_NOT_STICKY;
            }
            update(engine.status(), true);
        } catch (Throwable t) {
            Log.w(TAG, "onStartCommand", t);
        }
        return START_STICKY;
    }

    private boolean restoreAfterKill() {
        if (engine.isBusy()) {
            return true;
        }
        Settings st = Settings.get(this);
        long end = st.sessionEnd();
        long left = end - System.currentTimeMillis();
        if (left < 3000L) {
            st.clearSession();
            return false;
        }
        TimerConfig c = st.config();
        c.rounds = 1;
        c.restMs = 0L;
        c.prepareSec = 0;
        c.workMs = left;
        engine.start(this, c);
        return true;
    }

    @Override
    public void onDestroy() {
        if (engine != null && listener != null) {
            engine.removeListener(listener);
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ------------------------------------------------------------------ 通知

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26 || nm == null) {
            return;
        }
        try {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_LOW);
            ch.setDescription(getString(R.string.notif_channel_desc));
            ch.setShowBadge(false);
            ch.setSound(null, null);
            ch.enableVibration(false);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
        } catch (Throwable t) {
            Log.w(TAG, "channel", t);
        }
    }

    private void startForegroundSafe() {
        Notification n = build(engine.status());
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIF_ID, n);
            }
        } catch (Throwable t) {
            Log.w(TAG, "startForeground", t);
            try {
                startForeground(NOTIF_ID, n);
            } catch (Throwable ignored) {
            }
        }
    }

    private void update(TimerEngine.Status s, boolean force) {
        if (s == null || nm == null) {
            return;
        }
        if (s.state == TimerEngine.State.IDLE) {
            return;
        }
        long sec = s.sessionRemainMs / 1000L;
        if (!force && sec == lastSecond && s.state == lastState) {
            return;
        }
        lastSecond = sec;
        lastState = s.state;
        try {
            nm.notify(NOTIF_ID, build(s));
        } catch (Throwable t) {
            Log.w(TAG, "notify", t);
        }
    }

    private Notification build(TimerEngine.Status s) {
        boolean paused = s.state == TimerEngine.State.PAUSED;

        String title;
        if (paused) {
            title = "禅息 · 已暂停";
        } else if (s.phase == TimerEngine.Phase.PREPARE) {
            title = "禅息 · 准备";
        } else if (s.phase == TimerEngine.Phase.REST) {
            title = "禅息 · 休息";
        } else if (s.state == TimerEngine.State.DONE) {
            title = "禅息 · 完成";
        } else {
            title = "禅息 · 练习中";
        }

        StringBuilder text = new StringBuilder();
        if (s.rounds > 1) {
            text.append("第 ").append(s.round).append('/').append(s.rounds).append(" 组");
        } else if (s.phase == TimerEngine.Phase.PREPARE) {
            text.append("即将开始");
        } else if (paused) {
            text.append("点击继续");
        } else {
            text.append("专注当下");
        }
        if (s.tailing && !paused) {
            text.append(" · 末尾铃声已就绪");
        }

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_stat_bell)
                .setContentTitle(title)
                .setContentText(text.toString())
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setWhen(System.currentTimeMillis() + Math.max(0L, s.sessionRemainMs))
                .setContentIntent(contentIntent())
                .setCategory(Notification.CATEGORY_STATUS)
                .setVisibility(Notification.VISIBILITY_PUBLIC);
        if (Build.VERSION.SDK_INT >= 24) {
            b.setChronometerCountDown(true);
        }
        if (Build.VERSION.SDK_INT >= 21) {
            b.setColor(0xFF4FE3B8);
        }

        if (paused) {
            b.addAction(buildAction("继续", serviceIntent(ACTION_RESUME)));
        } else {
            b.addAction(buildAction("暂停", serviceIntent(ACTION_PAUSE)));
        }
        b.addAction(buildAction("结束", serviceIntent(ACTION_STOP)));
        return b.build();
    }

    private Notification.Action buildAction(String title, PendingIntent pi) {
        android.graphics.drawable.Icon icon =
                android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_stat_bell);
        return new Notification.Action.Builder(icon, title, pi).build();
    }

    private PendingIntent contentIntent() {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getActivity(this, 1, i, flags);
    }

    private PendingIntent serviceIntent(String action) {
        Intent i = new Intent(this, TimerService.class);
        i.setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getService(this, action.hashCode(), i, flags);
    }

    private void postDone(long workedMs, int rounds) {
        if (nm == null) {
            return;
        }
        try {
            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(this, CHANNEL_ID)
                    : new Notification.Builder(this);
            b.setSmallIcon(R.drawable.ic_stat_bell)
                    .setContentTitle("禅息 · 练习完成")
                    .setContentText(Ui.minutes(workedMs) + (rounds > 1 ? " · 共 " + rounds + " 组" : ""))
                    .setAutoCancel(true)
                    .setContentIntent(contentIntent())
                    .setShowWhen(true)
                    .setWhen(System.currentTimeMillis());
            nm.notify(DONE_ID, b.build());
        } catch (Throwable t) {
            Log.w(TAG, "done notif", t);
        }
        shutdown();
    }

    private void shutdown() {
        try {
            stopForeground(true);
        } catch (Throwable ignored) {
        }
        stopSelf();
    }
}

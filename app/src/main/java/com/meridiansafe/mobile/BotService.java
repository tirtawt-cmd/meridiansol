package com.meridiansafe.mobile;

import android.app.*;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

public class BotService extends Service {
    public static final String ACTION_START = "com.meridiansafe.mobile.START";
    public static final String ACTION_STOP = "com.meridiansafe.mobile.STOP";
    public static final String ACTION_EMERGENCY = "com.meridiansafe.mobile.EMERGENCY";
    private static final String CHANNEL_ID = "bot_channel";
    private static final int NOTIFICATION_ID = 101;
    private volatile boolean loop = false;
    private Thread worker;
    private PowerManager.WakeLock wakeLock;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) { stopBot(false); return START_NOT_STICKY; }
        if (ACTION_EMERGENCY.equals(action)) { stopBot(true); return START_NOT_STICKY; }
        startForegroundCompat();
        startBot();
        return START_STICKY;
    }

    private void startForegroundCompat() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent stopIntent = new Intent(this, BotService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Meridian Safe • PAPER")
                .setContentText("Bot simulasi aktif. Ketuk untuk membuka dashboard.")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setOngoing(true)
                .setContentIntent(pi)
                .addAction(new Notification.Action.Builder(null, "STOP", stopPi).build())
                .build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(NOTIFICATION_ID, n);
    }

    private synchronized void startBot() {
        if (loop) return;
        loop = true;
        BotState.setRunning(this, true);
        BotState.appendLog(this, "BOT START: mode PAPER lokal.");
        PowerManager pm = (PowerManager)getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MeridianSafe:BotWakeLock");
        wakeLock.acquire(30 * 60 * 1000L); // bounded; reacquired per cycle loop if needed
        worker = new Thread(() -> {
            PaperEngine engine = new PaperEngine(getApplicationContext());
            while (loop && BotState.running(this)) {
                engine.cycle();
                if (!BotState.running(this)) break;
                try { Thread.sleep(60_000L); } catch (InterruptedException ignored) { break; }
                if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire(30 * 60 * 1000L);
            }
            stopBot(false);
        }, "paper-engine");
        worker.start();
    }

    private synchronized void stopBot(boolean emergency) {
        loop = false;
        BotState.setRunning(this, false);
        if (emergency) {
            BotState.appendLog(this, "EMERGENCY STOP: engine dihentikan dan paper account di-reset.");
            BotState.reset(this);
        } else BotState.appendLog(this, "BOT STOP.");
        if (worker != null) worker.interrupt();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL_ID, "Meridian Safe Bot", NotificationManager.IMPORTANCE_LOW);
            c.setDescription("Status bot paper trading lokal");
            getSystemService(NotificationManager.class).createNotificationChannel(c);
        }
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}

package com.alya.assistant;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

public final class AlyaForegroundService extends Service {
    private static final String CHANNEL_ID = "alya_user_session";
    private static volatile boolean running;

    public static boolean isRunning() {
        return running;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Intent stopIntent = new Intent(this, AlyaForegroundService.class).setAction("alya.action.STOP_SESSION");
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Alya session is active")
                .setContentText("No background microphone recording. Tap Stop to end this session.")
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent).build())
                .build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(12, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(12, notification);
        running = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "alya.action.STOP_SESSION".equals(intent.getAction())) stopSelf();
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        running = false;
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Alya user-started session", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
}
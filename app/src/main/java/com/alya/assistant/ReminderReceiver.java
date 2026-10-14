package com.alya.assistant;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class ReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "alya_reminders";

    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID, "Alya reminders", NotificationManager.IMPORTANCE_DEFAULT));
        }
        android.app.Notification.Builder notification = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new android.app.Notification.Builder(context, CHANNEL_ID)
                : new android.app.Notification.Builder(context);
        notification.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Alya reminder")
                .setContentText(intent.getStringExtra("message"))
                .setAutoCancel(true);
        manager.notify(intent.getIntExtra("notification_id", 1), notification.build());
    }
}
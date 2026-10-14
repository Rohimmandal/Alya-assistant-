package com.alya.assistant;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.app.Notification;
import android.telecom.TelecomManager;
import android.os.Bundle;

public final class AlyaNotificationListenerService extends NotificationListenerService {
    private static volatile String latestAllowedNotification = "";
    private static volatile String latestNotificationKey = "";

    static String getLatestAllowedNotification() {
        return latestAllowedNotification;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification statusBarNotification) {
        String packageName = statusBarNotification.getPackageName();
        boolean whatsapp = packageName.equals("com.whatsapp") || packageName.equals("com.whatsapp.w4b");
        TelecomManager telecom = getSystemService(TelecomManager.class);
        String dialerPackage = telecom == null ? null : telecom.getDefaultDialerPackage();
        Notification notification = statusBarNotification.getNotification();
        boolean callNotice = packageName.equals(dialerPackage)
            && Notification.CATEGORY_CALL.equals(notification.category);
        if (!whatsapp && !callNotice) return;
        Bundle extras = notification.extras;
        if (extras == null) return;
        CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (text == null) text = extras.getCharSequence(Notification.EXTRA_TEXT);
        StringBuilder content = new StringBuilder(whatsapp ? "WhatsApp" : "Phone call notification");
        if (title != null) content.append("\n").append(title);
        if (text != null) content.append("\n").append(text);
        latestAllowedNotification = content.substring(0, Math.min(content.length(), 1000));
        latestNotificationKey = statusBarNotification.getKey();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification statusBarNotification) {
        if (latestNotificationKey.equals(statusBarNotification.getKey())) {
            latestNotificationKey = "";
            latestAllowedNotification = "";
        }
    }
}
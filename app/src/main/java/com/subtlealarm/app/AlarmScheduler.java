package com.subtlealarm.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class AlarmScheduler {

    private AlarmScheduler() { }

    public static void scheduleNext(Context context) {
        AlarmStore store = AlarmStore.load(context);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent fire = firePendingIntent(context);
        if (!store.enabled) {
            am.cancel(fire);
            return;
        }
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            return; // MainActivity guides the user to grant exact alarms
        }
        long triggerAt = store.nextTrigger(System.currentTimeMillis());
        PendingIntent show = PendingIntent.getActivity(context, 1,
                new Intent(context, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setAlarmClock(new AlarmManager.AlarmClockInfo(triggerAt, show), fire);
    }

    public static void cancel(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(firePendingIntent(context));
    }

    private static PendingIntent firePendingIntent(Context context) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.setAction("com.subtlealarm.app.ACTION_FIRE");
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}

package com.subtlealarm.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;

/**
 * Single-alarm settings, kept in SharedPreferences.
 * Day bits: index 0 = Sunday ... 6 = Saturday.
 */
public final class AlarmStore {

    public static final int TONE_DAWN = 0;
    public static final int TONE_AURORA = 1;
    public static final int TONE_BREEZE = 2;

    public static final int[] FADE_CHOICES_SEC = {30, 60, 120, 300};

    public int hour = 6;
    public int minute = 30;
    public int daysMask = 0;
    public boolean enabled = false;
    public boolean vibrate = true;
    public int tone = TONE_DAWN;
    public int fadeSec = 60;
    public float volume = 0.6f;

    private AlarmStore() { }

    public static AlarmStore load(Context context) {
        SharedPreferences p = prefs(context);
        AlarmStore s = new AlarmStore();
        s.hour = p.getInt("hour", 6);
        s.minute = p.getInt("minute", 30);
        s.daysMask = p.getInt("daysMask", 0);
        s.enabled = p.getBoolean("enabled", false);
        s.vibrate = p.getBoolean("vibrate", true);
        s.tone = p.getInt("tone", TONE_DAWN);
        s.fadeSec = p.getInt("fadeSec", 60);
        s.volume = p.getFloat("volume", 0.6f);
        return s;
    }

    public void save(Context context) {
        prefs(context).edit()
                .putInt("hour", hour)
                .putInt("minute", minute)
                .putInt("daysMask", daysMask)
                .putBoolean("enabled", enabled)
                .putBoolean("vibrate", vibrate)
                .putInt("tone", tone)
                .putInt("fadeSec", fadeSec)
                .putFloat("volume", volume)
                .apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("subtle_alarm", Context.MODE_PRIVATE);
    }

    /** Next trigger time in epoch millis, strictly after now. */
    public long nextTrigger(long nowMillis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(nowMillis);
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        for (int i = 0; i < 8; i++) {
            boolean inFuture = cal.getTimeInMillis() > nowMillis;
            int dayIndex = cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY; // 0..6
            boolean dayOk = daysMask == 0 || (daysMask & (1 << dayIndex)) != 0;
            if (inFuture && dayOk) {
                return cal.getTimeInMillis();
            }
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
        return cal.getTimeInMillis();
    }

    public boolean repeats() {
        return daysMask != 0;
    }

    public static String toneName(int tone) {
        if (tone == TONE_AURORA) return "Aurora";
        if (tone == TONE_BREEZE) return "Breeze";
        return "Dawn";
    }

    public static String fadeName(int fadeSec) {
        if (fadeSec < 60) return fadeSec + " seconds";
        int min = fadeSec / 60;
        return min == 1 ? "1 minute" : min + " minutes";
    }
}

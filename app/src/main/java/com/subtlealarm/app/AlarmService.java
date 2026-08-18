package com.subtlealarm.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;

/**
 * Plays the alarm: looping bell phrase with a long fade-in, an optional
 * gentle vibration, and a full-screen wake activity. Stops on the on-screen
 * dismiss button, on any volume key, or when the power button turns the
 * screen off.
 */
public class AlarmService extends Service {

    public static final String ACTION_START = "com.subtlealarm.app.ACTION_START";
    public static final String ACTION_STOP = "com.subtlealarm.app.ACTION_STOP";
    public static final String ACTION_ALARM_STOPPED = "com.subtlealarm.app.ACTION_ALARM_STOPPED";
    public static final String EXTRA_START_ELAPSED = "startElapsed";
    public static final String EXTRA_FADE_SEC = "fadeSec";

    private static final String CHANNEL_ID = "subtle_alarm";
    private static final int NOTIFICATION_ID = 1;
    private static final long AUTO_SILENCE_MS = 15 * 60 * 1000L;

    /** True while the alarm is actually sounding; AlarmActivity checks this. */
    public static volatile boolean running = false;

    private AudioTrack track;
    private Vibrator vibrator;
    private LedBreather ledBreather;
    private BroadcastReceiver screenOffReceiver;
    private PowerManager.WakeLock wakeLock;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long startElapsed;
    private int fadeSec = 60;
    private float targetVolume = 0.6f;

    private final Runnable fadeTick = new Runnable() {
        @Override
        public void run() {
            if (track == null) return;
            float t = (SystemClock.elapsedRealtime() - startElapsed) / 1000f;
            float p = Math.min(1f, t / Math.max(1f, fadeSec));
            float vol = targetVolume * p * p; // quadratic ramp: very quiet start
            track.setVolume(Math.max(0.004f, vol));
            if (p < 1f) {
                handler.postDelayed(this, 120);
            }
        }
    };

    private final Runnable autoSilence = new Runnable() {
        @Override
        public void run() {
            stopAlarm();
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            stopAlarm();
            return START_NOT_STICKY;
        }
        startAlarm();
        return START_NOT_STICKY;
    }

    private void startAlarm() {
        if (running) return;
        running = true;
        startElapsed = SystemClock.elapsedRealtime();

        AlarmStore store = AlarmStore.load(this);
        fadeSec = store.fadeSec;
        targetVolume = store.volume;

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "subtlealarm:alarm");
        wakeLock.acquire(AUTO_SILENCE_MS + 5000);

        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        // schedule the next occurrence right away (or disable a one-shot)
        if (store.repeats()) {
            AlarmScheduler.scheduleNext(this);
        } else {
            store.enabled = false;
            store.save(this);
            AlarmScheduler.cancel(this);
        }

        startSound(store);
        if (store.vibrate) startVibration();
        if (store.led) {
            ledBreather = new LedBreather(this, handler);
            ledBreather.start();
        }

        screenOffReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                stopAlarm(); // power button pressed: screen went off
            }
        };
        IntentFilter screenOffFilter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenOffReceiver, screenOffFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenOffReceiver, screenOffFilter);
        }

        handler.postDelayed(autoSilence, AUTO_SILENCE_MS);
        startActivity(alarmActivityIntent());
    }

    private void startSound(AlarmStore store) {
        short[] pcm = BellSynth.render(store.tone);
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        AudioFormat format = new AudioFormat.Builder()
                .setSampleRate(BellSynth.SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build();
        track = new AudioTrack(attrs, format, pcm.length * 2,
                AudioTrack.MODE_STATIC, android.media.AudioManager.AUDIO_SESSION_ID_GENERATE);
        track.write(pcm, 0, pcm.length);
        track.setLoopPoints(0, pcm.length, -1);
        track.setVolume(0.004f);
        track.play();
        handler.post(fadeTick);
    }

    private void startVibration() {
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) return;
        long[] timings = {1600, 280, 240, 340, 2800};
        if (vibrator.hasAmplitudeControl()) {
            int[] amplitudes = {0, 40, 0, 70, 0};
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, 0));
        } else {
            vibrator.vibrate(VibrationEffect.createWaveform(timings, 0));
        }
    }

    private void stopAlarm() {
        if (!running) return;
        running = false;
        handler.removeCallbacks(fadeTick);
        handler.removeCallbacks(autoSilence);
        if (screenOffReceiver != null) {
            unregisterReceiver(screenOffReceiver);
            screenOffReceiver = null;
        }
        if (track != null) {
            try {
                track.stop();
            } catch (IllegalStateException ignored) { }
            track.release();
            track = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }
        if (ledBreather != null) {
            ledBreather.stop();
            ledBreather = null;
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }
        Intent stopped = new Intent(ACTION_ALARM_STOPPED);
        stopped.setPackage(getPackageName());
        sendBroadcast(stopped);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private Intent alarmActivityIntent() {
        Intent intent = new Intent(this, AlarmActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(EXTRA_START_ELAPSED, startElapsed);
        intent.putExtra(EXTRA_FADE_SEC, fadeSec);
        return intent;
    }

    private Notification buildNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                getString(R.string.notif_channel_alarm), NotificationManager.IMPORTANCE_HIGH);
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.setBypassDnd(true);
        nm.createNotificationChannel(channel);

        PendingIntent fullScreen = PendingIntent.getActivity(this, 2, alarmActivityIntent(),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, AlarmService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stop = PendingIntent.getService(this, 3, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_text))
                .setCategory(Notification.CATEGORY_ALARM)
                .setOngoing(true)
                .setColor(0xFFE9C48E)
                .setFullScreenIntent(fullScreen, true)
                .addAction(new Notification.Action.Builder(
                        android.graphics.drawable.Icon.createWithResource(this,
                                R.drawable.ic_launcher_foreground),
                        getString(R.string.dismiss), stop).build());
        return builder.build();
    }

    @Override
    public void onDestroy() {
        stopAlarm();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

package com.subtlealarm.app;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The wake screen: a slowly rising glow, the time, and a single dismiss
 * button. Volume keys dismiss; the power button dismisses via the
 * service's screen-off receiver.
 */
public class AlarmActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private GlowView glow;
    private TextView clockText;
    private long startElapsed;
    private int fadeSec = 60;
    private BroadcastReceiver stoppedReceiver;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            float t = (SystemClock.elapsedRealtime() - startElapsed) / 1000f;
            glow.setSunrise(t / Math.max(1f, fadeSec));
            handler.postDelayed(this, 250);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alarm);

        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        glow = (GlowView) findViewById(R.id.glow);
        glow.setMode(GlowView.MODE_SUNRISE);
        clockText = (TextView) findViewById(R.id.clockText);

        startElapsed = getIntent().getLongExtra(AlarmService.EXTRA_START_ELAPSED,
                SystemClock.elapsedRealtime());
        fadeSec = getIntent().getIntExtra(AlarmService.EXTRA_FADE_SEC, 60);

        View.OnClickListener dismissOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        };
        findViewById(R.id.dismissButton).setOnClickListener(dismissOnClick);
        // safety net: if any device ever clips or covers the button, tapping
        // the screen itself still stops the alarm
        findViewById(R.id.alarmRoot).setOnClickListener(dismissOnClick);

        stoppedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                finish();
            }
        };
        IntentFilter filter = new IntentFilter(AlarmService.ACTION_ALARM_STOPPED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(stoppedReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stoppedReceiver, filter);
        }
    }

    private void updateClock() {
        boolean is24 = android.text.format.DateFormat.is24HourFormat(this);
        SimpleDateFormat fmt = new SimpleDateFormat(is24 ? "H:mm" : "h:mm", Locale.getDefault());
        clockText.setText(fmt.format(new Date()));
    }

    private void dismiss() {
        Intent stop = new Intent(this, AlarmService.class);
        stop.setAction(AlarmService.ACTION_STOP);
        startService(stop);
        finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE
                || keyCode == KeyEvent.KEYCODE_POWER) {
            dismiss();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        dismiss();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!AlarmService.running) {
            finish();
            return;
        }
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (stoppedReceiver != null) {
            unregisterReceiver(stoppedReceiver);
            stoppedReceiver = null;
        }
        super.onDestroy();
    }
}

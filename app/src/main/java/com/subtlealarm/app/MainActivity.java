package com.subtlealarm.app;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.util.Calendar;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String[] DAY_LETTERS = {"S", "M", "T", "W", "T", "F", "S"};

    private AlarmStore store;
    private TextView timeText;
    private TextView ampmText;
    private TextView toneValue;
    private TextView fadeValue;
    private Switch enableSwitch;
    private Switch vibrateSwitch;
    private Switch ledSwitch;
    private SeekBar volumeSeek;
    private final TextView[] dayViews = new TextView[7];
    private final Handler handler = new Handler(Looper.getMainLooper());
    private AudioTrack preview;
    private boolean suppressCallbacks = false;

    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            updateCountdown();
            handler.postDelayed(this, 30 * 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        store = AlarmStore.load(this);

        timeText = (TextView) findViewById(R.id.timeText);
        ampmText = (TextView) findViewById(R.id.ampmText);
        toneValue = (TextView) findViewById(R.id.toneValue);
        fadeValue = (TextView) findViewById(R.id.fadeValue);
        enableSwitch = (Switch) findViewById(R.id.enableSwitch);
        vibrateSwitch = (Switch) findViewById(R.id.vibrateSwitch);
        ledSwitch = (Switch) findViewById(R.id.ledSwitch);
        volumeSeek = (SeekBar) findViewById(R.id.volumeSeek);

        buildDayRow();

        findViewById(R.id.timeRow).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTimePicker();
            }
        });

        enableSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (suppressCallbacks) return;
                store.enabled = isChecked;
                store.save(MainActivity.this);
                if (isChecked) {
                    ensurePermissions();
                }
                AlarmScheduler.scheduleNext(MainActivity.this);
                updateCountdown();
            }
        });

        vibrateSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (suppressCallbacks) return;
                store.vibrate = isChecked;
                store.save(MainActivity.this);
            }
        });

        ledSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (suppressCallbacks) return;
                store.led = isChecked;
                store.save(MainActivity.this);
            }
        });

        findViewById(R.id.toneRow).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                store.tone = (store.tone + 1) % 3;
                store.save(MainActivity.this);
                toneValue.setText(AlarmStore.toneName(store.tone));
                playPreview();
            }
        });

        findViewById(R.id.fadeRow).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int idx = 0;
                for (int i = 0; i < AlarmStore.FADE_CHOICES_SEC.length; i++) {
                    if (AlarmStore.FADE_CHOICES_SEC[i] == store.fadeSec) idx = i;
                }
                store.fadeSec = AlarmStore.FADE_CHOICES_SEC[(idx + 1) % AlarmStore.FADE_CHOICES_SEC.length];
                store.save(MainActivity.this);
                fadeValue.setText(AlarmStore.fadeName(store.fadeSec));
            }
        });

        volumeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                store.volume = Math.max(0.05f, progress / 100f);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                store.save(MainActivity.this);
                playPreview();
            }
        });
    }

    private void buildDayRow() {
        LinearLayout dayRow = (LinearLayout) findViewById(R.id.dayRow);
        float density = getResources().getDisplayMetrics().density;
        int size = (int) (38 * density);
        int margin = (int) (4 * density);
        for (int i = 0; i < 7; i++) {
            final int index = i;
            TextView day = new TextView(this);
            day.setText(DAY_LETTERS[i]);
            day.setGravity(Gravity.CENTER);
            day.setTextSize(13);
            day.setBackgroundResource(R.drawable.day_toggle);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(margin, 0, margin, 0);
            day.setLayoutParams(lp);
            day.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    store.daysMask ^= (1 << index);
                    store.save(MainActivity.this);
                    refreshDayRow();
                    AlarmScheduler.scheduleNext(MainActivity.this);
                    updateCountdown();
                }
            });
            dayViews[i] = day;
            dayRow.addView(day);
        }
    }

    private void refreshDayRow() {
        for (int i = 0; i < 7; i++) {
            boolean on = (store.daysMask & (1 << i)) != 0;
            dayViews[i].setSelected(on);
            dayViews[i].setTextColor(getColor(on ? R.color.gold : R.color.text_dim));
        }
    }

    private void showTimePicker() {
        boolean is24 = android.text.format.DateFormat.is24HourFormat(this);
        TimePickerDialog dialog = new TimePickerDialog(this,
                new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                        store.hour = hourOfDay;
                        store.minute = minute;
                        store.enabled = true;
                        store.save(MainActivity.this);
                        ensurePermissions();
                        AlarmScheduler.scheduleNext(MainActivity.this);
                        refreshAll();
                    }
                }, store.hour, store.minute, is24);
        dialog.show();
    }

    private void refreshAll() {
        boolean is24 = android.text.format.DateFormat.is24HourFormat(this);
        int h = store.hour;
        String ampm = "";
        if (!is24) {
            ampm = h < 12 ? "AM" : "PM";
            h = h % 12;
            if (h == 0) h = 12;
        }
        timeText.setText(String.format(Locale.getDefault(), "%d:%02d", h, store.minute));
        ampmText.setText(ampm);
        ampmText.setVisibility(is24 ? View.GONE : View.VISIBLE);
        toneValue.setText(AlarmStore.toneName(store.tone));
        fadeValue.setText(AlarmStore.fadeName(store.fadeSec));

        suppressCallbacks = true;
        enableSwitch.setChecked(store.enabled);
        vibrateSwitch.setChecked(store.vibrate);
        ledSwitch.setChecked(store.led);
        suppressCallbacks = false;

        volumeSeek.setProgress((int) (store.volume * 100));
        refreshDayRow();
        updateCountdown();
    }

    /** The toggle's label is the app's only alarm-state readout. */
    private void updateCountdown() {
        if (!store.enabled) {
            enableSwitch.setText(R.string.alarm_off);
            return;
        }
        long now = System.currentTimeMillis();
        long next = store.nextTrigger(now);
        long mins = (next - now + 59999) / 60000;
        long days = mins / (24 * 60);
        long hours = (mins % (24 * 60)) / 60;
        long m = mins % 60;
        StringBuilder sb = new StringBuilder("wakes you in ");
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0 || days > 0) sb.append(hours).append("h ");
        sb.append(m).append("m");
        enableSwitch.setText(sb.toString());
    }

    private void ensurePermissions() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 10);
        }
        if (Build.VERSION.SDK_INT >= 31 && Build.VERSION.SDK_INT < 33) {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (!am.canScheduleExactAlarms()) {
                Toast.makeText(this, "Please allow exact alarms for Subtle Alarm",
                        Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }
    }

    private void playPreview() {
        stopPreview();
        short[] pcm = BellSynth.render(store.tone);
        int samples = Math.min(pcm.length, BellSynth.SAMPLE_RATE * 4);
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        AudioFormat format = new AudioFormat.Builder()
                .setSampleRate(BellSynth.SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build();
        preview = new AudioTrack(attrs, format, samples * 2,
                AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE);
        preview.write(pcm, 0, samples);
        preview.setVolume(store.volume);
        preview.play();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                stopPreview();
            }
        }, 4500);
    }

    private void stopPreview() {
        if (preview != null) {
            try {
                preview.stop();
            } catch (IllegalStateException ignored) { }
            preview.release();
            preview = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        store = AlarmStore.load(this);
        refreshAll();
        handler.post(minuteTick);
        GlowView glow = (GlowView) findViewById(R.id.glow);
        glow.setMode(GlowView.MODE_AMBIENT);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(minuteTick);
        stopPreview();
        super.onPause();
    }
}

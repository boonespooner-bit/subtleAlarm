package com.subtlealarm.app;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;

/**
 * Breathes the camera flash LED while the alarm plays: a slow fade in and
 * out on devices with torch strength control (Android 13+), or a soft slow
 * pulse where the torch is only on/off.
 */
public class LedBreather {

    private static final double PERIOD_SEC = 6.0;

    /** Ceiling on torch output: the breath peaks at this fraction of full
     *  strength, so the light stays a dim ember in a dark room. */
    private static final double MAX_BRIGHTNESS = 0.10;

    private final CameraManager cameraManager;
    private final Handler handler;
    private String cameraId;
    private int maxStrength = 1;
    private int peakLevel = 1;
    private boolean running = false;
    private boolean torchOn = false;
    private int lastLevel = -1;
    private long startElapsed;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            step();
            handler.postDelayed(this, maxStrength > 1 ? 120 : 250);
        }
    };

    public LedBreather(Context context, Handler handler) {
        this.handler = handler;
        this.cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        findCamera();
    }

    private void findCamera() {
        if (cameraManager == null) return;
        try {
            String[] ids = cameraManager.getCameraIdList();
            for (int i = 0; i < ids.length; i++) {
                CameraCharacteristics c = cameraManager.getCameraCharacteristics(ids[i]);
                Boolean hasFlash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                if (hasFlash != null && hasFlash) {
                    cameraId = ids[i];
                    if (Build.VERSION.SDK_INT >= 33) {
                        Integer max = c.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL);
                        if (max != null) maxStrength = max;
                    }
                    // peak at MAX_BRIGHTNESS of full strength, but never below
                    // level 1 (the dimmest the torch can go while lit)
                    peakLevel = Math.max(1, (int) Math.round(maxStrength * MAX_BRIGHTNESS));
                    if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                        break; // prefer the back flash; keep looking otherwise
                    }
                }
            }
        } catch (Exception ignored) {
            cameraId = null;
        }
    }

    public void start() {
        if (cameraId == null || running) return;
        running = true;
        startElapsed = SystemClock.elapsedRealtime();
        handler.post(tick);
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(tick);
        turnOff();
    }

    private void step() {
        double t = (SystemClock.elapsedRealtime() - startElapsed) / 1000.0;
        // 0 -> 1 -> 0 over each period, smooth at both ends
        double phase = 0.5 * (1 - Math.cos(2 * Math.PI * t / PERIOD_SEC));
        try {
            if (Build.VERSION.SDK_INT >= 33 && maxStrength > 1) {
                int level = (int) Math.round(phase * peakLevel);
                if (level == lastLevel) return;
                lastLevel = level;
                if (level <= 0) {
                    turnOff();
                } else {
                    cameraManager.turnOnTorchWithStrengthLevel(cameraId, level);
                    torchOn = true;
                }
            } else {
                // binary torch: brightness isn't adjustable, so keep the lit
                // portion of each breath brief to limit the light it throws
                boolean shouldBeOn = phase > 0.88;
                if (shouldBeOn != torchOn) {
                    cameraManager.setTorchMode(cameraId, shouldBeOn);
                    torchOn = shouldBeOn;
                }
            }
        } catch (Exception ignored) {
            // torch busy (camera in use) or unavailable: skip this step
        }
    }

    private void turnOff() {
        if (cameraId == null) return;
        try {
            if (torchOn) cameraManager.setTorchMode(cameraId, false);
        } catch (Exception ignored) { }
        torchOn = false;
        lastLevel = -1;
    }
}

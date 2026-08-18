package com.subtlealarm.app;

/**
 * Renders soft bell phrases as 16-bit mono PCM at 44.1 kHz.
 * Each tone is a short phrase meant to loop seamlessly, with long
 * silences between strikes so the sound stays unobtrusive.
 */
public final class BellSynth {

    public static final int SAMPLE_RATE = 44100;

    private BellSynth() { }

    /** Renders one loopable phrase for the given tone. */
    public static short[] render(int tone) {
        if (tone == AlarmStore.TONE_AURORA) return renderAurora();
        if (tone == AlarmStore.TONE_BREEZE) return renderBreeze();
        return renderDawn();
    }

    /** Low singing bowl. One deep, slow strike every nine seconds. */
    private static short[] renderDawn() {
        float[] buf = new float[SAMPLE_RATE * 9];
        double[] ratios = {1.0, 2.0, 2.92, 4.18};
        double[] amps = {1.0, 0.32, 0.16, 0.07};
        strike(buf, 0.0, 196.0, ratios, amps, 3.2, 0.05);
        return normalize(buf);
    }

    /** Two distant hand-bell notes, a gentle fifth apart, every ten seconds. */
    private static short[] renderAurora() {
        float[] buf = new float[SAMPLE_RATE * 10];
        double[] ratios = {1.0, 2.76, 5.40};
        double[] amps = {1.0, 0.22, 0.05};
        strike(buf, 0.0, 440.0, ratios, amps, 2.4, 0.02);
        strike(buf, 1.8, 659.25, ratios, amps, 2.2, 0.02);
        return normalize(buf);
    }

    /** A slow music-box arpeggio, rising and falling away, every twelve seconds. */
    private static short[] renderBreeze() {
        float[] buf = new float[SAMPLE_RATE * 12];
        double[] ratios = {1.0, 3.01};
        double[] amps = {1.0, 0.12};
        strike(buf, 0.0, 440.00, ratios, amps, 1.8, 0.012);
        strike(buf, 0.9, 554.37, ratios, amps, 1.8, 0.012);
        strike(buf, 1.8, 659.25, ratios, amps, 1.8, 0.012);
        scaleRegion(buf, 2.9, 880.00, ratios, amps, 1.6, 0.012, 0.6);
        return normalize(buf);
    }

    private static void scaleRegion(float[] buf, double startSec, double f0,
                                    double[] ratios, double[] amps,
                                    double tau, double attackSec, double gain) {
        double[] scaled = new double[amps.length];
        for (int i = 0; i < amps.length; i++) scaled[i] = amps[i] * gain;
        strike(buf, startSec, f0, ratios, scaled, tau, attackSec);
    }

    /**
     * Adds one bell strike into the buffer: a sum of detuned partial pairs,
     * each with a soft attack and exponential decay.
     */
    private static void strike(float[] buf, double startSec, double f0,
                               double[] ratios, double[] amps,
                               double tau, double attackSec) {
        int start = (int) (startSec * SAMPLE_RATE);
        int length = Math.min(buf.length - start, (int) (tau * 4 * SAMPLE_RATE));
        for (int k = 0; k < ratios.length; k++) {
            double f = f0 * ratios[k];
            double fDetuned = f * 1.0025;
            double partialTau = tau / Math.pow(k + 1, 0.85);
            double amp = amps[k];
            double w1 = 2 * Math.PI * f / SAMPLE_RATE;
            double w2 = 2 * Math.PI * fDetuned / SAMPLE_RATE;
            int attack = Math.max(1, (int) (attackSec * SAMPLE_RATE));
            for (int i = 0; i < length; i++) {
                double t = i / (double) SAMPLE_RATE;
                double env = Math.exp(-t / partialTau);
                if (i < attack) {
                    // raised-cosine ramp: soft mallet, no click
                    env *= 0.5 * (1 - Math.cos(Math.PI * i / (double) attack));
                }
                double s = amp * env * (Math.sin(w1 * i) + 0.55 * Math.sin(w2 * i));
                buf[start + i] += (float) s;
            }
        }
    }

    private static short[] normalize(float[] buf) {
        float peak = 1e-6f;
        for (int i = 0; i < buf.length; i++) {
            float a = Math.abs(buf[i]);
            if (a > peak) peak = a;
        }
        float scale = 0.72f * 32767f / peak;
        short[] out = new short[buf.length];
        for (int i = 0; i < buf.length; i++) {
            out[i] = (short) (buf[i] * scale);
        }
        return out;
    }
}

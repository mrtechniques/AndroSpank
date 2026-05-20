package com.spankapp.android.sensor;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * AccelerometerMonitor
 *
 * Reads the device accelerometer at GAME (fast) rate and detects impact spikes
 * using the same ring-buffer / amplitude approach as taigrr/spank's Go code.
 *
 * Algorithm:
 *   1. Maintain a rolling baseline of gravity-corrected magnitude.
 *   2. When a sample's magnitude exceeds (baseline + threshold), fire onSpank().
 *   3. Enforce a cooldown to prevent repeat triggers from the same impact.
 *
 * Compatible with Android 8+ (API 26).
 */
public class AccelerometerMonitor implements SensorEventListener {

    private static final String TAG = "AccelerometerMonitor";

    // Ring buffer length for baseline (≈ 0.5 s at 100 Hz)
    private static final int RING_SIZE = 50;

    public interface SpankListener {
        /**
         * @param amplitude  peak g-force of this hit (useful for volume scaling)
         */
        void onSpank(float amplitude);
    }

    private final SensorManager sensorManager;
    private final Sensor        accel;
    private SpankListener       listener;

    // Config (written from UI thread, read from sensor thread — volatile for visibility)
    private volatile float sensitivityThreshold = 2.5f;   // g above baseline
    private volatile long  cooldownMs           = 800L;
    private volatile boolean active             = false;

    // State (sensor thread only)
    private final Deque<Float> ring    = new ArrayDeque<>(RING_SIZE);
    private float              ringSum = 0f;
    private long               lastTriggerNs = 0L;
    // Low-pass filter state for gravity removal
    private float gx = 0f, gy = 0f, gz = 0f;
    private static final float ALPHA = 0.8f;

    public AccelerometerMonitor(Context ctx) {
        sensorManager = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accel == null) {
            Log.e(TAG, "No accelerometer found on this device!");
        }
    }

    public boolean isAvailable() {
        return accel != null;
    }

    public boolean isActive() {
        return active;
    }

    public void setListener(SpankListener l) {
        listener = l;
    }

    public void setSensitivity(float threshold) {
        sensitivityThreshold = threshold;
    }

    public void setCooldownMs(long ms) {
        cooldownMs = ms;
    }

    /** Start listening at high rate (SENSOR_DELAY_GAME ≈ 20 ms / 50 Hz). */
    public void start() {
        if (accel == null) return;
        active = true;
        ring.clear();
        ringSum = 0f;
        gx = gy = gz = 0f;
        // SENSOR_DELAY_GAME gives ~50 Hz on most devices, good balance of
        // responsiveness vs battery. For older/weaker devices this is fine.
        sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME);
        Log.d(TAG, "Accelerometer started");
    }

    /** Stop listening and release sensor resources. */
    public void stop() {
        active = false;
        sensorManager.unregisterListener(this);
        Log.d(TAG, "Accelerometer stopped");
    }

    // ── SensorEventListener ──────────────────────────────────────────────────────

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!active || listener == null) return;

        float rawX = event.values[0];
        float rawY = event.values[1];
        float rawZ = event.values[2];

        // Low-pass filter isolates gravity component (same idea as Apple IMU baseline)
        gx = ALPHA * gx + (1f - ALPHA) * rawX;
        gy = ALPHA * gy + (1f - ALPHA) * rawY;
        gz = ALPHA * gz + (1f - ALPHA) * rawZ;

        // High-pass: remove gravity, keep linear acceleration
        float lx = rawX - gx;
        float ly = rawY - gy;
        float lz = rawZ - gz;

        // Magnitude of linear acceleration vector (in m/s²)
        float mag = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);

        // Convert m/s² → g  (1 g ≈ 9.81 m/s²)
        float magG = mag / SensorManager.GRAVITY_EARTH;

        // Update rolling ring buffer (baseline noise floor)
        if (ring.size() >= RING_SIZE) {
            ringSum -= ring.removeFirst();
        }
        ring.addLast(magG);
        ringSum += magG;
        float baseline = ringSum / ring.size();

        // Impact detection: magnitude spike above (baseline + user threshold)
        float spike = magG - baseline;
        if (spike >= sensitivityThreshold) {
            long nowNs     = event.timestamp;
            long nowMs     = nowNs / 1_000_000L;
            long lastMs    = lastTriggerNs / 1_000_000L;

            if ((nowMs - lastMs) >= cooldownMs) {
                lastTriggerNs = nowNs;
                // Amplitude normalised to [0,1] range for volume scaling
                float normalized = Math.min(1f, spike / (sensitivityThreshold * 3f));
                listener.onSpank(normalized);
                // Reset ring to avoid triggering on the rebound
                ring.clear();
                ringSum = 0f;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not needed
    }
}

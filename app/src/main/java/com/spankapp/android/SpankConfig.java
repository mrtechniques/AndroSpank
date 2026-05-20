package com.spankapp.android;

import com.spankapp.android.modes.SpankMode;

/**
 * Runtime configuration for the spank detector.
 * All values are validated on set.
 */
public class SpankConfig {

    // ── Defaults ────────────────────────────────────────────────────────────────
    public static final float  DEFAULT_SENSITIVITY    = 0.1f;
    public static final float  MIN_SENSITIVITY        = 0.1f;
    public static final float  MAX_SENSITIVITY        = 0.5f;

    public static final long   DEFAULT_COOLDOWN_MS    = 800L;   // ms between triggers
    public static final long   MIN_COOLDOWN_MS        = 100L;
    public static final long   MAX_COOLDOWN_MS        = 5000L;

    public static final float  DEFAULT_VOLUME         = 1.0f;
    public static final SpankMode DEFAULT_MODE        = SpankMode.PAIN;

    // ── Fields ──────────────────────────────────────────────────────────────────
    private float      sensitivity;
    private long       cooldownMs;
    private float      volume;
    private SpankMode  mode;
    private boolean    vibrateOnHit;
    private boolean    runInBackground;
    private boolean    volumeScaling;     // louder the harder you hit
    private String     customSoundUri;    // URI string for custom sound mode

    public SpankConfig() {
        reset();
    }

    public void reset() {
        sensitivity     = DEFAULT_SENSITIVITY;
        cooldownMs      = DEFAULT_COOLDOWN_MS;
        volume          = DEFAULT_VOLUME;
        mode            = DEFAULT_MODE;
        vibrateOnHit    = false;
        runInBackground = true;
        volumeScaling   = false;
        customSoundUri  = null;
    }

    // ── Getters / Setters ────────────────────────────────────────────────────────
    public float getSensitivity() { return sensitivity; }
    public void setSensitivity(float v) {
        sensitivity = Math.max(MIN_SENSITIVITY, Math.min(MAX_SENSITIVITY, v));
    }

    public long getCooldownMs() { return cooldownMs; }
    public void setCooldownMs(long ms) {
        cooldownMs = Math.max(MIN_COOLDOWN_MS, Math.min(MAX_COOLDOWN_MS, ms));
    }

    public float getVolume() { return volume; }
    public void setVolume(float v) { volume = Math.max(0f, Math.min(1f, v)); }

    public SpankMode getMode() { return mode; }
    public void setMode(SpankMode m) { mode = m; }

    public boolean isVibrateOnHit() { return vibrateOnHit; }
    public void setVibrateOnHit(boolean v) { vibrateOnHit = v; }

    public boolean isRunInBackground() { return runInBackground; }
    public void setRunInBackground(boolean v) { runInBackground = v; }

    public boolean isVolumeScaling() { return volumeScaling; }
    public void setVolumeScaling(boolean v) { volumeScaling = v; }

    public String getCustomSoundUri() { return customSoundUri; }
    public void setCustomSoundUri(String uri) { this.customSoundUri = uri; }
}

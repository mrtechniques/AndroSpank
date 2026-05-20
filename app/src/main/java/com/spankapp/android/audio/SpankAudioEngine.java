package com.spankapp.android.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.util.Log;
import android.util.SparseIntArray;

import com.spankapp.android.R;
import com.spankapp.android.modes.SpankMode;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * SpankAudioEngine
 *
 * Manages sound packs per SpankMode and plays them via SoundPool for low latency.
 * Mirrors the embedded MP3 audio packs from taigrr/spank:
 *   pain/   → 10 "ow!" sounds
 *   sexy/   → 60 sounds (escalating with hit count)
 *   halo/   → 9 Halo-style death sounds
 *   lizard/ → escalating (same as sexy, different sounds)
 *   goat/   → screaming goat sounds
 *   fart/   → flatulence sounds
 *
 * NOTE: The actual MP3 files should be placed in res/raw/ with names following
 *       the naming convention below (pain_01 … pain_10, sexy_01 … sexy_60 etc.)
 *       For a working APK, add real audio assets. This engine handles all the
 *       SoundPool lifecycle, escalation logic, and volume scaling.
 */
public class SpankAudioEngine {

    private static final String TAG = "SpankAudioEngine";

    private final Context    ctx;
    private SoundPool        soundPool;
    private final Random     rng = new Random();

    // Map: mode → list of loaded sound IDs (SoundPool IDs)
    private final Map<SpankMode, int[]> packs = new HashMap<>();
    private boolean loaded = false;

    private int    customSoundId = -1;
    private String lastCustomUri = null;

    // Escalation state (for SEXY / LIZARD modes)
    private int  hitCount      = 0;
    private long escalationWindowMs = 60_000L;
    private long firstHitInWindowMs = 0L;

    // ── Sound resource IDs per mode ──────────────────────────────────────────────
    // These map to res/raw/pain_01.mp3 … etc.
    // Update R.raw.* entries to match your actual asset files.
    private static final int[][] PAIN_SOUNDS = {
        { R.raw.pain_01, R.raw.pain_02, R.raw.pain_03, R.raw.pain_04, R.raw.pain_05,
          R.raw.pain_06, R.raw.pain_07, R.raw.pain_08, R.raw.pain_09, R.raw.pain_10 }
    };

    private static final int SEXY_LEVELS = 5;   // 5 intensity tiers × 12 sounds each
    // sexy_tier1_01…12, sexy_tier2_01…12, …  packed as a flat array per tier
    // For simplicity we reference them via naming convention resolved at load time.

    public SpankAudioEngine(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    /** Build SoundPool and preload all packs. Call once during service start. */
    public void load() {
        AudioAttributes attrs = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();

        soundPool = new SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(attrs)
            .build();

        soundPool.setOnLoadCompleteListener((sp, id, status) -> {
            if (status == 0) {
                Log.d(TAG, "Sound loaded id=" + id);
            } else {
                Log.e(TAG, "Sound failed to load id=" + id + " status=" + status);
            }
        });

        loadPack(SpankMode.PAIN,   getResourcesForPack("pain", 10));
        loadPack(SpankMode.SEXY,   getResourcesForPack("sexy", 60));
        loadPack(SpankMode.HALO,   getResourcesForPack("halo", 9));
        loadPack(SpankMode.FART,   getResourcesForPack("fart", 3));

        loaded = true;
        Log.d(TAG, "Audio engine loaded");
    }

    private void loadPack(SpankMode mode, int[] resIds) {
        int[] soundIds = new int[resIds.length];
        for (int i = 0; i < resIds.length; i++) {
            try {
                soundIds[i] = soundPool.load(ctx, resIds[i], 1);
            } catch (Exception e) {
                Log.w(TAG, "Could not load sound for " + mode + " index " + i + ": " + e.getMessage());
                soundIds[i] = -1;
            }
        }
        packs.put(mode, soundIds);
    }

    /**
     * Play a sound for the given mode.
     * @param mode       current SpankMode
     * @param amplitude  normalised hit strength [0,1] for volume scaling
     * @param baseVolume base volume setting [0,1]
     * @param volumeScaling whether to scale volume with hit strength
     */
    public void play(SpankMode mode, float amplitude, float baseVolume, boolean volumeScaling) {
        if (!loaded || soundPool == null) return;

        float vol = volumeScaling
            ? baseVolume * (0.5f + amplitude * 0.5f)   // amplitude influences volume
            : baseVolume;
        vol = Math.min(1f, vol);

        if (mode == SpankMode.CUSTOM) {
            if (customSoundId > 0) {
                soundPool.play(customSoundId, vol, vol, 1, 0, 1.0f);
            }
            return;
        }

        int[] pack = packs.get(mode);
        if (pack == null || pack.length == 0) return;

        int soundId = pickSound(mode, pack, amplitude);
        if (soundId > 0) {
            Log.d(TAG, "Playing sound mode=" + mode + " id=" + soundId + " vol=" + vol);
            soundPool.play(soundId, vol, vol, 1, 0, 1.0f);
        } else {
            Log.w(TAG, "Sound not ready or failed to load for mode=" + mode);
        }
    }

    private int pickSound(SpankMode mode, int[] pack, float amplitude) {
        if (mode == SpankMode.SEXY) {
            return pickEscalating(pack, amplitude);
        }
        // Random pick for non-escalating modes
        return pack[rng.nextInt(pack.length)];
    }

    private int pickEscalating(int[] pack, float amplitude) {
        long now = System.currentTimeMillis();
        if (now - firstHitInWindowMs > escalationWindowMs) {
            hitCount = 0;
            firstHitInWindowMs = now;
        }
        hitCount++;

        // Divide pack into 5 intensity tiers
        int tierCount = 5;
        int tierSize  = Math.max(1, pack.length / tierCount);
        // Which tier based on hit count (escalates over ~20 hits)
        int tier = Math.min(tierCount - 1, hitCount / 4);
        int start = tier * tierSize;
        int end   = Math.min(pack.length, start + tierSize);
        if (start >= pack.length) start = 0;
        int idx = start + rng.nextInt(end - start);
        return pack[idx];
    }

    public void loadCustom(String uriString) {
        if (!loaded || soundPool == null || uriString == null || uriString.isEmpty()) return;
        if (uriString.equals(lastCustomUri)) return;

        try {
            android.net.Uri uri = android.net.Uri.parse(uriString);
            android.content.res.AssetFileDescriptor afd = ctx.getContentResolver().openAssetFileDescriptor(uri, "r");
            if (afd != null) {
                // SoundPool load is async, but we can't easily wait here.
                // Re-loading the same ID if it fails is handled by status status in listener.
                int newId = soundPool.load(afd, 1);
                if (newId > 0) {
                    if (customSoundId > 0) soundPool.unload(customSoundId);
                    customSoundId = newId;
                    lastCustomUri = uriString;
                    Log.d(TAG, "Custom sound loading started: " + uriString);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load custom sound: " + e.getMessage());
        }
    }

    public void resetEscalation() {
        hitCount = 0;
        firstHitInWindowMs = 0;
    }

    /** Release all SoundPool resources. */
    public void release() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        packs.clear();
        loaded = false;
    }

    // ── Resource helpers ─────────────────────────────────────────────────────────

    private int[] getResourcesForPack(String prefix, int count) {
        int[] resIds = new int[count];
        for (int i = 0; i < count; i++) {
            String name = String.format(java.util.Locale.US, "%s_%02d", prefix, i + 1);
            resIds[i] = ctx.getResources().getIdentifier(name, "raw", ctx.getPackageName());
            if (resIds[i] == 0) {
                Log.w(TAG, "Resource not found: " + name);
            }
        }
        return resIds;
    }
}

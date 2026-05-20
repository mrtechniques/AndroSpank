package com.spankapp.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.spankapp.android.audio.SpankAudioEngine;
import com.spankapp.android.sensor.AccelerometerMonitor;
import com.spankapp.android.ui.MainActivity;

/**
 * SpankService
 *
 * Foreground service that keeps the accelerometer + audio alive even when the
 * app is in the background (user toggles "Run in background" in settings).
 *
 * Bound service pattern: MainActivity binds to it to pass config updates.
 */
public class SpankService extends Service {

    private static final String TAG             = "SpankService";
    private static final String CHANNEL_ID      = "spank_channel";
    private static final int    NOTIFICATION_ID = 1;

    public static final String ACTION_START = "com.spankapp.android.ACTION_START";
    public static final String ACTION_STOP  = "com.spankapp.android.ACTION_STOP";
    private static final String PREFS_NAME  = "spank_prefs";

    // ── Binder ───────────────────────────────────────────────────────────────────
    public class SpankBinder extends Binder {
        public SpankService getService() { return SpankService.this; }
    }

    private final IBinder binder = new SpankBinder();

    // ── Components ───────────────────────────────────────────────────────────────
    private AccelerometerMonitor accelerometer;
    private SpankAudioEngine     audioEngine;
    private Vibrator             vibrator;

    // ── Config (updated from UI) ─────────────────────────────────────────────────
    private SpankConfig config = new SpankConfig();

    // ── Stats ────────────────────────────────────────────────────────────────────
    private int  totalSpanks = 0;
    private long lastSpankMs = 0L;

    public interface SpankEventListener {
        void onSpankDetected(int total, float amplitude);
    }
    private SpankEventListener eventListener;

    // ── Lifecycle ────────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        audioEngine = new SpankAudioEngine(this);
        audioEngine.load();

        accelerometer = new AccelerometerMonitor(this);
        accelerometer.setListener(this::handleSpank);
        applyConfig();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_STOP.equals(action)) {
                stopListening();
                stopForeground(true);
                stopSelf();
                return START_NOT_STICKY;
            } else if (ACTION_START.equals(action)) {
                loadConfigFromPrefs();
                startListening();
                startForeground(NOTIFICATION_ID, buildNotification());
            }
        }
        return START_STICKY;
    }

    private void loadConfigFromPrefs() {
        android.content.SharedPreferences p = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        config.setSensitivity(p.getFloat("sensitivity",  SpankConfig.DEFAULT_SENSITIVITY));
        config.setCooldownMs(p.getLong("cooldown",       SpankConfig.DEFAULT_COOLDOWN_MS));
        config.setVolume(p.getFloat("volume",            SpankConfig.DEFAULT_VOLUME));
        config.setCustomSoundUri(p.getString("custom_uri", null));
        try {
            config.setMode(com.spankapp.android.modes.SpankMode.valueOf(
                p.getString("mode", SpankConfig.DEFAULT_MODE.name())));
        } catch (Exception ignored) {}
        config.setVibrateOnHit(p.getBoolean("vibrate",   true));
        config.setVolumeScaling(p.getBoolean("volscale", true));
        config.setRunInBackground(p.getBoolean("background", false));
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        accelerometer.stop();
        audioEngine.release();
        Log.d(TAG, "Service destroyed");
    }

    // ── Public API ───────────────────────────────────────────────────────────────

    public void startListening() {
        applyConfig();
        accelerometer.start();
        Log.d(TAG, "Listening for spanks in " + config.getMode().displayName + " mode");
    }

    public void stopListening() {
        accelerometer.stop();
    }

    public void updateConfig(SpankConfig newConfig) {
        this.config = newConfig;
        applyConfig();
    }

    public void setEventListener(SpankEventListener l) {
        eventListener = l;
    }

    public int getTotalSpanks() { return totalSpanks; }
    public long getLastSpankMs() { return lastSpankMs; }
    public boolean isListening() {
        return accelerometer != null && accelerometer.isAvailable();
    }

    /** Manually trigger a spank (for testing/UI). */
    public void triggerTestSpank(float amplitude) {
        handleSpank(amplitude);
    }

    // ── Internal ─────────────────────────────────────────────────────────────────

    private void applyConfig() {
        accelerometer.setSensitivity(config.getSensitivity());
        accelerometer.setCooldownMs(config.getCooldownMs());
        if (config.getMode() == com.spankapp.android.modes.SpankMode.CUSTOM) {
            String uri = config.getCustomSoundUri();
            Log.d(TAG, "Loading custom sound: " + uri);
            audioEngine.loadCustom(uri);
        }
    }

    private void handleSpank(float amplitude) {
        totalSpanks++;
        lastSpankMs = System.currentTimeMillis();

        Log.d(TAG, "SPANK! #" + totalSpanks + " amplitude=" + amplitude);

        // Play audio
        audioEngine.play(
            config.getMode(),
            amplitude,
            config.getVolume(),
            config.isVolumeScaling()
        );

        // Haptic feedback
        if (config.isVibrateOnHit() && vibrator != null) {
            long durationMs = (long)(50 + amplitude * 100); // 50–150 ms
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(
                    durationMs,
                    (int)(amplitude * 255)
                ));
            } else {
                vibrator.vibrate(durationMs);
            }
        }

        // Notify UI
        if (eventListener != null) {
            eventListener.onSpankDetected(totalSpanks, amplitude);
        }

        // Update notification count
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    // ── Notification ─────────────────────────────────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                "SpankApp",
                NotificationManager.IMPORTANCE_LOW
            );
            ch.setDescription("SpankApp is listening for hits");
            ch.setSound(null, null);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        createNotificationChannel();

        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent stopIntent = new Intent(this, SpankService.class);
        stopIntent.setAction("ACTION_STOP");
        PendingIntent stopPending = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String modeLabel = config.getMode().emoji + " " + config.getMode().displayName;

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("SpankApp is listening")
            .setContentText(modeLabel + " mode · " + totalSpanks + " spanks")
            .setContentIntent(openPending)
            .addAction(R.drawable.ic_stop, "Stop", stopPending)
            .setOngoing(true)
            .setSilent(true)
            .build();
    }
}

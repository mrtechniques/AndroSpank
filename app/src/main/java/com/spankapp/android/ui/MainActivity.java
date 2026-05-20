package com.spankapp.android.ui;

import android.animation.ObjectAnimator;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;

import com.spankapp.android.R;
import com.spankapp.android.SpankConfig;
import com.spankapp.android.SpankService;
import com.spankapp.android.modes.SpankMode;

/**
 * MainActivity
 *
 * Single-screen UI:
 *  • Large animated "SPANK" button (pulse on hit)
 *  • Mode chip group
 *  • Sensitivity + Cooldown sliders
 *  • Volume / vibrate / background toggles
 *  • Live hit counter & amplitude bar
 */
public class MainActivity extends AppCompatActivity
    implements SpankService.SpankEventListener {

    private static final String PREFS_NAME = "spank_prefs";

    // ── Service ──────────────────────────────────────────────────────────────────
    private SpankService  service;
    private boolean       bound = false;
    private boolean       listening = false;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder ib) {
            SpankService.SpankBinder b = (SpankService.SpankBinder) ib;
            service = b.getService();
            service.setEventListener(MainActivity.this);
            bound = true;
            listening = service.isListening();
            updateListeningUI();
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
            service = null;
        }
    };

    // ── Views ────────────────────────────────────────────────────────────────────
    private ImageView       spankButton;
    private TextView        tvStatus;
    private TextView        tvHitCount;
    private TextView        tvLastAmplitude;
    private View            amplitudeBar;
    private SeekBar         seekSensitivity;
    private SeekBar         seekCooldown;
    private SeekBar         seekVolume;
    private TextView        tvSensitivityVal;
    private TextView        tvCooldownVal;
    private TextView        tvVolumeVal;
    private ChipGroup       chipGroupMode;
    private View            cardCustomSound;
    private TextView        tvCustomSoundPath;
    private MaterialButton  btnPickSound;
    private SwitchMaterial  switchVibrate;
    private SwitchMaterial  switchVolumeScale;
    private SwitchMaterial  switchBackground;
    private MaterialButton  btnToggle;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private SpankConfig   config    = new SpankConfig();

    private final androidx.activity.result.ActivityResultLauncher<String[]> soundPickerLauncher =
        registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) {
                try {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    config.setCustomSoundUri(uri.toString());
                    updateCustomSoundLabel();
                    pushConfig();
                    savePrefs();
                } catch (Exception e) {
                    Toast.makeText(this, "Failed to use this file: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });

    private final androidx.activity.result.ActivityResultLauncher<String[]> permissionLauncher =
        registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(), result -> {
            boolean allGranted = true;
            for (Boolean granted : result.values()) {
                if (!granted) allGranted = false;
            }
            if (!allGranted) {
                Toast.makeText(this, "Permissions required for full functionality", Toast.LENGTH_SHORT).show();
            }
        });

    // ── Lifecycle ────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        loadPrefs();
        checkPermissions();
        bindViews();
        setupModeChips();
        setupSliders();
        setupToggles();
        setupSpankButton();
        setupToggleButton();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, SpankService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        savePrefs();
        if (bound) {
            unbindService(connection);
            bound = false;
        }
    }

    // ── View binding ─────────────────────────────────────────────────────────────

    private void bindViews() {
        spankButton        = findViewById(R.id.iv_spank_button);
        tvStatus           = findViewById(R.id.tv_status);
        tvHitCount         = findViewById(R.id.tv_hit_count);
        tvLastAmplitude    = findViewById(R.id.tv_amplitude);
        amplitudeBar       = findViewById(R.id.view_amplitude_bar);
        seekSensitivity    = findViewById(R.id.seek_sensitivity);
        seekCooldown       = findViewById(R.id.seek_cooldown);
        seekVolume         = findViewById(R.id.seek_volume);
        tvSensitivityVal   = findViewById(R.id.tv_sensitivity_val);
        tvCooldownVal      = findViewById(R.id.tv_cooldown_val);
        tvVolumeVal        = findViewById(R.id.tv_volume_val);
        chipGroupMode      = findViewById(R.id.chip_group_mode);
        cardCustomSound    = findViewById(R.id.card_custom_sound);
        tvCustomSoundPath  = findViewById(R.id.tv_custom_sound_path);
        btnPickSound       = findViewById(R.id.btn_pick_sound);
        switchVibrate      = findViewById(R.id.switch_vibrate);
        switchVolumeScale  = findViewById(R.id.switch_volume_scale);
        switchBackground   = findViewById(R.id.switch_background);
        btnToggle          = findViewById(R.id.btn_toggle);
    }

    private void checkPermissions() {
        java.util.List<String> permissions = new java.util.ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS);
            permissions.add(android.Manifest.permission.READ_MEDIA_AUDIO);
        } else {
            permissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        
        // Request battery optimization ignore
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent intent = new Intent();
                intent.setAction(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                try {
                    startActivity(intent);
                } catch (Exception ignored) {}
            }
        }

        if (!permissions.isEmpty()) {
            permissionLauncher.launch(permissions.toArray(new String[0]));
        }
    }

    // ── Mode chips ───────────────────────────────────────────────────────────────

    private void setupModeChips() {
        chipGroupMode.removeAllViews();
        chipGroupMode.setSingleSelection(true);

        for (SpankMode mode : SpankMode.values()) {
            Chip chip = new Chip(this);
            chip.setText(mode.emoji + " " + mode.displayName);
            chip.setCheckable(true);
            chip.setTag(mode);
            chip.setChecked(mode == config.getMode());
            chipGroupMode.addView(chip);
        }

        updateCustomSoundVisibility();
        updateCustomSoundLabel();

        chipGroupMode.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            View v = group.findViewById(ids.get(0));
            if (v instanceof Chip) {
                SpankMode selected = (SpankMode) v.getTag();
                config.setMode(selected);
                pushConfig();
                updateCustomSoundVisibility();

                // Show description toast
                Toast.makeText(this,
                    selected.description, Toast.LENGTH_SHORT).show();
            }
        });

        btnPickSound.setOnClickListener(v -> {
            soundPickerLauncher.launch(new String[]{"audio/*"});
        });
    }

    private void updateCustomSoundVisibility() {
        cardCustomSound.setVisibility(config.getMode() == SpankMode.CUSTOM ? View.VISIBLE : View.GONE);
    }

    private void updateCustomSoundLabel() {
        if (config.getCustomSoundUri() != null) {
            tvCustomSoundPath.setText(config.getCustomSoundUri());
        } else {
            tvCustomSoundPath.setText("No file selected");
        }
    }

    // ── Sliders ───────────────────────────────────────────────────────────────────

    private void setupSliders() {
        // Sensitivity: [0.1 … 0.5] g, mapped to SeekBar [0…400]
        seekSensitivity.setMax(400);
        seekSensitivity.setProgress(sensitivityToProgress(config.getSensitivity()));
        updateSensitivityLabel();
        seekSensitivity.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                config.setSensitivity(progressToSensitivity(p));
                updateSensitivityLabel();
                pushConfig();
            }
        });

        // Cooldown: [100 … 5000] ms, SeekBar [0…490]
        seekCooldown.setMax(490);
        seekCooldown.setProgress(cooldownToProgress(config.getCooldownMs()));
        updateCooldownLabel();
        seekCooldown.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                config.setCooldownMs(progressToCooldown(p));
                updateCooldownLabel();
                pushConfig();
            }
        });

        // Volume: [0…100]
        seekVolume.setMax(100);
        seekVolume.setProgress((int)(config.getVolume() * 100));
        updateVolumeLabel();
        seekVolume.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                config.setVolume(p / 100f);
                updateVolumeLabel();
                pushConfig();
            }
        });
    }

    private float progressToSensitivity(int p) {
        // [0,400] → [0.1, 0.5]
        return 0.1f + p * (0.4f / 400f);
    }
    private int sensitivityToProgress(float v) {
        return (int)((v - 0.1f) * 400f / 0.4f);
    }
    private long progressToCooldown(long p) {
        // [0,490] → [100, 5000] ms (10 ms steps)
        return 100L + p * 10L;
    }
    private int cooldownToProgress(long ms) {
        return (int)((ms - 100L) / 10L);
    }

    private void updateSensitivityLabel() {
        tvSensitivityVal.setText(String.format("%.1f g", config.getSensitivity()));
    }
    private void updateCooldownLabel() {
        tvCooldownVal.setText(config.getCooldownMs() + " ms");
    }
    private void updateVolumeLabel() {
        tvVolumeVal.setText((int)(config.getVolume() * 100) + "%");
    }

    // ── Toggles ───────────────────────────────────────────────────────────────────

    private void setupToggles() {
        switchVibrate.setChecked(config.isVibrateOnHit());
        switchVibrate.setOnCheckedChangeListener((v, c) -> {
            config.setVibrateOnHit(c);
            pushConfig();
            savePrefs();
        });

        switchVolumeScale.setChecked(config.isVolumeScaling());
        switchVolumeScale.setOnCheckedChangeListener((v, c) -> {
            config.setVolumeScaling(c);
            pushConfig();
            savePrefs();
        });

        switchBackground.setChecked(config.isRunInBackground());
        switchBackground.setOnCheckedChangeListener((v, c) -> {
            config.setRunInBackground(c);
            pushConfig();
            savePrefs();
            if (c) {
                // Promote to foreground service
                Intent i = new Intent(this, SpankService.class);
                i.setAction(SpankService.ACTION_START);
                androidx.core.content.ContextCompat.startForegroundService(this, i);
            }
        });
    }

    // ── Big spank button (visual only, triggers test hit) ────────────────────────

    private void setupSpankButton() {
        spankButton.setOnClickListener(v -> {
            // Manually trigger a test sound at 50% amplitude
            if (bound && service != null) {
                service.triggerTestSpank(0.5f);
            } else {
                // If not bound, just do the animation
                animateHit(0.5f);
            }
        });
    }

    // ── Start / Stop toggle ──────────────────────────────────────────────────────

    private void setupToggleButton() {
        btnToggle.setOnClickListener(v -> {
            if (!bound || service == null) return;
            Intent intent = new Intent(this, SpankService.class);
            if (listening) {
                intent.setAction(SpankService.ACTION_STOP);
                startService(intent);
                listening = false;
            } else {
                savePrefs(); // Ensure service gets latest values
                intent.setAction(SpankService.ACTION_START);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent);
                } else {
                    startService(intent);
                }
                listening = true;
            }
            updateListeningUI();
        });
    }

    private void updateListeningUI() {
        if (listening) {
            btnToggle.setText("■  Stop");
            btnToggle.setBackgroundColor(getColor(R.color.stop_color));
            tvStatus.setText("👂 Listening… slap me!");
        } else {
            btnToggle.setText("▶  Start Listening");
            btnToggle.setBackgroundColor(getColor(R.color.start_color));
            tvStatus.setText("Press Start to begin");
        }
    }

    // ── SpankEventListener ───────────────────────────────────────────────────────

    @Override
    public void onSpankDetected(int total, float amplitude) {
        uiHandler.post(() -> {
            tvHitCount.setText(String.valueOf(total));
            tvLastAmplitude.setText(String.format("%.0f%%", amplitude * 100));
            animateHit(amplitude);
        });
    }

    private void animateHit(float amplitude) {
        // Pulse the big spank icon
        float scale = 1.0f + amplitude * 0.4f;
        spankButton.animate()
            .scaleX(scale)
            .scaleY(scale)
            .setDuration(80)
            .withEndAction(() ->
                spankButton.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(200)
                    .setInterpolator(new OvershootInterpolator())
                    .start()
            ).start();

        // Amplitude bar
        amplitudeBar.animate()
            .scaleX(amplitude)
            .setDuration(100)
            .withEndAction(() ->
                amplitudeBar.animate().scaleX(0).setDuration(500).start()
            ).start();
    }

    // ── Config push ───────────────────────────────────────────────────────────────

    private void pushConfig() {
        if (bound && service != null) {
            service.updateConfig(config);
        }
    }

    // ── Prefs ─────────────────────────────────────────────────────────────────────

    private void savePrefs() {
        SharedPreferences.Editor e = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        e.putFloat("sensitivity",  config.getSensitivity());
        e.putLong("cooldown",      config.getCooldownMs());
        e.putFloat("volume",       config.getVolume());
        e.putString("mode",        config.getMode().name());
        e.putString("custom_uri",  config.getCustomSoundUri());
        e.putBoolean("vibrate",    config.isVibrateOnHit());
        e.putBoolean("volscale",   config.isVolumeScaling());
        e.putBoolean("background", config.isRunInBackground());
        e.apply();
    }

    private void loadPrefs() {
        SharedPreferences p = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        config.setSensitivity(p.getFloat("sensitivity",  SpankConfig.DEFAULT_SENSITIVITY));
        config.setCooldownMs(p.getLong("cooldown",       SpankConfig.DEFAULT_COOLDOWN_MS));
        config.setVolume(p.getFloat("volume",            SpankConfig.DEFAULT_VOLUME));
        config.setCustomSoundUri(p.getString("custom_uri", null));
        try {
            config.setMode(SpankMode.valueOf(p.getString("mode", SpankMode.PAIN.name())));
        } catch (Exception ignored) {}
        config.setVibrateOnHit(p.getBoolean("vibrate",   false));
        config.setVolumeScaling(p.getBoolean("volscale", false));
        config.setRunInBackground(p.getBoolean("background", true));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

    /** Minimal SeekBar listener to avoid boilerplate. */
    private abstract static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar s) {}
        @Override public void onStopTrackingTouch(SeekBar s) {}
    }
}

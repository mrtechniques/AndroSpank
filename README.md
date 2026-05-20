# 👋 SpankApp for Android

> Slap your Android phone — it yells back.  
> An Android port inspired by [taigrr/spank](https://github.com/taigrr/spank) for macOS.

---

## How it works

The Mac original uses Apple Silicon's undocumented IOKit HID accelerometer to detect physical impacts.  
Android has had a standard accelerometer API since Android 1.0 — this app uses the same physics, ported to Java:

| macOS (taigrr/spank)              | Android (this app)                            |
|-----------------------------------|-----------------------------------------------|
| Bosch BMI286 via IOKit HID        | `Sensor.TYPE_ACCELEROMETER` via SensorManager |
| Ring-buffer baseline (~100 Hz)    | Rolling 50-sample ring buffer (~50 Hz)         |
| Amplitude spike detection         | High-pass filter + magnitude spike detection   |
| `--sexy` escalation mode          | Escalating intensity with hit count            |
| JSON tuning via stdin             | Sliders in UI, persisted in SharedPreferences  |
| Embedded MP3 packs                | `res/raw/` MP3s via SoundPool (low latency)   |
| sudo required                     | No root required ✅                            |

---

## Features

### 🎭 Six modes
| Mode     | Description                                               |
|----------|-----------------------------------------------------------|
| 😖 Pain   | Classic "Ow!" pain reactions                             |
| 💋 Sexy   | Escalating intensity — the more you spank, the spicier   |
| 🎮 Halo   | Halo death sounds on every hit                           |
| 🦎 Lizard | Like Sexy but with lizard energy                         |
| 🐐 Goat   | Screaming goat for maximum chaos                         |
| 💨 Fart   | Toilet humour mode. Classic.                             |

### ⚙️ Controls
- **Sensitivity slider** — 0.5 g to 10.0 g (how hard you need to hit)
- **Cooldown slider** — 100 ms to 5000 ms (prevents sound spam on rebounds)
- **Volume slider** — 0–100%
- **Dynamic volume** — louder for harder hits (like the Mac version's amplitude-based scaling)
- **Vibrate on hit** — haptic feedback
- **Run in background** — foreground service keeps it alive when minimised

### 📊 Stats
- Live hit counter
- Last hit strength (%)
- Animated spank button pulses on each detection

---

## Project structure

```
SpankApp-Android/
├── app/src/main/
│   ├── java/com/spankapp/android/
│   │   ├── sensor/
│   │   │   └── AccelerometerMonitor.java   ← Core impact detection
│   │   ├── audio/
│   │   │   └── SpankAudioEngine.java       ← SoundPool + mode escalation
│   │   ├── modes/
│   │   │   └── SpankMode.java              ← Mode enum (Pain/Sexy/Halo/…)
│   │   ├── ui/
│   │   │   └── MainActivity.java           ← Full UI
│   │   ├── SpankConfig.java                ← Runtime tuning params
│   │   └── SpankService.java               ← Foreground service
│   ├── res/
│   │   ├── layout/activity_main.xml
│   │   ├── raw/                            ← Audio files go here
│   │   └── values/
└── generate_placeholders.py               ← Creates silent MP3 stubs
```

---

## Adding real audio

1. Run `generate_placeholders.py` once to create the stubs (already done).
2. Replace each stub in `app/src/main/res/raw/` with a real MP3.

### Naming convention
```
pain_01.mp3 … pain_10.mp3        ← "ow!" sounds
sexy_01.mp3 … sexy_20.mp3        ← escalating (tier 1→4 = 5 files each)
halo_01.mp3 … halo_09.mp3        ← death sounds
lizard_01.mp3 … lizard_10.mp3    ← lizard reactions
goat_01.mp3 … goat_05.mp3        ← screaming goats
fart_01.mp3 … fart_05.mp3        ← you know
```

### Where to get sounds
- **Pain/Sexy/Halo/Lizard**: download from the [taigrr/spank releases page](https://github.com/taigrr/spank/releases) (open source)
- **Goat**: search "screaming goat" on [myinstants.com](https://myinstants.com) or [freesound.org](https://freesound.org)
- **Fart**: any royalty-free SFX library
- **Generate custom TTS pain sounds**: ElevenLabs / Google TTS

---

## Building

```bash
# Clone
git clone <this-repo>
cd SpankApp-Android

# Generate placeholder audio stubs (if not already done)
python3 generate_placeholders.py

# Build debug APK
./gradlew assembleDebug

# Install on connected device
adb install app/build/outputs/apk/debug/app-debug.apk
```

Requires Android Studio Hedgehog or later, or Android command-line tools.  
**Min SDK: 26 (Android 8.0)** — works on any Android phone/tablet with an accelerometer.

---

## Detection algorithm

```
Raw accelerometer (x, y, z) in m/s²
         │
         ▼
Low-pass filter (α=0.8) → gravity vector (gx, gy, gz)
         │
         ▼
High-pass: linear_accel = raw - gravity
         │
         ▼
Magnitude = √(lx²+ly²+lz²) / 9.81  → in g
         │
         ▼
Rolling 50-sample ring buffer → baseline
         │
         ▼
spike = magnitude - baseline
         │
    spike ≥ threshold?  ──YES──▶  cooldown elapsed? ──YES──▶  FIRE onSpank()
         │                                │
        NO                               NO
         │                                │
    (ignore)                         (ignore)
```

---

## iOS version

Coming next! iOS will use `CMMotionManager` from CoreMotion — same physics, same modes.

---

## Credits

- Original concept: [taigrr/spank](https://github.com/taigrr/spank) — macOS implementation in Go
- Accelerometer research: [olvvier/apple-silicon-accelerometer](https://github.com/olvvier/apple-silicon-accelerometer)
- Android port: SpankApp-Android

---

*Don't break your screen. 📱*

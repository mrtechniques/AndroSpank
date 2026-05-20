# 👋 AndroSpank for Android

> Tap your phone — it yells back.  
> An ultra-sensitive Android port inspired by [taigrr/spank](https://github.com/taigrr/spank).

---

## 🚀 Overview

AndroSpank turns your Android device into a responsive soundboard triggered by physical movement. Whether it's a tap, a slap, or a shake, your phone responds instantly with a variety of sound packs or your own custom audio.

---

## ✨ Features

### 🎭 Available Modes
| Mode      | Description                                               |
|-----------|-----------------------------------------------------------|
| 😖 **Pain**   | Classic "Ow!" pain reactions.                             |
| 💋 **Sexy**   | Escalating intensity — 60 sounds that get spicier as you hit. |
| 🎮 **Halo**   | Random Halo death sounds on every hit.                    |
| 💨 **Fart**   | Three unique high-quality fart sounds.                    |
| 📁 **Custom** | **New!** Pick any audio file from your device storage.   |

### ⚙️ Smart Defaults & Controls
- **Ultra-Sensitivity** — Ranges from **0.1g to 0.5g**. Defaulted to 0.1g for maximum responsiveness.
- **Run in Background** — **Enabled by default**. Stays alive as a foreground service with a persistent notification.
- **Dynamic Volume & Vibrate** — Disabled by default for a cleaner experience.
- **Cooldown control** — 100 ms to 5000 ms to prevent sound spam.
- **Volume control** — Independent media volume scaling.

### 📁 Custom Sound Picker
Select the **Custom** mode to unlock the file picker. Grant the storage permission, select your favorite MP3 or WAV, and the app will play it every time a hit is detected.

---

## 🛠 How to Use

1. **Install the APK** and open AndroSpank.
2. **Grant Permissions**: Allow Notifications and Disable Battery Optimization when prompted (essential for background play).
3. **Select a Mode**: Choose from the built-in packs or pick your own file.
4. **Press Start**: Look for the notification to confirm the listener is active.
5. **Tap Anywhere**: Tap the screen, back, or side of your phone to trigger the sound!

---

## 🏗 Project Structure

```
AndroSpank/
├── app/src/main/
│   ├── java/com/spankapp/android/
│   │   ├── sensor/
│   │   │   └── AccelerometerMonitor.java   ← Core impact detection (0.1g threshold)
│   │   ├── audio/
│   │   │   └── SpankAudioEngine.java       ← SoundPool + Custom URI loading
│   │   ├── modes/
│   │   │   └── SpankMode.java              ← Mode definitions
│   │   ├── ui/
│   │   │   └── MainActivity.java           ← UI & Permission handling
│   │   └── SpankService.java               ← Persistent Foreground Service
│   ├── res/
│   │   ├── raw/                            ← Built-in MP3 packs
│   │   └── mipmap/                         ← Custom AndroSpank logo
└── README.md
```

---

## 🔧 Building from Source

1. Clone the repository.
2. Open in **Android Studio Ladybug** (or later).
3. Ensure you have the audio files in `app/src/main/res/raw/`.
4. Build the project using Gradle: `./gradlew assembleDebug`.

---

## ⚖️ Detection Algorithm

The app uses a high-frequency accelerometer stream (~50Hz) processed through a low-pass filter to isolate linear acceleration. 
`Spike = Current_Magnitude - Rolling_Baseline`
When `Spike > User_Threshold`, a sound is triggered.

---

## 📜 Credits

- Original concept: [taigrr/spank](https://github.com/taigrr/spank)
- Rebranded and Optimized for Android by **AndroSpank**.

---

*Handle your device with care. 📱*

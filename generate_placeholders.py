#!/usr/bin/env python3
"""
generate_placeholders.py
------------------------
Creates empty placeholder .mp3 files in app/src/main/res/raw/
so the project compiles. Replace these with real audio files before shipping.

Sound file naming convention (matches SpankAudioEngine.java):
  pain_01.mp3   …  pain_10.mp3
  sexy_01.mp3   …  sexy_20.mp3
  halo_01.mp3   …  halo_09.mp3
  lizard_01.mp3 …  lizard_10.mp3
  goat_01.mp3   …  goat_05.mp3
  fart_01.mp3   …  fart_05.mp3

Where to get sounds:
  • pain/halo/sexy/lizard — taigrr/spank GitHub repo (open source audio)
    https://github.com/taigrr/spank/tree/master/audio
  • goat — https://www.myinstants.com (search "screaming goat")
  • fart — any royalty-free SFX library
  • Generate with TTS: pain sounds → ElevenLabs / Google TTS
"""

import os
import struct

# Minimal valid MP3 header (32-byte silence) so Android's SoundPool doesn't crash
SILENT_MP3 = bytes([
    0xFF, 0xFB, 0x90, 0x00,  # MPEG1 Layer3 128kbps 44100Hz stereo
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
]) * 100  # 100 frames of silence

RAW_DIR = os.path.join(os.path.dirname(__file__),
    "app", "src", "main", "res", "raw")
os.makedirs(RAW_DIR, exist_ok=True)

packs = {
    "pain":   range(1, 11),   # 10 sounds
    "sexy":   range(1, 21),   # 20 sounds
    "halo":   range(1, 10),   #  9 sounds
    "lizard": range(1, 11),   # 10 sounds
    "goat":   range(1, 6),    #  5 sounds
    "fart":   range(1, 6),    #  5 sounds
}

count = 0
for pack, rng in packs.items():
    for i in rng:
        fname = f"{pack}_{i:02d}.mp3"
        fpath = os.path.join(RAW_DIR, fname)
        if not os.path.exists(fpath):
            with open(fpath, "wb") as f:
                f.write(SILENT_MP3)
            count += 1

print(f"Created {count} placeholder audio files in {RAW_DIR}")
print("⚠️  Replace with real audio files before shipping!")

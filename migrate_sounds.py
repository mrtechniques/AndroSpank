import os
import shutil

BASE_SRC = "app/src/main/java/com/spankapp/android/audio/sounds"
TARGET_DIR = "app/src/main/res/raw"

os.makedirs(TARGET_DIR, exist_ok=True)

packs = ["pain", "halo", "sexy", "lizard"]

for pack in packs:
    src_dir = os.path.join(BASE_SRC, pack)
    if not os.path.exists(src_dir):
        print(f"Skipping {pack}, directory not found.")
        continue

    files = sorted(os.listdir(src_dir))
    for i, fname in enumerate(files):
        if not fname.endswith(".mp3"):
            continue

        # New name: pack_01.mp3, pack_02.mp3, etc.
        new_name = f"{pack}_{i+1:02d}.mp3"
        src_path = os.path.join(src_dir, fname)
        target_path = os.path.join(TARGET_DIR, new_name)

        print(f"Moving {src_path} -> {target_path}")
        shutil.copy2(src_path, target_path)

print("Migration complete.")

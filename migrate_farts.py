import os
import shutil

SRC = "app/src/main/java/com/spankapp/android/audio/sounds/fart"
TARGET_DIR = "app/src/main/res/raw"

os.makedirs(TARGET_DIR, exist_ok=True)

if os.path.exists(SRC):
    files = sorted(os.listdir(SRC))
    for i, fname in enumerate(files):
        if not fname.endswith(".mp3"):
            continue
        new_name = f"fart_{i+1:02d}.mp3"
        src_path = os.path.join(SRC, fname)
        target_path = os.path.join(TARGET_DIR, new_name)
        print(f"Moving {src_path} -> {target_path}")
        shutil.copy2(src_path, target_path)

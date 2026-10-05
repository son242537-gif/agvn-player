# Copyright (c) 2026 agvn.io - MIT License (see LICENSE).
#
# AGVN Player's start file for Ren'Py games. librenpython.so runs ANDROID_PRIVATE/main.py; Ren'Py's own Android
# build puts its renpy.py there, with the game inside the APK. Here the game stays in its folder on the phone:
#   - AGVN_RENPY_BASEDIR: that folder; it becomes the base directory, as "renpy.py <folder>" does on a PC.
#   - AGVN_RENPY_NAME: the game's launcher name (MyGame.py next to MyGame.exe), which Ren'Py uses to find game/.
#   - AGVN_RENPY_QUIT_FILE: the app creates this file for "Thoát game"; the game then quits as when its window is
#     closed on a PC (its own question, persistent data saved).
# Then Ren'Py's renpy.py runs unchanged, in this module, so its path functions are the ones Ren'Py calls.

import os
import sys
import threading
import time

basedir = os.environ.get("AGVN_RENPY_BASEDIR")

if basedir:
    sys.argv = [os.path.join(basedir, os.environ.get("AGVN_RENPY_NAME") or "main") + ".py", basedir]


def watch_quit(path):
    while True:
        time.sleep(0.25)

        if not os.path.exists(path):
            continue

        pygame = sys.modules.get("renpy.pygame")

        try:
            pygame.event.post(pygame.event.Event(pygame.QUIT))
            os.unlink(path)
        except Exception as e:
            # Ren'Py not started yet: try again a little later
            print("AGVN: quit request waits:", e)
            time.sleep(2)


quit_file = os.environ.get("AGVN_RENPY_QUIT_FILE")

if quit_file:
    threading.Thread(target=watch_quit, args=(quit_file,), name="agvn-quit", daemon=True).start()

renpy_py = os.path.join(os.path.dirname(os.path.abspath(__file__)), "renpy.py")

with open(renpy_py, "rb") as f:
    code = compile(f.read(), renpy_py, "exec")

exec(code)

# Copyright (c) 2026 agvn.io - MIT License (see LICENSE).
#
# AGVN Player's start file for Ren'Py games. librenpython.so runs ANDROID_PRIVATE/main.py; Ren'Py's own Android
# build puts its renpy.py there, with the game inside the APK. Here the game stays in its folder on the phone:
#   - AGVN_RENPY_BASEDIR: that folder; it becomes the base directory, as "renpy.py <folder>" does on a PC.
#   - AGVN_RENPY_NAME: the game's launcher name (MyGame.py next to MyGame.exe), which Ren'Py uses to find game/.
#   - AGVN_RENPY_QUIT_FILE: the app creates this file for "Thoát game"; the game then quits as when its window is
#     closed on a PC (its own question, persistent data saved).
#   - AGVN_RENPY_STRETCH: "1" for "Toàn màn hình cho mọi game": the game is drawn over the whole screen, without the
#     bars Ren'Py keeps at the sides, through config.adjust_view_size (unless the game sets that itself).
#   - ANDROID_PUBLIC: beside Ren'Py's log.txt, faulthandler.txt gets the Python lines a crash in native code came from.
# Live2D is checked as Ren'Py meant (Live2DCheck). Then Ren'Py's renpy.py runs unchanged, in this module, so its path
# functions are the ones Ren'Py calls.

import importlib.abc
import importlib.machinery
import os
import sys
import threading
import time

LIVE2D_MISSING = ("Game cần Live2D, mà bản Chạy nhẹ của AGVN Player không có thư viện Live2D Cubism Core "
                  "(libLive2DCubismCore.so). Bản Windows của game có thư viện này: chọn \"Chạy bằng Windows\".")

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


def stretch_to_screen():
    def whole(width, height):
        return width, height

    # Set as soon as Ren'Py's config has the setting, before the window is made. Ren'Py sets its config back to its
    # defaults when it reloads a game, so this looks again now and then.
    while True:
        config = sys.modules.get("renpy.config")

        if config is not None and hasattr(config, "adjust_view_size") and config.adjust_view_size is None:
            config.adjust_view_size = whole

        ready = config is not None and getattr(config, "adjust_view_size", None) is not None
        time.sleep(1.0 if ready else 0.05)


def check_live2d(live2d):
    """
    Ren'Py 8.5.3 goes on as if Live2D were there when its library cannot be loaded: onetime_init() adds a note to the
    error and drops it, and live2dmodel.load() says True from its second call. has_live2d() then said True, and the
    first Live2D model called a function never loaded: the game closed at once, with no traceback (SIGSEGV at pc 0).
    Here the first answer of load() is kept and a failed one raised again, as Ren'Py meant: has_live2d() says False,
    and a game that makes a Live2D model anyway stops on Ren'Py's error screen, which "Tự sửa lỗi" then reads.
    """
    model = getattr(live2d, "live2dmodel", None)
    if model is None:
        return  # not built in: Ren'Py raises itself

    if not hasattr(model.load, "agvn_loaded"):  # the extension stays loaded through a reload, and so does this
        load = model.load

        def checked_load(dll):
            if not checked_load.agvn_loaded:
                try:
                    checked_load.agvn_loaded.append(bool(load(dll)))
                except Exception:
                    checked_load.agvn_loaded.append(False)
                    raise
            return checked_load.agvn_loaded[0]

        checked_load.agvn_loaded = []
        model.load = checked_load

    loaded = model.load.agvn_loaded
    onetime_init = live2d.onetime_init

    def checked_init():
        onetime_init()
        if loaded and not loaded[0]:
            raise Exception(LIVE2D_MISSING)

    live2d.onetime_init = checked_init


class Live2DCheck(importlib.abc.MetaPathFinder):
    """Runs check_live2d on renpy.gl2.live2d as it is imported, also when Ren'Py imports it again (a reload)."""

    def find_spec(self, name, path, target=None):
        if name != "renpy.gl2.live2d":
            return None
        spec = importlib.machinery.PathFinder.find_spec(name, path)
        if spec is None or spec.loader is None or not hasattr(spec.loader, "exec_module"):
            return spec
        run = spec.loader.exec_module

        def exec_module(module):
            run(module)
            check_live2d(module)

        spec.loader.exec_module = exec_module
        return spec


def enable_faulthandler():
    public = os.environ.get("ANDROID_PUBLIC")
    if not public:
        return None
    try:
        import faulthandler
        log = open(os.path.join(public, "faulthandler.txt"), "w")  # empty unless the game crashed
        faulthandler.enable(log)
        return log  # kept open: faulthandler writes to it as the process dies
    except Exception as e:
        print("AGVN: faulthandler:", e)
        return None


fault_log = enable_faulthandler()
sys.meta_path.insert(0, Live2DCheck())
quit_file = os.environ.get("AGVN_RENPY_QUIT_FILE")

if os.environ.get("AGVN_RENPY_STRETCH") == "1":
    threading.Thread(target=stretch_to_screen, name="agvn-stretch", daemon=True).start()

if quit_file:
    threading.Thread(target=watch_quit, args=(quit_file,), name="agvn-quit", daemon=True).start()

renpy_py = os.path.join(os.path.dirname(os.path.abspath(__file__)), "renpy.py")

with open(renpy_py, "rb") as f:
    code = compile(f.read(), renpy_py, "exec")

exec(code)

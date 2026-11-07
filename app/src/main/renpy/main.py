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
#   - AGVN_RENPY_PY2: "1" for a Ren'Py 6 or 7 game (Python 2) on trial: agvn_py2.py gives it Python 2's builtins,
#     division and dict methods.
# Live2D is checked as Ren'Py meant, and each start time goes into log.txt once (ModuleHooks). Then Ren'Py's renpy.py
# runs unchanged, in this module, so its path functions are the ones Ren'Py calls.

import importlib.abc
import importlib.machinery
import os
import sys
import threading
import time

# The names check_live2d sets. Ren'Py backs up its modules with pickle once it has imported them (renpy.Backup), and a
# function made inside another cannot be pickled: in 0.1.56 the backup failed ("Can't get local object
# 'check_live2d.<locals>.checked_load'") and every Ren'Py game stopped as it started. They go in Ren'Py's own list of
# names its backup leaves out, so a reload (which restores the backup) keeps them too.
LIVE2D_NAMES = ("renpy.gl2.live2dmodel.load", "renpy.gl2.live2d.onetime_init")
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


def whole_screen(width, height):
    return width, height


def stretch_to_screen():
    # Set as soon as Ren'Py's config has the setting, before the window is made, and left out of Ren'Py's backup of its
    # modules, which pickles every value (see LIVE2D_NAMES): from 0.1.39 to 0.1.56 a function of main.py was backed up,
    # and with "Toàn màn hình cho mọi game" on no Ren'Py game started. This looks again now and then, should a game's
    # reload set its config back.
    while True:
        config = sys.modules.get("renpy.config")
        blacklist = getattr(sys.modules.get("renpy"), "name_blacklist", None)

        if config is not None and hasattr(config, "adjust_view_size") and config.adjust_view_size is None \
                and isinstance(blacklist, set):
            blacklist.add("renpy.config.adjust_view_size")
            config.adjust_view_size = whole_screen

        ready = config is not None and (getattr(config, "adjust_view_size", None) is not None
                                        or not isinstance(blacklist, set))  # a Ren'Py whose backup this does not know
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
    blacklist = getattr(sys.modules.get("renpy"), "name_blacklist", None)
    if model is None or not isinstance(blacklist, set):
        return  # not built in (Ren'Py raises itself), or a backup this does not know: Ren'Py left as it is

    blacklist.update(LIVE2D_NAMES)

    if not hasattr(model.load, "agvn_loaded"):  # once: the extension module is never imported twice
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


def log_once(main):
    """
    Ren'Py on Android writes each start time ("Early init took 51 ms" ... "Interface start took 4301 ms") into log.txt,
    then prints it for logcat. Its print goes to log.txt too, as all of stdout does, so every one of these lines was
    there twice (03/11/2026, every Ren'Py game). Here it goes to log.txt once and to logcat as before: Ren'Py's own
    log_clock of 8.5.3, writing to the stdout it had before it sent stdout to log.txt instead of printing.
    """
    renpy = sys.modules.get("renpy")
    blacklist = getattr(renpy, "name_blacklist", None)
    if not isinstance(blacklist, set) or not hasattr(main, "log_clock") or not hasattr(main, "last_clock"):
        return  # not the Ren'Py this knows: left as it is

    blacklist.add("renpy.main.log_clock")  # a function of this file cannot be pickled by Ren'Py's backup

    def log_clock(s):
        now = time.time()
        s = "{} took {:.0f} ms".format(s, 1000 * (now - main.last_clock))
        renpy.display.log.write(s)
        if renpy.android and not renpy.config.log_to_stdout:
            try:
                renpy.log.real_stdout.write(s + "\n")
            except Exception:
                pass
        renpy.display.presplash.pump_window()
        main.last_clock = now

    main.log_clock = log_clock


class ModuleHooks(importlib.abc.MetaPathFinder):
    """
    Runs check_live2d on renpy.gl2.live2d and log_once on renpy.main as they are imported. Whatever goes wrong here,
    Ren'Py goes on as it is.
    """

    HOOKS = {"renpy.gl2.live2d": check_live2d, "renpy.main": log_once}

    def find_spec(self, name, path, target=None):
        hook = self.HOOKS.get(name)
        if hook is None:
            return None
        spec = importlib.machinery.PathFinder.find_spec(name, path)
        if spec is None or spec.loader is None or not hasattr(spec.loader, "exec_module"):
            return spec
        run = spec.loader.exec_module

        def exec_module(module):
            run(module)
            try:
                hook(module)
            except Exception as e:
                print("AGVN: " + name + ":", e)

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

if os.environ.get("AGVN_RENPY_PY2") == "1":
    try:
        here = os.path.dirname(os.path.abspath(__file__))
        if here not in sys.path:
            sys.path.insert(0, here)
        import agvn_py2
        agvn_py2.install(ModuleHooks.HOOKS)
    except Exception as e:
        print("AGVN: Python 2 support:", e)

sys.meta_path.insert(0, ModuleHooks())
quit_file = os.environ.get("AGVN_RENPY_QUIT_FILE")

if os.environ.get("AGVN_RENPY_STRETCH") == "1":
    threading.Thread(target=stretch_to_screen, name="agvn-stretch", daemon=True).start()

if quit_file:
    threading.Thread(target=watch_quit, args=(quit_file,), name="agvn-quit", daemon=True).start()

renpy_py = os.path.join(os.path.dirname(os.path.abspath(__file__)), "renpy.py")

with open(renpy_py, "rb") as f:
    code = compile(f.read(), renpy_py, "exec")

exec(code)

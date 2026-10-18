# Copyright (c) 2026 agvn.io - MIT License (see LICENSE).
#
# PC check of the app's Ren'Py start file (app/src/main/renpy/main.py): python3 scripts/agvn/renpy/test-main.py
# It runs main.py as the phone does, with a stand-in renpy package whose Live2D code behaves as Ren'Py 8.5.3's:
#   renpy/gl2/live2d.py   onetime_init() drops the error of a failed load (live2d.py, lines 82-89), init() calls it,
#                         has_live2d() says True unless init() raises
#   renpy/gl2/live2dmodel load() sets did_load first, so its second call says True (live2dcsm.pxi, load)
# and a stand-in renpy.py that plays the game: has_live2d(), a Live2D model, then a reload. No phone, no Ren'Py.

import os
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
MAIN = os.path.join(HERE, "..", "..", "..", "app", "src", "main", "renpy", "main.py")

LIVE2DMODEL = '''
import os
did_load = False

def load(dll):
    global did_load
    if did_load:
        return True
    did_load = True
    return os.environ.get("FAKE_CORE") == "1"
'''

LIVE2D = '''
import renpy.gl2.live2dmodel as live2dmodel
did_onetime_init = False
did_init = False
_has_live2d = None

def onetime_init():
    global did_onetime_init
    if did_onetime_init:
        return
    dll = b"libLive2DCubismCore.so"
    try:
        if not live2dmodel.load(dll):
            raise Exception("Could not load Live2D. {} was not found.".format(dll))
    except Exception as e:
        e.add_note("Live2D Cubism 5.3 or later is required.")
    did_onetime_init = True

def init():
    global did_init
    if did_init:
        return
    onetime_init()
    did_init = True

def has_live2d():
    global _has_live2d
    if _has_live2d is None:
        try:
            init()
            _has_live2d = True
        except Exception:
            _has_live2d = False
    return _has_live2d

class Live2DCommon(object):
    def __init__(self, filename):
        init()
        self.model = "model of " + filename  # Ren'Py calls into the library here: SIGSEGV when it is not loaded
'''

RENPY_PY = '''
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import renpy.gl2.live2d as live2d
print("has_live2d", live2d.has_live2d())
try:
    live2d.Live2DCommon("images/nv")
    print("model made")
except Exception as e:
    print("model refused:", e)
del sys.modules["renpy.gl2.live2d"]  # Ren'Py imports its modules again when it reloads a game
import renpy.gl2.live2d as again
print("again", again is not live2d)
try:
    again.Live2DCommon("images/nv")
    print("model made")
except Exception as e:
    print("model refused:", e)
'''


def run(core):
    with tempfile.TemporaryDirectory() as tmp:
        engine = os.path.join(tmp, "engine")
        public = os.path.join(tmp, "public")
        os.makedirs(os.path.join(engine, "renpy", "gl2"))
        os.makedirs(public)
        files = {"renpy/__init__.py": "", "renpy/gl2/__init__.py": "", "renpy/gl2/live2dmodel.py": LIVE2DMODEL,
                 "renpy/gl2/live2d.py": LIVE2D, "renpy.py": RENPY_PY}
        for name, text in files.items():
            with open(os.path.join(engine, name), "w", encoding="utf-8") as f:
                f.write(text)
        with open(MAIN, encoding="utf-8") as src, open(os.path.join(engine, "main.py"), "w", encoding="utf-8") as dst:
            dst.write(src.read())
        env = {"PATH": os.environ.get("PATH", ""), "ANDROID_PUBLIC": public, "PYTHONIOENCODING": "utf-8"}
        if core:
            env["FAKE_CORE"] = "1"
        out = subprocess.run([sys.executable, "-I", os.path.join(engine, "main.py")], env=env, capture_output=True,
                             text=True, encoding="utf-8", timeout=60)
        fault = os.path.join(public, "faulthandler.txt")
        size = os.path.getsize(fault) if os.path.isfile(fault) else -1
        return out.returncode, out.stdout, out.stderr, size


failures = 0


def check(name, ok):
    global failures
    print(("ok   " if ok else "FAIL ") + name)
    if not ok:
        failures += 1


code, out, err, fault = run(core=False)
lines = out.splitlines()
check("runs without the library", code == 0 and not err.strip())
check("has_live2d() says False", "has_live2d False" in lines)
check("a Live2D model is refused, with the reason", lines.count("model refused: Game cần Live2D, mà bản Chạy nhẹ của "
      "AGVN Player không có thư viện Live2D Cubism Core (libLive2DCubismCore.so). Bản Windows của game có thư viện "
      "này: chọn \"Chạy bằng Windows\".") == 2)
check("also after a reload", "again True" in lines and "model made" not in lines)
check("faulthandler.txt is there, empty", fault == 0)

code, out, err, fault = run(core=True)
lines = out.splitlines()
check("with the library: has_live2d() says True", code == 0 and "has_live2d True" in lines)
check("with the library: models are made", lines.count("model made") == 2)

if failures:
    print(out, err)
    sys.exit(1)
print("all checks passed")

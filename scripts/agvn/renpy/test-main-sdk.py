# Copyright (c) 2026 agvn.io - MIT License (see LICENSE).
#
# Runs the app's Ren'Py start file (app/src/main/renpy/main.py) on Ren'Py's own Linux build, with two small games:
# one without Live2D (also with "Toàn màn hình cho mọi game" on), one that makes a Live2D model at init as the
# reported games do. The phone's Ren'Py is the same version, built for Android. Ren'Py runs its "lint" command, which
# runs every game's init code without a window.
#   tar -xjf app/agvn-downloads/renpy-8.5.3-sdk.tar.bz2 -C <dir> renpy-8.5.3-sdk/renpy.py renpy-8.5.3-sdk/renpy \
#       renpy-8.5.3-sdk/lib/py3-linux-x86_64 renpy-8.5.3-sdk/lib/python3.12
#   python3 scripts/agvn/renpy/test-main-sdk.py <dir>/renpy-8.5.3-sdk
# The SDK has no Live2D Cubism Core, as the app has none.

import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
MAIN = os.path.join(HERE, "..", "..", "..", "app", "src", "main", "renpy", "main.py")
MARK = '''init python:
    import os
    with open(os.path.join(config.basedir, "agvn-marker.txt"), "w") as f:
        f.write("init ran, has_live2d=%r, view=%s\\n" % (renpy.has_live2d(), getattr(config.adjust_view_size, "__name__", None)))
'''
PLAIN = MARK + '\nlabel start:\n    "Xin chào."\n    return\n'
# name: (script, "Toàn màn hình cho mọi game")
GAMES = {
    "plain": (PLAIN, False),
    "stretched": (PLAIN, True),
    "live2d": (MARK + '\nimage nv = Live2D("live2d/nv", base=.6)\n\nlabel start:\n    show nv\n    "Xong."\n    return\n', False),
}

sdk = os.path.abspath(sys.argv[1]) if len(sys.argv) > 1 else ""
python = os.path.join(sdk, "lib", "py3-linux-x86_64", "python")
if not os.path.isfile(os.path.join(sdk, "renpy.py")) or not os.path.isfile(python):
    sys.exit("usage: test-main-sdk.py <renpy-8.5.3-sdk folder> (see the top of this file)")

failures = 0


def check(name, ok):
    global failures
    print(("ok   " if ok else "FAIL ") + name)
    failures += 0 if ok else 1


main = os.path.join(sdk, "agvn-test-main.py")  # beside renpy.py, which main.py runs from its own folder
shutil.copyfile(MAIN, main)
try:
    with tempfile.TemporaryDirectory() as tmp:
        results = {}
        for name, (script, stretch) in GAMES.items():
            game = os.path.join(tmp, name)
            os.makedirs(os.path.join(game, "game"))
            with open(os.path.join(game, "game", "script.rpy"), "w", encoding="utf-8") as f:
                f.write(script)
            env = {"PATH": "/usr/bin:/bin", "HOME": tmp, "ANDROID_PUBLIC": tmp, "SDL_AUDIODRIVER": "dummy",
                   "SDL_VIDEODRIVER": "dummy", "AGVN_RENPY_STRETCH": "1" if stretch else "0"}
            out = subprocess.run([python, main, game, "lint"], cwd=sdk, env=env, capture_output=True, text=True,
                                 encoding="utf-8", errors="replace", timeout=300)
            marker = os.path.join(game, "agvn-marker.txt")
            ran = open(marker, encoding="utf-8").read().strip() if os.path.isfile(marker) else ""
            results[name] = (out.returncode, out.stdout + out.stderr, ran)
        code, text, ran = results["plain"]
        check("a game without Live2D: its init code runs, lint passes", code == 0 and ran == "init ran, has_live2d=False, view=None")
        code, text, ran = results["stretched"]
        check("with \"Toàn màn hình cho mọi game\" on, the same, and the game is stretched",
              code == 0 and ran == "init ran, has_live2d=False, view=whole_screen")
        check("nothing Ren'Py's backup cannot pickle", all("Cannot pickle" not in r[1] for r in results.values()))
        code, text, ran = results["live2d"]
        check("a Live2D game: has_live2d() says False", ran == "init ran, has_live2d=False, view=None")
        check("its Live2D model is refused with the app's reason", "Exception: Game cần Live2D" in text)
        check("faulthandler.txt beside the logs, empty", os.path.getsize(os.path.join(tmp, "faulthandler.txt")) == 0)
        if failures:
            for name, (code, text, ran) in results.items():
                print("== %s (exit %d, marker %r)\n%s" % (name, code, ran, text[-3000:]))
finally:
    os.unlink(main)

if failures:
    sys.exit(1)
print("all checks passed")

"""Tests for app/src/main/renpy/main.py, AGVN's start file for Ren'Py games, with a stand-in renpy.py.

Run: python -m unittest discover -s tools/agvn/tests
"""
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
MAIN = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(HERE))), "app", "src", "main", "renpy", "main.py")

# Stands in for Ren'Py's renpy.py: reports what Ren'Py would see. With a quit file, it plays Ren'Py's event queue.
FAKE_RENPY = r'''
import json, os, sys, time, types

def path_to_renpy_base():
    return os.path.dirname(os.path.abspath(__file__))

report = {"argv": sys.argv, "name": __name__, "base": path_to_renpy_base(),
          "main_has_paths": hasattr(sys.modules["__main__"], "path_to_renpy_base")}

quit_file = os.environ.get("AGVN_RENPY_QUIT_FILE")
if quit_file:
    posted = []
    event = types.SimpleNamespace(Event=lambda t: ("event", t), post=posted.append)
    time.sleep(0.6)  # Ren'Py not started yet: the request waits
    sys.modules["renpy.pygame"] = types.SimpleNamespace(QUIT=256, event=event)
    open(quit_file, "w").close()
    for _ in range(100):
        if posted:
            break
        time.sleep(0.05)
    time.sleep(0.1)
    report["posted"] = posted
    report["quit_file_left"] = os.path.exists(quit_file)

stretch = os.environ.get("TEST_STRETCH")
if stretch:
    # Ren'Py's config as it imports, then a game that sets its own setting, then a reload back to the defaults
    config = types.ModuleType("renpy.config")
    sys.modules["renpy.config"] = config
    renpy = sys.modules.setdefault("renpy", types.ModuleType("renpy"))
    renpy.name_blacklist = set()  # what Ren'Py leaves out of its backup of its modules
    time.sleep(0.2)
    config.adjust_view_size = None
    for _ in range(40):
        if config.adjust_view_size is not None:
            break
        time.sleep(0.05)
    report["view"] = config.adjust_view_size(2400, 1080) if config.adjust_view_size else None
    report["blacklist"] = sorted(renpy.name_blacklist)
    if stretch == "game":
        own = lambda w, h: (w // 2, h // 2)
        config.adjust_view_size = own
        time.sleep(1.3)
        report["game_kept"] = config.adjust_view_size is own

if os.environ.get("TEST_LOG_CLOCK"):
    # Ren'Py's package as far as log_clock needs it, its log_clock of 8.5.3, and its stdout sent to log.txt
    pkg = os.path.join(path_to_renpy_base(), "renpy")
    os.makedirs(pkg, exist_ok=True)
    with open(os.path.join(pkg, "__init__.py"), "w") as f:
        f.write(os.environ["TEST_RENPY_INIT"])
    with open(os.path.join(pkg, "main.py"), "w") as f:
        f.write(os.environ["TEST_RENPY_MAIN"])
    import renpy

    class ToLog:  # Ren'Py's StdioRedirector: whole lines go to log.txt
        buffer = ""

        def write(self, s):
            lines = (self.buffer + s).split("\n")
            renpy.log_txt.extend(lines[:-1])
            self.buffer = lines[-1]

    saved, sys.stdout = sys.stdout, ToLog()
    import renpy.main
    renpy.main.log_clock("Early init")
    sys.stdout = saved
    report["log_txt"] = renpy.log_txt
    report["logcat"] = renpy.log.real_stdout.lines
    report["blacklist"] = sorted(renpy.name_blacklist)

with open(os.environ["TEST_REPORT"], "w") as f:
    json.dump(report, f)
'''

RENPY_INIT = r'''
import types
name_blacklist = set()
android = True
log_txt = []
config = types.SimpleNamespace(log_to_stdout=False)
display = types.SimpleNamespace(log=types.SimpleNamespace(write=log_txt.append),
                                presplash=types.SimpleNamespace(pump_window=lambda: None))


class Out:
    def __init__(self):
        self.lines = []

    def write(self, s):
        self.lines.append(s)


log = types.SimpleNamespace(real_stdout=Out())
'''

RENPY_MAIN = r'''
import time
import renpy
last_clock = time.time()


def log_clock(s):
    global last_clock
    now = time.time()
    s = "{} took {:.0f} ms".format(s, 1000 * (now - last_clock))

    renpy.display.log.write(s)
    if renpy.android and not renpy.config.log_to_stdout:
        print(s)

    renpy.display.presplash.pump_window()

    last_clock = now
'''


class RenpyMainTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.private = os.path.join(self.tmp, "renpy8")
        os.makedirs(self.private)
        shutil.copy(MAIN, self.private)
        with open(os.path.join(self.private, "renpy.py"), "w") as f:
            f.write(FAKE_RENPY)
        self.game = os.path.join(self.tmp, "My Game")
        os.makedirs(os.path.join(self.game, "game"))
        self.report = os.path.join(self.tmp, "report.json")

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def run_main(self, **env):
        full = dict(os.environ, TEST_REPORT=self.report, **env)
        subprocess.run([sys.executable, os.path.join(self.private, "main.py")], env=full, check=True, timeout=30,
                       cwd=self.private, stdout=subprocess.DEVNULL)
        with open(self.report) as f:
            return json.load(f)

    def test_points_renpy_at_the_game_folder(self):
        r = self.run_main(AGVN_RENPY_BASEDIR=self.game, AGVN_RENPY_NAME="MyGame")
        self.assertEqual([os.path.join(self.game, "MyGame.py"), self.game], r["argv"])
        self.assertEqual("__main__", r["name"])
        self.assertTrue(r["main_has_paths"], "Ren'Py calls renpy.py's functions on the __main__ module")
        self.assertEqual(os.path.realpath(self.private), os.path.realpath(r["base"]))

    def test_without_a_name_the_folder_still_counts(self):
        r = self.run_main(AGVN_RENPY_BASEDIR=self.game)
        self.assertEqual([os.path.join(self.game, "main.py"), self.game], r["argv"])

    def test_quit_request_becomes_a_quit_event_once_renpy_runs(self):
        quit_file = os.path.join(self.tmp, "agvn-renpy-quit")
        r = self.run_main(AGVN_RENPY_BASEDIR=self.game, AGVN_RENPY_QUIT_FILE=quit_file)
        self.assertEqual([["event", 256]], r["posted"])
        self.assertFalse(r["quit_file_left"])

    def test_stretch_fills_the_screen_unless_the_game_sizes_it(self):
        # "Toàn màn hình cho mọi game": the whole screen, as the app's AGVN_RENPY_STRETCH asks
        r = self.run_main(AGVN_RENPY_BASEDIR=self.game, AGVN_RENPY_STRETCH="1", TEST_STRETCH="1")
        self.assertEqual([2400, 1080], r["view"])
        r = self.run_main(AGVN_RENPY_BASEDIR=self.game, AGVN_RENPY_STRETCH="1", TEST_STRETCH="game")
        self.assertTrue(r["game_kept"], "a game's own adjust_view_size stays")
        r = self.run_main(AGVN_RENPY_BASEDIR=self.game, AGVN_RENPY_STRETCH="0", TEST_STRETCH="1")
        self.assertIsNone(r["view"], "off: Ren'Py keeps its bars")
        r = self.run_main(AGVN_RENPY_BASEDIR=self.game, AGVN_RENPY_STRETCH="1", TEST_STRETCH="1")
        self.assertEqual(["renpy.config.adjust_view_size"], r["blacklist"], "never in Ren'Py's backup")

    def test_each_start_time_goes_into_log_txt_once(self):
        # Ren'Py printed it for logcat, and its stdout goes to log.txt too: every such line was there twice (03/11/2026)
        r = self.run_main(AGVN_RENPY_BASEDIR=self.game, TEST_LOG_CLOCK="1", TEST_RENPY_INIT=RENPY_INIT,
                          TEST_RENPY_MAIN=RENPY_MAIN)
        self.assertEqual(1, len(r["log_txt"]), r["log_txt"])
        self.assertRegex(r["log_txt"][0], r"^Early init took \d+ ms$")
        self.assertEqual([r["log_txt"][0] + "\n"], r["logcat"], "still in logcat")
        self.assertIn("renpy.main.log_clock", r["blacklist"], "never in Ren'Py's backup")


if __name__ == "__main__":
    unittest.main()

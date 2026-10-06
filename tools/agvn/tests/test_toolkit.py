"""Tests for the AGVN PC toolkit. Run: python -m unittest discover -s tools/agvn/tests"""
import importlib.util
import io
import json
import os
import shutil
import subprocess
import struct
import sys
import tempfile
import unittest
import zlib
from contextlib import redirect_stdout, redirect_stderr

HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.dirname(HERE)
REPO = os.path.dirname(os.path.dirname(TOOLS))
sys.path.insert(0, TOOLS)

from agvn_profile_lib import CONTROLS, ProfileError, controls_for, detect_engine, resolve_exe, validate  # noqa: E402
from texture_headers import read_texture  # noqa: E402


def load_script(name):
    spec = importlib.util.spec_from_file_location(name.replace("-", "_"), os.path.join(TOOLS, name))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def touch(root, rel, data=b""):
    path = os.path.join(root, *rel.split("/"))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(data)
    return path


def dds(width, height, fourcc, mips):
    head = bytearray(128)
    head[:4] = b"DDS "
    struct.pack_into("<IIIII", head, 4, 124, 0x1007, height, width, 0)
    struct.pack_into("<I", head, 28, mips)
    struct.pack_into("<II4s", head, 76, 32, 0x4, fourcc)
    return bytes(head)


def png(width, height):
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + struct.pack(">I", 13) + b"IHDR" + ihdr + struct.pack(">I", zlib.crc32(b"IHDR" + ihdr))


def jpg(width, height):
    app0 = b"\xff\xe0" + struct.pack(">H", 16) + b"JFIF\x00" + b"\x00" * 9
    sof = b"\xff\xc0" + struct.pack(">HBHHB", 11, 8, height, width, 1) + b"\x01\x11\x00"
    return b"\xff\xd8" + app0 + sof + b"\xff\xd9"


class ProfileRulesTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.ue = os.path.join(self.tmp.name, "MyGame")
        touch(self.ue, "MyGame.exe")
        touch(self.ue, "MyGame/Binaries/Win64/MyGame-Win64-Shipping.exe")

    def tearDown(self):
        self.tmp.cleanup()

    def test_unreal_uses_shipping(self):
        self.assertEqual("UNREAL", detect_engine(self.ue))
        p = {"schemaVersion": 1, "name": "My Game", "exe": "MyGame.exe"}
        self.assertEqual("MyGame/Binaries/Win64/MyGame-Win64-Shipping.exe", validate(p, self.ue))

    def test_unity_and_generic(self):
        u = os.path.join(self.tmp.name, "unity")
        for rel in ("UnityCrashHandler64.exe", "Launcher.exe", "Cool Game.exe", "UnityPlayer.dll", "Cool Game_Data/globalgamemanagers"):
            touch(u, rel)
        self.assertEqual("UNITY", detect_engine(u))
        self.assertEqual("Cool Game.exe", resolve_exe(u, "UNITY"))
        g = os.path.join(self.tmp.name, "gm")
        for rel in ("unins000.exe", "GameLauncher.exe", "game.exe", "data.win"):
            touch(g, rel)
        self.assertEqual("game.exe", resolve_exe(g, detect_engine(g)))
        helpers = os.path.join(self.tmp.name, "helpers")
        for rel in ("AutoUpdate.exe", "CrashReporter.exe", "Game.exe", "oalinst.exe", "x.dll"):
            touch(helpers, rel)
        self.assertEqual("Game.exe", resolve_exe(helpers, detect_engine(helpers)))

    def test_rejections(self):
        touch(self.tmp.name, "evil.exe")
        bad = [{"exe": "../evil.exe"}, {"exe": "C:/evil.exe"}, {"args": ["a;b"]}, {"env": {"A": "$(x)"}},
               {"resolution": "320x240"}, {"fpsLimit": 5}, {"texturePool": 9000}, {"ueEngineIni": {"[x]": {}}},
               {"name": "a/b"}, {"schemaVersion": 2}, {"locale": "japanese"}, {"locale": "ja_JP;x"}, {"runner": "exe"}]
        for extra in bad:
            p = {"schemaVersion": 1, "name": "g"}
            p.update(extra)
            with self.assertRaises(ProfileError, msg=str(extra)):
                validate(p, self.ue)

    def test_runner_values(self):
        game = os.path.join(self.tmp.name, "RenPyGame")
        for rel in ("MyGame.exe", "MyGame.py", "game/script.rpyc", "renpy/common/00start.rpyc"):
            touch(game, rel)
        for runner in ("html", "renpy", "rgss", "godot", "wine"):
            self.assertEqual("MyGame.exe", validate({"schemaVersion": 1, "name": "g", "runner": runner}, game))

    def test_rejects_wine_in_path(self):
        g = os.path.join(self.tmp.name, "Red wine 2")
        touch(g, "game.exe")
        with self.assertRaises(ProfileError):
            validate({"schemaVersion": 1, "name": "g"}, g)

    def test_shared_example_profile_is_valid(self):
        with open(os.path.join(REPO, "docs", "agvn", "agvn-profile.example.json"), encoding="utf-8") as f:
            example = json.load(f)
        game = os.path.join(self.tmp.name, "AVDirector")
        touch(game, example["exe"])
        self.assertEqual(example["exe"], validate(example, game))


class EngineDetectionTest(unittest.TestCase):
    """Same fixtures as GameExeResolverTest.java: both sides must agree on every engine."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()

    def tearDown(self):
        self.tmp.cleanup()

    def game(self, name, *paths):
        root = os.path.join(self.tmp.name, name)
        os.makedirs(root)
        for rel in paths:
            if rel.endswith("/"):
                os.makedirs(os.path.join(root, *rel.strip("/").split("/")))
            else:
                touch(root, rel)
        return root

    def check(self, expected, name, *paths):
        root = self.game(name, *paths)
        self.assertEqual(expected, detect_engine(root), name)
        return root

    def test_siglus_is_not_godot(self):
        siglus = self.check("SIGLUS", "siglus", "SiglusEngine.exe", "Scene.pck", "Gameexe.dat", "g00/")
        self.check("SIGLUS", "siglus2", "Game.exe", "Gameexe.dat")
        self.check("GODOT", "godot", "Game.exe", "Game.pck")
        self.assertEqual("SiglusEngine.exe", resolve_exe(siglus, "SIGLUS"))

    def test_godot_in_one_exe(self):
        # "Embed PCK": the exe, the pack ("GDPC", format, major, minor, patch...), the pack's size, "GDPC"
        pck = struct.pack("<4s4I", b"GDPC", 3, 4, 5, 1) + bytes(76)
        root = self.game("embedded", "readme.txt")
        with open(os.path.join(root, "Game.exe"), "wb") as f:
            f.write(b"MZ... the engine ..." + pck + struct.pack("<Q", len(pck)) + b"GDPC")
        self.assertEqual("GODOT", detect_engine(root))
        with open(os.path.join(root, "Game.exe"), "wb") as f:  # a size bigger than the file: not a pack
            f.write(b"MZ... the engine ..." + pck + struct.pack("<Q", 1 << 40) + b"GDPC")
        self.assertEqual("UNKNOWN", detect_engine(root))

    def test_rpgmaker_families(self):
        mv = self.check("RPGMAKER_MV", "mv", "Game.exe", "notification_helper.exe", "nw.dll", "www/js/rpg_core.js")
        self.assertEqual("Game.exe", resolve_exe(mv, "RPGMAKER_MV"))
        self.check("RPGMAKER_MV", "mz", "Game.exe", "js/rmmz_core.js")
        self.check("RPGMAKER", "vxace", "Game.exe", "Game.ini", "Game.rgss3a", "System/RGSS301.dll")
        self.check("RPGMAKER", "xp", "Game.exe", "Game.ini", "RGSS104E.dll")
        self.check("RPGMAKER", "vx", "Game.exe", "Game.ini", "Data/Map001.rvdata")
        self.check("RPGMAKER", "rm2k3", "RPG_RT.exe", "RPG_RT.ldb", "RPG_RT.ini")
        self.check("UNKNOWN", "iniOnly", "Game.exe", "Game.ini")

    def test_wolf_skips_config_exe(self):
        wolf = self.check("WOLFRPG", "wolf", "Config.exe", "Game.exe", "Data.wolf", "GuruguruSMF4.dll")
        self.assertEqual("Game.exe", resolve_exe(wolf, "WOLFRPG"))
        self.check("WOLFRPG", "wolf2", "Game.exe", "Data/BasicData.wolf")
        self.check("WOLFRPG", "wolf3", "Game.exe", "GuruguruSMF4.dll", "Data/BasicData/")

    def test_visual_novel_engines(self):
        self.check("KIRIKIRI", "krkr", "game.exe", "data.xp3", "plugin/wuvorbis.dll")
        self.check("NSCRIPTER", "ons", "nscr.exe", "arc.nsa", "0.txt")
        self.check("NSCRIPTER", "ons2", "nscr.exe", "nscript.dat")
        self.check("TYRANO", "tyrano", "Game.exe", "index.html", "tyrano/libs.js")
        self.check("TYRANO", "tyrano2", "Game.exe", "Data/System/Config.tjs")
        self.check("RENPY", "renpy", "Game.exe", "renpy/", "lib/")
        self.check("RENPY", "renpy2", "Game.exe", "game/archive.rpa")
        self.check("RENPY", "renpy3", "Game.exe", "game/script.rpyc")
        self.check("UNKNOWN", "plain", "Game.exe", "game/readme.txt")

    def test_controls_kind(self):
        self.assertEqual(("pc", "vn", "rpg", "2d", "action", "mouse"), CONTROLS)
        for engine, kind in (("RENPY", "vn"), ("KIRIKIRI", "vn"), ("TYRANO", "vn"), ("SIGLUS", "vn"), ("NSCRIPTER", "vn"),
                             ("RPGMAKER", "rpg"), ("RPGMAKER_MV", "rpg"), ("WOLFRPG", "rpg"), ("GAMEMAKER", "2d"),
                             ("GODOT", "2d"), ("UNREAL", "pc"), ("UNITY", "pc"), ("UNKNOWN", "pc")):
            self.assertEqual(kind, controls_for(engine), engine)
        self.assertEqual("action", controls_for("RENPY", {"controls": "action"}))
        game = self.game("g", "game.exe", "data.xp3")
        for kind in CONTROLS:
            validate({"schemaVersion": 1, "name": "g", "controls": kind}, game)
        for bad in ("VN", "gamepad", "", 1):
            with self.assertRaises(ProfileError, msg=repr(bad)):
                validate({"schemaVersion": 1, "name": "g", "controls": bad}, game)


class ProfileScriptTest(unittest.TestCase):
    def test_generate_and_check(self):
        mod = load_script("tao-agvn-profile.py")
        with tempfile.TemporaryDirectory() as tmp:
            game = os.path.join(tmp, "Game")
            touch(game, "Game/Binaries/Win64/Game-Win64-Shipping.exe")
            with redirect_stdout(io.StringIO()):
                self.assertEqual(0, mod.main([game, "--name", "Trò chơi", "--ram-mb", "6000"]))
            with open(os.path.join(game, "agvn-profile.json"), encoding="utf-8") as f:
                p = json.load(f)
            self.assertEqual("Game/Binaries/Win64/Game-Win64-Shipping.exe", p["exe"])
            self.assertEqual(768, p["texturePool"])
            self.assertEqual(512, p["weakDevice"]["texturePool"])
            self.assertTrue(p["simulatedTouchscreen"])
            with redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
                self.assertEqual(3, mod.main([game]))           # no overwrite without --force
                self.assertEqual(0, mod.main([game, "--check"]))
                self.assertEqual(1, mod.main([game, "--force", "--fps", "500"]))

    def test_controls_option(self):
        mod = load_script("tao-agvn-profile.py")
        with tempfile.TemporaryDirectory() as tmp:
            game = os.path.join(tmp, "Game")
            touch(game, "Game.exe")
            touch(game, "data.xp3")
            out = io.StringIO()
            with redirect_stdout(out):
                self.assertEqual(0, mod.main([game]))
            self.assertIn("Bộ phím: vn", out.getvalue())
            with open(os.path.join(game, "agvn-profile.json"), encoding="utf-8") as f:
                self.assertNotIn("controls", json.load(f))
            with redirect_stdout(io.StringIO()):
                self.assertEqual(0, mod.main([game, "--force", "--controls", "mouse"]))
            with open(os.path.join(game, "agvn-profile.json"), encoding="utf-8") as f:
                self.assertEqual("mouse", json.load(f)["controls"])

    def test_no_exe(self):
        mod = load_script("tao-agvn-profile.py")
        with tempfile.TemporaryDirectory() as tmp, redirect_stderr(io.StringIO()):
            self.assertEqual(1, mod.main([tmp]))


class TextureTest(unittest.TestCase):
    def test_headers(self):
        with tempfile.TemporaryDirectory() as tmp:
            bc1 = read_texture(touch(tmp, "a.dds", dds(1024, 1024, b"DXT1", 11)))
            self.assertEqual((1024, 1024, "DXT1", 11), bc1[:4])
            self.assertAlmostEqual(1024 * 1024 * 0.5 * 4 / 3, bc1[4], delta=2048)
            bc3 = read_texture(touch(tmp, "b.dds", dds(512, 256, b"DXT5", 1)))
            self.assertEqual(512 * 256, bc3[4])
            p = read_texture(touch(tmp, "c.png", png(2048, 1024)))
            self.assertEqual((2048, 1024), p[:2])
            self.assertEqual(int(2048 * 1024 * 4 * 4 / 3), p[4])
            j = read_texture(touch(tmp, "d.jpg", jpg(640, 480)))
            self.assertEqual((640, 480), j[:2])
            self.assertIsNone(read_texture(touch(tmp, "e.png", b"not a png")))

    def test_report(self):
        mod = load_script("do-bo-nho-anh.py")
        with tempfile.TemporaryDirectory() as tmp:
            touch(tmp, "Game/Content/big.dds", dds(2048, 2048, b"DXT5", 1))
            touch(tmp, "Game/Content/ui.png", png(256, 256))
            touch(tmp, "Game/Content/Paks/pakchunk0.pak", b"x" * 1000)
            touch(tmp, "Game/Content/tex.ubulk", b"x" * 4096)
            out = io.StringIO()
            with redirect_stdout(out):
                self.assertEqual(0, mod.main([tmp, "--csv", os.path.join(tmp, "r.csv")]))
            text = out.getvalue()
            self.assertIn("Số texture: 3", text)
            self.assertIn("gói Unreal", text)
            with open(os.path.join(tmp, "r.csv"), encoding="utf-8") as f:
                lines = f.read().splitlines()
            self.assertTrue(lines[1].startswith("Game/Content/big.dds,2048,2048,DXT5,1,4.00"))


class CatalogTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.game = os.path.join(self.tmp.name, "MyGame")
        touch(self.game, "MyGame/Binaries/Win64/MyGame-Win64-Shipping.exe")
        touch(self.game, "agvn-profile.json", json.dumps({"schemaVersion": 1, "name": "My Game", "fpsLimit": 30}).encode())
        self.catalog = os.path.join(self.tmp.name, "game-profiles.json")
        with open(self.catalog, "w", encoding="utf-8") as f:
            json.dump({"schemaVersion": 1, "entries": []}, f)

    def tearDown(self):
        self.tmp.cleanup()

    def test_adds_then_replaces_entry(self):
        kho = load_script("them-vao-kho-cau-hinh.py")
        entry = kho.entry_for(self.game)
        self.assertEqual("my-game", entry["id"])
        self.assertEqual(["MyGame/Binaries/Win64/MyGame-Win64-Shipping.exe"], entry["match"])
        self.assertEqual(1, kho.add(self.catalog, entry))
        self.assertEqual(1, kho.add(self.catalog, entry))

    def test_generic_exe_needs_a_second_file(self):
        kho = load_script("them-vao-kho-cau-hinh.py")
        self.assertFalse(kho.is_specific(["Game.exe"]))
        self.assertTrue(kho.is_specific(["Game.exe", "www/data/System.json"]))
        self.assertFalse(kho.is_specific(["../x.exe"]))

    def test_bundled_catalog_is_valid(self):
        kho = load_script("them-vao-kho-cau-hinh.py")
        with open(kho.CATALOG, encoding="utf-8") as f:
            catalog = json.load(f)
        self.assertEqual(1, catalog["schemaVersion"])
        for e in catalog["entries"]:
            self.assertTrue(kho.is_specific(e["match"]), e["id"])
            self.assertEqual(1, e["profile"]["schemaVersion"], e["id"])


class HtmlCompatTest(unittest.TestCase):
    @unittest.skipIf(shutil.which("node") is None, "node is not installed")
    def test_compat_script_in_simulated_rpg_maker(self):
        sim = os.path.join(os.path.dirname(os.path.abspath(__file__)), "html_compat_sim.js")
        out = subprocess.run(["node", sim], capture_output=True, text=True)
        self.assertEqual(0, out.returncode, out.stdout + out.stderr)

    @unittest.skipIf(shutil.which("node") is None, "node is not installed")
    def test_rpg_maker_saves_are_files_as_on_a_pc(self):
        sim = os.path.join(os.path.dirname(os.path.abspath(__file__)), "html_saves_sim.js")
        out = subprocess.run(["node", sim], capture_output=True, text=True)
        self.assertEqual(0, out.returncode, out.stdout + out.stderr)

    @unittest.skipIf(shutil.which("node") is None, "node is not installed")
    def test_rpg_maker_mz_moves_while_the_page_shows(self):
        sim = os.path.join(os.path.dirname(os.path.abspath(__file__)), "html_focus_sim.js")
        out = subprocess.run(["node", sim], capture_output=True, text=True)
        self.assertEqual(0, out.returncode, out.stdout + out.stderr)


if __name__ == "__main__":
    unittest.main()

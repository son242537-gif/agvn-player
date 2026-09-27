"""Tests for the AGVN PC toolkit. Run: python -m unittest discover -s tools/agvn/tests"""
import importlib.util
import io
import json
import os
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

from agvn_profile_lib import ProfileError, detect_engine, resolve_exe, validate  # noqa: E402
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

    def test_rejections(self):
        touch(self.tmp.name, "evil.exe")
        bad = [{"exe": "../evil.exe"}, {"exe": "C:/evil.exe"}, {"args": ["a;b"]}, {"env": {"A": "$(x)"}},
               {"resolution": "320x240"}, {"fpsLimit": 5}, {"texturePool": 9000}, {"ueEngineIni": {"[x]": {}}},
               {"name": "a/b"}, {"schemaVersion": 2}]
        for extra in bad:
            p = {"schemaVersion": 1, "name": "g"}
            p.update(extra)
            with self.assertRaises(ProfileError, msg=str(extra)):
                validate(p, self.ue)

    def test_shared_example_profile_is_valid(self):
        with open(os.path.join(REPO, "docs", "agvn", "agvn-profile.example.json"), encoding="utf-8") as f:
            example = json.load(f)
        game = os.path.join(self.tmp.name, "AVDirector")
        touch(game, example["exe"])
        self.assertEqual(example["exe"], validate(example, game))


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


if __name__ == "__main__":
    unittest.main()

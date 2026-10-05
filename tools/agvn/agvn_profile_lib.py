"""Shared rules for the AGVN PC toolkit: engine/exe detection and agvn-profile.json v1 validation.

Mirrors app/src/main/java/com/winlator/cmod/agvn/GameExeResolver.java and AgvnProfileValidator.java.
Keep both sides in sync; tests/test_toolkit.py and the Java unit tests validate the same example profile.
Copyright (c) 2026 agvn.io - MIT License.
"""
import os
import re

SAFE_VALUE = re.compile(r"^[A-Za-z0-9_\-=./:,+]*$")
ENV_KEY = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*$")
RESOLUTION = re.compile(r"^(\d{3,4})x(\d{3,4})$")
INI_SECTION = re.compile(r"^(/Script/[A-Za-z0-9_.]+|SystemSettings|ConsoleVariables|TextureStreaming)$")
INI_KEY = re.compile(r"^[A-Za-z0-9_.]+$")
IGNORED_EXES = {
    "unitycrashhandler64.exe", "unitycrashhandler32.exe", "crashreportclient.exe", "dxsetup.exe",
    "vc_redist.x64.exe", "vc_redist.x86.exe", "vcredist_x64.exe", "vcredist_x86.exe", "dotnetfx.exe",
    "ue4prereqsetup_x64.exe", "ueprereqsetup_x64.exe", "notification_helper.exe"}
# Name parts of exes that only serve a game (launcher, setup, crash reporter, settings...), never a game of their own.
HELPER_PARTS = ("launcher", "setup", "config", "crash", "report", "update", "patch", "install", "redist", "setting",
                "editor", "server", "tool", "helper", "physx", "dotnet", "oalinst", "nwjc", "bssndrpt")
# On-screen controls layouts (AgvnLayouts.java): profile "controls" value -> bundled layout; order = ids 9000..9005.
CONTROLS = ("pc", "vn", "rpg", "2d", "action", "mouse")
_ENGINE_CONTROLS = {"RENPY": "vn", "KIRIKIRI": "vn", "TYRANO": "vn", "SIGLUS": "vn", "NSCRIPTER": "vn",
                    "RPGMAKER": "rpg", "RPGMAKER_MV": "rpg", "WOLFRPG": "rpg", "GAMEMAKER": "2d", "GODOT": "2d"}


class ProfileError(Exception):
    """Invalid profile; message is Vietnamese like in the app."""


def _children(path):
    try:
        return sorted(os.listdir(path))
    except OSError:
        return []


def find_shipping(game_dir):
    roots = [game_dir] + [os.path.join(game_dir, c) for c in _children(game_dir)
                          if os.path.isdir(os.path.join(game_dir, c)) and c.lower() != "engine"]
    for root in roots:
        win64 = os.path.join(root, "Binaries", "Win64")
        for name in _children(win64):
            if name.lower().endswith("-shipping.exe") and os.path.isfile(os.path.join(win64, name)):
                return os.path.relpath(os.path.join(win64, name), game_dir).replace(os.sep, "/")
    return None


def _path(game_dir, *segments):
    """Follows lower-case path segments case-insensitively (like GameExeResolver.path); None when missing."""
    path = game_dir
    for seg in segments:
        path = next((os.path.join(path, n) for n in reversed(_children(path)) if n.lower() == seg), None)
        if path is None:
            return None
    return path


def _any_file(folder, prefix, *suffixes):
    return folder is not None and any(
        n.lower().startswith(prefix) and n.lower().endswith(suffixes) and os.path.isfile(os.path.join(folder, n))
        for n in _children(folder))


def _is_file(path):
    return path is not None and os.path.isfile(path)


def _is_dir(path):
    return path is not None and os.path.isdir(path)


def _is_rpgmaker(game_dir):
    if _is_file(_path(game_dir, "rpg_rt.ini")) or _is_file(_path(game_dir, "rpg_rt.ldb")):
        return True
    return _is_file(_path(game_dir, "game.ini")) and (
        _any_file(game_dir, "rgss", ".dll") or _any_file(_path(game_dir, "system"), "rgss", ".dll")
        or _any_file(game_dir, "", ".rgssad", ".rgss2a", ".rgss3a")
        or _any_file(_path(game_dir, "data"), "", ".rxdata", ".rvdata", ".rvdata2"))


GODOT_MAGIC = b"GDPC"


def _embeds_godot(game_dir, limit=8):
    """A Godot game exported as one exe ("Embed PCK"): its pack ends the exe, then its size and "GDPC" (AgvnGodotFiles)."""
    exes = [n for n in _children(game_dir) if n.lower().endswith(".exe") and os.path.isfile(os.path.join(game_dir, n))]
    for n in exes[:limit]:
        try:
            with open(os.path.join(game_dir, n), "rb") as f:
                length = f.seek(0, os.SEEK_END)
                if length < 32:
                    continue
                f.seek(length - 12)
                end = f.read(12)
                size = int.from_bytes(end[:8], "little")
                if end[8:] != GODOT_MAGIC or not 8 <= size <= length - 12:
                    continue
                f.seek(length - 12 - size)
                head = f.read(20)
                if head[:4] == GODOT_MAGIC and 2 <= int.from_bytes(head[8:12], "little") <= 9:
                    return True
        except OSError:
            continue
    return False


def detect_engine(game_dir):
    """Same rules and order as GameExeResolver.detectEngine (Siglus before the generic *.pck Godot rule)."""
    if find_shipping(game_dir) or os.path.isdir(os.path.join(game_dir, "Engine", "Binaries")):
        return "UNREAL"
    for n in _children(game_dir):
        p = os.path.join(game_dir, n)
        if os.path.isdir(p) and n.lower().endswith("_data") and os.path.exists(os.path.join(p, "globalgamemanagers")):
            return "UNITY"
        if os.path.isfile(p) and n.lower() == "unityplayer.dll":
            return "UNITY"
    sub = lambda *segments: _path(game_dir, *segments)  # noqa: E731
    if _is_file(sub("scene.pck")) or _is_file(sub("gameexe.dat")):
        return "SIGLUS"
    if any(_is_file(sub(*rel)) for rel in (("www", "js", "rpg_core.js"), ("www", "js", "rmmz_core.js"),
                                           ("js", "rpg_core.js"), ("js", "rmmz_core.js"))):
        return "RPGMAKER_MV"
    if _is_rpgmaker(game_dir):
        return "RPGMAKER"
    if _any_file(game_dir, "", ".wolf") or _any_file(sub("data"), "", ".wolf") or _is_file(sub("gurugurusmf4.dll")):
        return "WOLFRPG"
    if _any_file(game_dir, "", ".xp3"):
        return "KIRIKIRI"
    if _any_file(game_dir, "", ".nsa") or _is_file(sub("nscript.dat")):
        return "NSCRIPTER"
    if _is_dir(sub("tyrano")) or _is_file(sub("data", "system", "config.tjs")):
        return "TYRANO"
    if _is_dir(sub("renpy")) or _any_file(sub("game"), "", ".rpa", ".rpyc"):
        return "RENPY"
    if _any_file(game_dir, "", ".pck") or _embeds_godot(game_dir):
        return "GODOT"
    if _is_file(sub("data.win")):
        return "GAMEMAKER"
    return "UNKNOWN"


def resolve_exe(game_dir, engine):
    if engine == "UNREAL":
        shipping = find_shipping(game_dir)
        if shipping:
            return shipping
    exes = [n for n in _children(game_dir) if n.lower().endswith(".exe")
            and os.path.isfile(os.path.join(game_dir, n))
            and n.lower() not in IGNORED_EXES and not n.lower().startswith("unins")]
    if not exes:
        return None
    if engine == "UNITY":
        for n in exes:
            if os.path.isdir(os.path.join(game_dir, n[:-4] + "_Data")):
                return n
    if len(exes) == 1:
        return exes[0]
    for n in exes:
        if not any(part in n.lower() for part in HELPER_PARTS):
            return n
    for n in exes:
        low = n.lower()
        if "launcher" not in low and "setup" not in low and "config" not in low:
            return n
    return exes[0]


def controls_for(engine, profile=None):
    """Layout the app picks: the profile's "controls" when set, else by engine (like AgvnLayouts.kindFor)."""
    chosen = (profile or {}).get("controls")
    return chosen if chosen in CONTROLS else _ENGINE_CONTROLS.get(engine, "pc")


def _check_resolution(res):
    if res is None:
        return
    m = RESOLUTION.match(str(res))
    if not m:
        raise ProfileError("Độ phân giải phải có dạng RỘNGxCAO, ví dụ 1280x720: %s" % res)
    w, h = int(m.group(1)), int(m.group(2))
    if not (640 <= w <= 3840 and 360 <= h <= 2160):
        raise ProfileError("Độ phân giải ngoài khoảng cho phép (640x360 – 3840x2160): %s" % res)


def _check_fps(fps):
    if fps in (None, 0):
        return
    if not isinstance(fps, int) or not 15 <= fps <= 120:
        raise ProfileError("Giới hạn FPS phải từ 15 đến 120 (hoặc 0 = không giới hạn): %s" % fps)


def _check_pool(pool):
    if pool in (None, 0):
        return
    if not isinstance(pool, int) or not 128 <= pool <= 4096:
        raise ProfileError("Texture pool phải từ 128 đến 4096 MB: %s" % pool)


def validate(profile, game_dir):
    """Same rules as the app; returns the exe (relative, '/'-separated) the app would launch."""
    if profile.get("schemaVersion") != 1:
        raise ProfileError("Phiên bản profile không được hỗ trợ (cần schemaVersion = 1).")
    name = profile.get("name") or ""
    if not name.strip() or len(name) > 80 or re.search(r'[\\/:*?"<>|]', name):
        raise ProfileError("Tên game (name) trống, quá dài hoặc có ký tự không hợp lệ.")
    for arg in profile.get("args") or []:
        if not isinstance(arg, str) or not SAFE_VALUE.match(arg):
            raise ProfileError("Tham số chạy (args) có ký tự không an toàn: %s" % arg)
    for k, v in (profile.get("env") or {}).items():
        if not ENV_KEY.match(k) or not isinstance(v, str) or not SAFE_VALUE.match(v):
            raise ProfileError("Biến môi trường (env) không an toàn: %s" % k)
    for k, v in (profile.get("dllOverrides") or {}).items():
        if not re.match(r"^[A-Za-z0-9_.-]+$", k) or v not in ("n", "b", "n,b", "b,n", ""):
            raise ProfileError("dllOverrides không hợp lệ: %s (chỉ dùng n, b, n,b hoặc b,n)" % k)
    _check_resolution(profile.get("resolution"))
    _check_fps(profile.get("fpsLimit"))
    _check_pool(profile.get("texturePool"))
    weak = profile.get("weakDevice") or {}
    _check_resolution(weak.get("resolution"))
    _check_fps(weak.get("fpsLimit"))
    _check_pool(weak.get("texturePool"))
    controls = profile.get("controls")
    if controls is not None and controls not in CONTROLS:
        raise ProfileError("Bộ phím (controls) phải là một trong: %s: %s" % (", ".join(CONTROLS), controls))
    if profile.get("runner") not in (None, "html", "renpy", "rgss", "wine"):
        raise ProfileError("Cách chạy (runner) phải là html, renpy, rgss hoặc wine: %s" % profile.get("runner"))
    locale = profile.get("locale")
    if locale not in (None, "") and not (isinstance(locale, str) and re.match(r"^[a-z]{2}_[A-Z]{2}(\.UTF-8)?$", locale)):
        raise ProfileError("Ngôn ngữ (locale) phải có dạng ja_JP hoặc ja_JP.UTF-8: %s" % locale)
    for section, kv in (profile.get("ueEngineIni") or {}).items():
        if not INI_SECTION.match(section) or not isinstance(kv, dict):
            raise ProfileError("Mục Engine.ini không được phép: %s" % section)
        for k, v in kv.items():
            if not INI_KEY.match(k) or not isinstance(v, str) or not SAFE_VALUE.match(v):
                raise ProfileError("Khóa Engine.ini không hợp lệ: %s" % k)

    if "wine " in os.path.basename(os.path.normpath(game_dir)) or "wine " in (profile.get("exe") or ""):
        raise ProfileError('Tên thư mục hoặc đường dẫn exe không được chứa chữ "wine " (viết thường, có dấu cách). '
                           'Hãy đổi tên thư mục.')
    engine = detect_engine(game_dir)
    suggested = resolve_exe(game_dir, engine)
    exe = (profile.get("exe") or "").strip().replace("\\", "/")
    if not exe:
        if not suggested:
            raise ProfileError('Không tìm thấy file .exe để chạy. Hãy ghi rõ "exe" trong profile.')
        if "wine " in suggested:
            raise ProfileError('Đường dẫn exe không được chứa chữ "wine " (viết thường, có dấu cách): %s' % suggested)
        return suggested
    if exe.startswith("/") or re.match(r"^[A-Za-z]:", exe) or ".." in exe:
        raise ProfileError("Đường dẫn exe phải nằm trong thư mục game: %s" % exe)
    if not exe.lower().endswith(".exe"):
        raise ProfileError("File chạy phải là .exe: %s" % exe)
    full = os.path.realpath(os.path.join(game_dir, exe))
    if not full.startswith(os.path.realpath(game_dir) + os.sep):
        raise ProfileError("Đường dẫn exe phải nằm trong thư mục game: %s" % exe)
    if not os.path.isfile(full):
        raise ProfileError("Không tìm thấy file chạy: %s" % exe)
    if engine == "UNREAL" and not exe.lower().endswith("-shipping.exe") and suggested:
        exe = suggested
    if "wine " in exe:
        raise ProfileError('Đường dẫn exe không được chứa chữ "wine " (viết thường, có dấu cách): %s' % exe)
    return exe

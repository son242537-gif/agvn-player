"""Shared rules for the AGVN PC toolkit: engine/exe detection and agvn-profile.json v1 validation.

Mirrors app/src/main/java/com/winlator/cmod/agvn/GameExeResolver.java and AgvnProfileValidator.java.
Keep both sides in sync; tests/test_toolkit.py and the Java unit tests validate the same example profile.
Copyright (c) 2026 agvn.io.vn - MIT License.
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


def detect_engine(game_dir):
    if find_shipping(game_dir) or os.path.isdir(os.path.join(game_dir, "Engine", "Binaries")):
        return "UNREAL"
    names = _children(game_dir)
    for n in names:
        p = os.path.join(game_dir, n)
        if os.path.isdir(p) and n.lower().endswith("_data") and os.path.exists(os.path.join(p, "globalgamemanagers")):
            return "UNITY"
        if os.path.isfile(p) and n.lower() == "unityplayer.dll":
            return "UNITY"
    for n in names:
        p, low = os.path.join(game_dir, n), n.lower()
        if os.path.isfile(p) and low.endswith(".pck"):
            return "GODOT"
        if os.path.isfile(p) and low == "data.win":
            return "GAMEMAKER"
        if os.path.isdir(p) and low == "renpy":
            return "RENPY"
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
        low = n.lower()
        if "launcher" not in low and "setup" not in low and "config" not in low:
            return n
    return exes[0]


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
    _check_resolution(profile.get("resolution"))
    _check_fps(profile.get("fpsLimit"))
    _check_pool(profile.get("texturePool"))
    weak = profile.get("weakDevice") or {}
    _check_resolution(weak.get("resolution"))
    _check_fps(weak.get("fpsLimit"))
    _check_pool(weak.get("texturePool"))
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

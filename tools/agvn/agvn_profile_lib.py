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
                "editor", "server", "tool", "helper", "physx", "dotnet", "oalinst", "nwjc", "bssndrpt",
                # the same in Japanese, Chinese and Korean (GameExeResolver.HELPERS)
                "設定", "设置", "設置", "コンフィグ", "ランチャー", "セットアップ", "アンインストール", "启动器", "卸载", "설정")
# On-screen controls layouts (AgvnLayouts.java): profile "controls" value -> bundled layout; order = ids 9000..9005.
CONTROLS = ("pc", "vn", "rpg", "2d", "action", "mouse")
_ENGINE_CONTROLS = {"RENPY": "vn", "KIRIKIRI": "vn", "TYRANO": "vn", "SIGLUS": "vn", "NSCRIPTER": "vn",
                    "RPGMAKER": "rpg", "RPGMAKER_MV": "rpg", "WOLFRPG": "rpg", "SRPG_STUDIO": "rpg", "GAMEMAKER": "2d",
                    "GODOT": "2d"}


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
    # SRPG Studio: its data and runtime packs beside game.exe, and no DLL
    if _is_file(sub("data.dts")) and (_is_file(sub("runtime.rts")) or _is_file(sub("environment.evs"))):
        return "SRPG_STUDIO"
    # a stray .xp3 or .nsa in an Electron or NW.js game is no KiriKiri or NScripter one
    chromium = any(_is_file(sub(*rel)) for rel in (("chrome_100_percent.pak",), ("v8_context_snapshot.bin",),
                                                   ("package.nw",), ("resources", "app.asar"),
                                                   ("resources", "app", "package.json")))
    if not chromium and _any_file(game_dir, "", ".xp3"):
        return "KIRIKIRI"
    if not chromium and (_any_file(game_dir, "", ".nsa") or _is_file(sub("nscript.dat"))):
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


def _machine(path):
    """The machine a PE file is for (0x8664: 64-bit, 0x14c: 32-bit), or None for no PE file."""
    try:
        with open(path, "rb") as f:
            dos = f.read(64)
            if len(dos) < 64 or dos[:2] != b"MZ":
                return None
            f.seek(int.from_bytes(dos[0x3c:0x40], "little"))
            nt = f.read(6)
    except OSError:
        return None
    return int.from_bytes(nt[4:6], "little") if len(nt) == 6 and nt[:4] == b"PE\0\0" else None


def is_dotnet(path):
    """True for a .NET program: a PE file with a CLR header (data directory 14), as AgvnWineMono.isDotNet."""
    try:
        with open(path, "rb") as f:
            dos = f.read(64)
            if len(dos) < 64 or dos[:2] != b"MZ":
                return False
            pe = int.from_bytes(dos[0x3c:0x40], "little")
            f.seek(pe)
            nt = f.read(24)
            if len(nt) < 24 or nt[:4] != b"PE\0\0":
                return False
            opt = f.read(int.from_bytes(nt[20:22], "little"))
    except OSError:
        return False
    dirs = {0x10b: 96, 0x20b: 112}.get(int.from_bytes(opt[0:2], "little") if len(opt) >= 2 else 0)
    entry = dirs + 14 * 8 if dirs else 0
    if not dirs or len(opt) < entry + 8 or int.from_bytes(opt[dirs - 4:dirs], "little") <= 14:
        return False
    return int.from_bytes(opt[entry:entry + 4], "little") != 0 and int.from_bytes(opt[entry + 4:entry + 8], "little") != 0


def has_64bit_twin(game_dir, name):
    """True for "<Name>-32.exe" beside a 64-bit "<Name>.exe" that can start (Ren'Py 7), as AgvnExe64.twin."""
    if len(name) <= 7 or not name.lower().endswith("-32.exe"):
        return False
    twin = os.path.join(game_dir, name[:-7] + name[-4:])
    if not os.path.isfile(twin) or _machine(twin) != 0x8664:
        return False
    libs = [c.lower() for c in _children(os.path.join(game_dir, "lib"))]
    return any("windows-x86_64" in c for c in libs) or not any("windows-i686" in c for c in libs)


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
    exes = [n for n in exes if not has_64bit_twin(game_dir, n)]  # "<Name>-32.exe" sorts before "<Name>.exe"
    native = [n for n in exes if not is_dotnet(os.path.join(game_dir, n))]
    if engine != "UNKNOWN" and native:
        exes = native  # a small .NET launcher beside the exe of an engine that is no .NET one (GameMaker)
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


# NW.js's own files beside its exe, when package.json sets no window (AgvnNwjsGame.RUNTIME)
NWJS_RUNTIME = ("nw.dll", "nw_elf.dll", "nw.pak", "resources.pak")


def _read(path, limit=64 * 1024):
    try:
        with open(path, encoding="utf-8", errors="replace") as f:
            return f.read(limit)
    except OSError:
        return None


def _index(folder):
    return next((os.path.join(folder, n) for n in _children(folder or "")
                 if n.lower() == "index.html" and os.path.isfile(os.path.join(folder, n))), None) if folder else None


def nwjs_page(game_dir):
    """The page an NW.js page game starts on, beside its package.json (AgvnNwjsGame.index); None for any other folder."""
    text = _read(os.path.join(game_dir, "package.json"))
    if text is None:
        return None
    if not re.search(r'"window"\s*:\s*\{', text) and not any(_is_file(_path(game_dir, n)) for n in NWJS_RUNTIME):
        return None
    if (re.search(r'"(node-main|bg-script)"\s*:\s*"[^"]+"', text) or _is_dir(_path(game_dir, "node_modules"))
            or _any_file(game_dir, "", ".node")):
        return None  # it needs Node itself
    m = re.search(r'"main"\s*:\s*"([^"]*)"', text)
    page = (m.group(1) if m else "").strip().replace("\\", "/")
    if page.startswith("app://"):
        page = page[6:]
    page = re.split(r"[?#]", page, maxsplit=1)[0]
    while page.startswith("./"):
        page = page[2:]
    if not page or "/" in page or not page.lower().endswith((".html", ".htm")):
        return None
    found = _path(game_dir, page.lower())
    return found if _is_file(found) else None


def _renpy_python(game_dir):
    """3 for Ren'Py 8, 2 for 6 and 7, 0 when the folder does not tell (AgvnRenpyGame.pythonOf)."""
    lib = [n.lower() for n in _children(os.path.join(game_dir, "lib")) if os.path.isdir(os.path.join(game_dir, "lib", n))]
    if any(n.startswith(("py3-", "python3")) for n in lib):
        return 3
    if any(n.startswith(("py2-", "python2", "pythonlib2", "windows-", "linux-", "darwin-", "mac-")) for n in lib):
        return 2
    renpy = os.path.join(game_dir, "renpy")
    if os.path.isdir(os.path.join(renpy, "__pycache__")):
        return 3
    if _any_file(renpy, "", ".pyo", ".pyc"):
        return 2
    m = re.match(r"\s*\(?\s*(\d+)", _read(os.path.join(game_dir, "game", "script_version.txt")) or "")
    major = int(m.group(1)) if m else 0
    return 3 if major >= 8 else 2 if major in (6, 7) else 0


def _rgss_ini(game_dir):
    """Game.ini, else the .ini named as an exe beside it (AgvnRgssGame.readIni)."""
    names = _children(game_dir)
    inis = [n for n in names if n.lower().endswith(".ini") and os.path.isfile(os.path.join(game_dir, n))]
    for n in inis:
        if n.lower() == "game.ini":
            return n
    exes = {n.lower() for n in names}
    return next((n for n in reversed(inis) if (n[:-4] + ".exe").lower() in exes), None)


def _rgss_from(name):
    n = (name or "").lower()
    return 3 if n.endswith((".rgss3a", ".rvdata2")) else 2 if n.endswith((".rgss2a", ".rvdata")) else \
        1 if n.endswith((".rgssad", ".rxdata")) else 0


def rgss_version(game_dir):
    """1 for XP, 2 for VX, 3 for VX Ace; 0 when the folder is not an RGSS game (AgvnRgssGame.rgssVersion)."""
    ini = _rgss_ini(game_dir)
    if ini is None:
        return 0
    values, section = {}, False
    for line in (_read(os.path.join(game_dir, ini)) or "").splitlines():
        line = line.strip()
        if line.startswith("["):
            section = line.lower() == "[game]"
        elif section and "=" in line and line.index("=") > 0:
            key, value = line.split("=", 1)
            values[key.strip().lower()] = value.strip()
    library = values.get("library", "").upper()
    i = library.rfind("RGSS")
    v = int(library[i + 4]) if i >= 0 and i + 4 < len(library) and library[i + 4] in "123" else 0
    v = v or _rgss_from(values.get("scripts"))
    execname = ini[:-4].lower()
    for n in _children(game_dir):
        if not v and n.lower().startswith(execname + ".") and os.path.isfile(os.path.join(game_dir, n)):
            v = _rgss_from(n)
    data = os.path.join(game_dir, "Data")
    for n in _children(data):
        if not v and os.path.isfile(os.path.join(data, n)):
            v = _rgss_from(n)
    return v


def light_can_run(game_dir, engine):
    """True when "Chạy nhẹ" runs the folder as a game of engine, with or without an .exe (AgvnLightGame.canRun)."""
    if engine == "RPGMAKER_MV":
        return bool(_index(_path(game_dir, "www")) or _index(game_dir))
    if engine == "TYRANO":
        return bool(_index(game_dir)) and os.path.isdir(os.path.join(game_dir, "tyrano"))
    if engine == "UNKNOWN":
        return nwjs_page(game_dir) is not None
    if engine == "RENPY":
        return os.path.isdir(os.path.join(game_dir, "game")) and _renpy_python(game_dir) == 3
    if engine == "RPGMAKER":
        return rgss_version(game_dir) != 0
    return False


def placeholder_exe(game_dir, engine):
    """The exe a game without one gets: what its PC copy would have (AgvnLightGame.placeholderExe)."""
    ini = _rgss_ini(game_dir) if engine == "RPGMAKER" else None
    return (ini[:-4] if ini else "Game") + ".exe"


def clean_name(name):
    """name fit for a file name (AgvnProfile.cleanName): "Tên game: Phụ đề" becomes "Tên game - Phụ đề"."""
    if not isinstance(name, str):
        return ""
    if not re.search(r'[\\/:*?"<>|]', name):
        return name.strip()
    clean = re.sub(r"^\s*:+\s*|\s*:+\s*$", "", name)
    clean = re.sub(r'[\\/*?"<>|]', " ", re.sub(r"\s*:+\s*", " - ", clean))
    return re.sub(r"\s+", " ", clean).strip()


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
    name = clean_name(profile.get("name"))  # also the shortcut's file name (AgvnProfile.FORBIDDEN)
    if not name:
        raise ProfileError("Tên game (name) trống.")
    if len(name) > 80:
        raise ProfileError("Tên game (name) dài %d ký tự, tối đa 80: %s" % (len(name), name))
    profile["name"] = name
    for arg in profile.get("args") or []:
        if not isinstance(arg, str) or not SAFE_VALUE.match(arg):
            raise ProfileError("Tham số chạy (args) có ký tự không an toàn: %s" % arg)
    for k, v in (profile.get("env") or {}).items():
        if not ENV_KEY.match(k) or not isinstance(v, str) or not SAFE_VALUE.match(v):
            raise ProfileError("Biến môi trường (env) không an toàn: %s" % k)
    for k, v in (profile.get("dllOverrides") or {}).items():
        # mirrors GameDllOverrides.MODE: "", "d" and "disabled" turn the DLL off, as Wine reads them
        if not re.match(r"^[A-Za-z0-9_.-]+$", k) or v not in ("n", "b", "n,b", "b,n", "d", "disabled", ""):
            raise ProfileError("dllOverrides không hợp lệ: %s = %s (chỉ dùng n, b, n,b, b,n; \"d\" hoặc \"\" để tắt DLL)" % (k, v))
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
    if profile.get("runner") not in (None, "html", "renpy", "rgss", "godot", "wine"):
        raise ProfileError("Cách chạy (runner) phải là html, renpy, rgss, godot hoặc wine: %s" % profile.get("runner"))
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
        if not suggested and light_can_run(game_dir, engine):
            return placeholder_exe(game_dir, engine)  # a copy made for phones: "Chạy nhẹ" runs it, never this exe
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

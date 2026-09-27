#!/usr/bin/env python3
"""Tạo agvn-profile.json cho một thư mục game (AGVN Player, schema v1).

Ví dụ:
  python tao-agvn-profile.py "D:/AGVN/AVDirector" --name "AV Director LIFE"
  python tao-agvn-profile.py D:/AGVN/Game --fps 30 --weak-fps 24 --screen 1280x720 --pool 1024 --weak-pool 512
  python tao-agvn-profile.py D:/AGVN/Game --check      # chỉ kiểm tra profile đang có

Exe được tự tìm giống hệt app (Unreal: <Game>-Win64-Shipping.exe). Profile được kiểm tra bằng đúng luật của app
trước khi ghi; sai luật thì báo lỗi tiếng Việt và không ghi file. Không truy cập mạng.
Copyright (c) 2026 agvn.io.vn - MIT License.
"""
import argparse
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from agvn_profile_lib import ProfileError, detect_engine, resolve_exe, validate  # noqa: E402

PROFILE = "agvn-profile.json"


def pool_for_ram(ram_mb):
    """Texture pool mặc định theo RAM máy mục tiêu (khớp bảng mức máy của app)."""
    if ram_mb >= 7000:
        return 1024
    if ram_mb >= 5000:
        return 768
    return 512


def build_profile(game_dir, args):
    engine = detect_engine(game_dir)
    exe = args.exe or resolve_exe(game_dir, engine)
    if not exe:
        raise ProfileError("Không tìm thấy file .exe trong %s. Dùng --exe để chỉ rõ." % game_dir)
    profile = {
        "schemaVersion": 1,
        "name": args.name or os.path.basename(os.path.normpath(game_dir)),
        "exe": exe,
        "args": args.arg or [],
        "env": {},
        "resolution": args.screen,
        "fpsLimit": args.fps,
        "simulatedTouchscreen": not args.no_touch,
        "weakDevice": {"resolution": args.weak_screen, "fpsLimit": args.weak_fps},
        "ueEngineIni": {},
    }
    if engine == "UNREAL":
        profile["texturePool"] = args.pool or pool_for_ram(args.ram_mb)
        profile["weakDevice"]["texturePool"] = args.weak_pool or 512
    return profile, engine


def main(argv=None):
    p = argparse.ArgumentParser(description="Tạo agvn-profile.json cho AGVN Player")
    p.add_argument("game_dir")
    p.add_argument("--name")
    p.add_argument("--exe", help="đường dẫn exe tương đối trong thư mục game (mặc định: tự tìm)")
    p.add_argument("--arg", action="append", help="tham số chạy, lặp lại được")
    p.add_argument("--ram-mb", type=int, default=8000, help="RAM máy mục tiêu, để chọn texture pool")
    p.add_argument("--fps", type=int, default=30)
    p.add_argument("--weak-fps", type=int, default=24)
    p.add_argument("--screen", default="1280x720")
    p.add_argument("--weak-screen", default="854x480")
    p.add_argument("--pool", type=int, help="texture pool (MB), chỉ cho Unreal")
    p.add_argument("--weak-pool", type=int, help="texture pool máy yếu (MB), chỉ cho Unreal")
    p.add_argument("--no-touch", action="store_true", help="tắt Simulated Touchscreen")
    p.add_argument("--force", action="store_true", help="ghi đè profile đang có")
    p.add_argument("--check", action="store_true", help="chỉ kiểm tra profile đang có")
    args = p.parse_args(argv)

    game_dir = args.game_dir
    if not os.path.isdir(game_dir):
        print("Không thấy thư mục: %s" % game_dir, file=sys.stderr)
        return 2
    target = os.path.join(game_dir, PROFILE)
    try:
        if args.check:
            with open(target, encoding="utf-8") as f:
                profile = json.load(f)
            exe = validate(profile, game_dir)
            print("Hợp lệ. App sẽ chạy: %s" % exe)
            return 0
        if os.path.exists(target) and not args.force:
            print("Đã có %s. Thêm --force để ghi đè." % target, file=sys.stderr)
            return 3
        profile, engine = build_profile(game_dir, args)
        exe = validate(profile, game_dir)
    except (ProfileError, ValueError, OSError) as e:
        print("LỖI: %s" % e, file=sys.stderr)
        return 1
    with open(target, "w", encoding="utf-8") as f:
        json.dump(profile, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("Engine: %s\nExe: %s\nĐã ghi: %s" % (engine, exe, target))
    return 0


if __name__ == "__main__":
    sys.exit(main())

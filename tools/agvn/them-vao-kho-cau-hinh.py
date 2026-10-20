#!/usr/bin/env python3
"""Đưa agvn-profile.json của một game vào kho cấu hình có sẵn trong app (assets/agvn/game-profiles.json).

Ví dụ:
  python them-vao-kho-cau-hinh.py "D:/AGVN/AVDirector"
  python them-vao-kho-cau-hinh.py D:/AGVN/Game --match "Game.exe" --match "www/js/plugins/MyPlugin.js"

Game đã có trong kho (cùng id) thì được thay bằng bản mới. Profile được kiểm tra bằng đúng luật của app.
"match": các file phải có trong thư mục game; mặc định là exe của profile. Tên exe chung chung (Game.exe…)
phải kèm thêm một file riêng của game đó. Không truy cập mạng.
Copyright (c) 2026 agvn.io - MIT License.
"""
import argparse
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from agvn_profile_lib import ProfileError, validate  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
CATALOG = os.path.join(HERE, "..", "..", "app", "src", "main", "assets", "agvn", "game-profiles.json")
# Mirrors AgvnProfileCatalog.GENERIC.
GENERIC = {"game.exe", "rpg_rt.exe", "nw.exe", "launcher.exe", "start.exe", "play.exe", "main.exe", "setup.exe"}


def is_specific(match):
    if not match or any(not p or p.startswith("/") or ".." in p or ":" in p for p in match):
        return False
    return len(match) > 1 or "/" in match[0] or match[0].lower() not in GENERIC


def entry_for(game_dir, match=None):
    with open(os.path.join(game_dir, "agvn-profile.json"), encoding="utf-8") as f:
        profile = json.load(f)
    exe = validate(profile, game_dir)
    profile["exe"] = exe
    match = [m.replace("\\", "/") for m in (match or [exe])]
    if not is_specific(match):
        raise ProfileError('"%s" là tên chung của nhiều game: thêm --match với một file riêng của game này.' % match[0])
    for m in match:
        if not os.path.exists(os.path.join(game_dir, m)):
            raise ProfileError("Không có file %s trong thư mục game." % m)
    slug = re.sub(r"[^a-z0-9]+", "-", profile["name"].lower()).strip("-") or "game"
    return {"id": slug, "match": match, "profile": profile}


def add(catalog_path, entry):
    with open(catalog_path, encoding="utf-8") as f:
        catalog = json.load(f)
    catalog["entries"] = [e for e in catalog["entries"] if e.get("id") != entry["id"]] + [entry]
    with open(catalog_path, "w", encoding="utf-8") as f:
        json.dump(catalog, f, ensure_ascii=False, indent=2)
        f.write("\n")
    return len(catalog["entries"])


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("game_dir")
    ap.add_argument("--match", action="append", help="file phải có trong thư mục game (lặp lại được)")
    ap.add_argument("--catalog", default=CATALOG)
    args = ap.parse_args()
    try:
        entry = entry_for(args.game_dir, args.match)
    except (ProfileError, OSError, ValueError) as e:
        print("LỖI: %s" % e)
        return 1
    total = add(args.catalog, entry)
    print("Đã thêm %s vào kho (%d game). Dựng lại app để người chơi nhận." % (entry["profile"]["name"], total))
    return 0


if __name__ == "__main__":
    sys.exit(main())

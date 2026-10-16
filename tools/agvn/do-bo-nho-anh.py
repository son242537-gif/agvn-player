#!/usr/bin/env python3
"""Đo bộ nhớ ảnh (texture) của một game để biết nên làm nhẹ phần nào trước.

  python do-bo-nho-anh.py <thu_muc_game> [--csv ket-qua.csv] [--top 30]

- Ảnh rời (DDS/PNG/JPG/TGA/BMP, kể cả atlas Spine): đọc header, tính dung lượng khi nạp lên GPU.
- Unreal: file .pak/.utoc/.ucas là gói nén, script KHÔNG giải nén (không tự tải repak). Hãy giải nén bằng
  repak/FModel ra một thư mục rồi chạy script trên thư mục đó; .ubulk/.uptnl được tính như dữ liệu texture.
- Unity: nếu máy có UnityPy (pip install UnityPy) thì đọc Texture2D trong .assets/bundle; nếu không thì chỉ
  liệt kê dung lượng file.
Chỉ đọc phần đầu file, không tải cả gói vào RAM, không dùng mạng.
Copyright (c) 2026 agvn.io - MIT License.
"""
import argparse
import csv
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from agvn_profile_lib import detect_engine  # noqa: E402
from texture_headers import read_texture  # noqa: E402

UE_BULK = (".ubulk", ".uptnl")
UE_CONTAINERS = (".pak", ".utoc", ".ucas")
UNITY_FILES = (".assets", ".bundle", ".unity3d", ".resource", ".ress")
MB = 1024.0 * 1024.0


def unity_textures(path):
    try:
        import UnityPy  # optional dependency
    except ImportError:
        return None
    rows = []
    try:
        env = UnityPy.load(path)
        for obj in env.objects:
            if obj.type.name != "Texture2D":
                continue
            tex = obj.read()
            size = getattr(tex, "m_CompleteImageSize", 0) or 0
            rows.append((path + "::" + str(getattr(tex, "m_Name", "?")), tex.m_Width, tex.m_Height,
                         str(getattr(tex, "m_TextureFormat", "?")), getattr(tex, "m_MipCount", 0), int(size)))
    except Exception as e:  # corrupt or unsupported file: report and continue
        print("  bỏ qua %s: %s" % (path, e), file=sys.stderr)
    return rows


def scan(game_dir):
    rows, notes = [], {"containers": [], "unity_raw": 0, "unitypy": True}
    for root, _dirs, files in os.walk(game_dir):
        for name in files:
            path = os.path.join(root, name)
            rel = os.path.relpath(path, game_dir).replace(os.sep, "/")
            low = name.lower()
            info = read_texture(path)
            if info:
                rows.append((rel,) + info)
            elif low.endswith(UE_BULK):
                rows.append((rel, 0, 0, "UE-bulk", 0, os.path.getsize(path)))
            elif low.endswith(UE_CONTAINERS):
                notes["containers"].append((rel, os.path.getsize(path)))
            elif low.endswith(UNITY_FILES) or low in ("sharedassets0.assets", "data.unity3d"):
                found = unity_textures(path)
                if found is None:
                    notes["unitypy"] = False
                    notes["unity_raw"] += os.path.getsize(path)
                else:
                    rows.extend((r[0].replace(game_dir, "").lstrip("/\\"),) + r[1:] for r in found)
    rows.sort(key=lambda r: r[5], reverse=True)
    return rows, notes


def main(argv=None):
    p = argparse.ArgumentParser(description="Đo bộ nhớ texture của game")
    p.add_argument("game_dir")
    p.add_argument("--csv", default=None, help="file CSV kết quả (mặc định: <thu_muc>/agvn-texture-report.csv)")
    p.add_argument("--top", type=int, default=30)
    args = p.parse_args(argv)
    if not os.path.isdir(args.game_dir):
        print("Không thấy thư mục: %s" % args.game_dir, file=sys.stderr)
        return 2
    rows, notes = scan(args.game_dir)
    out = args.csv or os.path.join(args.game_dir, "agvn-texture-report.csv")
    with open(out, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["file", "width", "height", "format", "mips", "gpu_mb"])
        for r in rows:
            w.writerow([r[0], r[1], r[2], r[3], r[4], "%.2f" % (r[5] / MB)])
    total = sum(r[5] for r in rows)
    print("Engine: %s" % detect_engine(args.game_dir))
    print("Số texture: %d, tổng bộ nhớ GPU ước tính: %.1f MB" % (len(rows), total / MB))
    print("Nếu giảm mọi texture xuống 1/2 cạnh: tiết kiệm khoảng %.1f MB (75%%)" % (total * 0.75 / MB))
    print("\nTop %d texture lớn nhất:" % args.top)
    for r in rows[:args.top]:
        dims = "%dx%d" % (r[1], r[2]) if r[1] else "-"
        print("  %8.1f MB  %-11s %-10s %s" % (r[5] / MB, dims, r[3], r[0]))
    if notes["containers"]:
        size = sum(s for _, s in notes["containers"]) / MB
        print("\nChú ý: %d gói Unreal (.pak/.utoc/.ucas, %.0f MB) chưa được đo. Giải nén bằng repak/FModel rồi đo "
              "lại thư mục đã giải nén." % (len(notes["containers"]), size))
    if not notes["unitypy"]:
        print("\nChú ý: có %.0f MB file Unity nhưng máy chưa cài UnityPy (pip install UnityPy) nên chưa đo được "
              "texture bên trong." % (notes["unity_raw"] / MB))
    print("\nĐã ghi CSV: %s" % out)
    return 0


if __name__ == "__main__":
    sys.exit(main())

"""Header-only texture size readers for the AGVN PC toolkit (never loads whole files).

Each reader returns (width, height, format, mip_count, gpu_bytes) or None.
gpu_bytes is what the texture occupies once uploaded: compressed block formats stay compressed,
PNG/JPG/TGA/BMP are expanded to RGBA8 (4 bytes/pixel) with a full mip chain (x4/3).
Copyright (c) 2026 agvn.io.vn - MIT License.
"""
import struct

FULL_MIP_CHAIN = 4.0 / 3.0
# bytes per pixel for DDS FourCC / DXGI formats (block formats expressed per pixel)
FOURCC_BPP = {b"DXT1": 0.5, b"ATI1": 0.5, b"BC4U": 0.5, b"BC4S": 0.5,
              b"DXT2": 1, b"DXT3": 1, b"DXT4": 1, b"DXT5": 1, b"ATI2": 1, b"BC5U": 1, b"BC5S": 1}
DXGI_BPP = {70: 0.5, 71: 0.5, 72: 0.5, 79: 0.5, 80: 0.5, 81: 0.5,           # BC1, BC4
            73: 1, 74: 1, 75: 1, 76: 1, 77: 1, 78: 1, 82: 1, 83: 1, 84: 1,  # BC2, BC3, BC5
            94: 1, 95: 1, 96: 1, 97: 1, 98: 1, 99: 1,                        # BC6H, BC7
            28: 4, 29: 4, 87: 4, 88: 4, 91: 4, 10: 8, 2: 16, 61: 1, 49: 2}


def _mip_bytes(width, height, bpp, mips):
    total, w, h = 0.0, width, height
    for _ in range(max(1, mips)):
        if bpp < 4 and bpp in (0.5, 1):  # block compressed: 4x4 minimum
            total += max(4, w) * max(4, h) * bpp
        else:
            total += w * h * bpp
        w, h = max(1, w // 2), max(1, h // 2)
    return int(total)


def read_dds(f):
    head = f.read(148)
    if len(head) < 128 or head[:4] != b"DDS ":
        return None
    height, width = struct.unpack_from("<II", head, 12)
    mips = struct.unpack_from("<I", head, 28)[0] or 1
    pf_flags, fourcc, bitcount = struct.unpack_from("<I4sI", head, 80)
    if fourcc == b"DX10" and len(head) >= 132:
        dxgi = struct.unpack_from("<I", head, 128)[0]
        bpp, fmt = DXGI_BPP.get(dxgi, 4), "DXGI_%d" % dxgi
    elif pf_flags & 0x4:
        bpp, fmt = FOURCC_BPP.get(fourcc, 4), fourcc.decode("latin-1")
    else:
        bpp, fmt = max(1, bitcount // 8), "RGB%d" % bitcount
    return width, height, fmt, mips, _mip_bytes(width, height, bpp, mips)


def _rgba(width, height, fmt):
    return width, height, fmt, 0, int(width * height * 4 * FULL_MIP_CHAIN)


def read_png(f):
    head = f.read(24)
    if len(head) < 24 or head[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    width, height = struct.unpack(">II", head[16:24])
    return _rgba(width, height, "PNG")


def read_tga(f):
    head = f.read(18)
    if len(head) < 18 or head[2] not in (1, 2, 3, 9, 10, 11):
        return None
    width, height = struct.unpack_from("<HH", head, 12)
    return _rgba(width, height, "TGA") if width and height else None


def read_bmp(f):
    head = f.read(26)
    if len(head) < 26 or head[:2] != b"BM":
        return None
    width, height = struct.unpack_from("<ii", head, 18)
    return _rgba(abs(width), abs(height), "BMP")


def read_jpg(f, limit=1 << 20):
    if f.read(2) != b"\xff\xd8":
        return None
    read = 2
    while read < limit:
        marker = f.read(2)
        if len(marker) < 2 or marker[0] != 0xFF:
            return None
        length_raw = f.read(2)
        if len(length_raw) < 2:
            return None
        length = struct.unpack(">H", length_raw)[0]
        if marker[1] in (0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF):
            data = f.read(5)
            height, width = struct.unpack(">HH", data[1:5])
            return _rgba(width, height, "JPG")
        f.seek(length - 2, 1)
        read += length + 2
    return None


READERS = {".dds": read_dds, ".png": read_png, ".tga": read_tga, ".bmp": read_bmp, ".jpg": read_jpg, ".jpeg": read_jpg}


def read_texture(path):
    ext = path[path.rfind("."):].lower() if "." in path else ""
    reader = READERS.get(ext)
    if not reader:
        return None
    try:
        with open(path, "rb") as f:
            return reader(f)
    except (OSError, struct.error, IndexError):
        return None

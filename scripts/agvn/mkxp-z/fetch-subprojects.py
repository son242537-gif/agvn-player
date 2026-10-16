#!/usr/bin/env python3
# AGVN Player - fills mkxp-z's subprojects/packagecache with the sources Meson cannot download on the build machine.
# GitHub tag tarballs and some hosts are blocked there, so each file comes another way:
#   git <repo> <ref> <commit>  clone the pinned commit and rebuild GitHub's tarball (git archive | gzip -n, byte for
#                              byte the same file GitHub serves)
#   url <url>                  the same file from a mirror
# Every file must match the SHA-256 that mkxp-z pins in its .wrap, or the build stops. Wrapdb patch archives come from
# wrapdb's GitHub releases. Usage: fetch-subprojects.py <sources.lock> <mkxp-z source dir>
# Copyright (c) 2026 agvn.io - MIT License.
import configparser
import hashlib
import os
import shutil
import subprocess
import sys
import tempfile
import urllib.parse
import urllib.request


def fail(msg):
    sys.exit("[AGVN mkxp-z] LỖI: " + msg)


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def download(url, dest):
    with urllib.request.urlopen(url, timeout=300) as r, open(dest, "wb") as out:
        shutil.copyfileobj(r, out)


def git_tarball(repo, ref, commit, prefix, dest):
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.run(["git", "init", "-q", tmp], check=True)
        subprocess.run(["git", "-C", tmp, "fetch", "-q", "--depth", "1", repo, ref], check=True)
        got = subprocess.run(["git", "-C", tmp, "rev-parse", "FETCH_HEAD^{commit}"], check=True,
                             capture_output=True, text=True).stdout.strip()
        if got != commit:
            fail(f"{repo} {ref} là commit {got}, không phải {commit} đã ghim")
        with open(dest, "wb") as out:
            archive = subprocess.Popen(["git", "-C", tmp, "archive", "--format=tar", "--prefix=" + prefix + "/",
                                        commit], stdout=subprocess.PIPE)
            subprocess.run(["gzip", "-n"], stdin=archive.stdout, stdout=out, check=True)
            if archive.wait() != 0:
                fail(f"git archive {repo} {ref}")


def read_wrap(src, name):
    wrap = configparser.ConfigParser(interpolation=None)
    if not wrap.read(os.path.join(src, "subprojects", name + ".wrap")):
        fail(f"không có subprojects/{name}.wrap")
    return wrap["wrap-file"]


def fetch(cache, filename, want, get):
    dest = os.path.join(cache, filename)
    if os.path.exists(dest) and sha256(dest) == want:
        return
    get(dest + ".part")
    got = sha256(dest + ".part")
    if got != want:
        os.remove(dest + ".part")
        fail(f"{filename}: SHA-256 {got}, wrap ghim {want}")
    os.replace(dest + ".part", dest)
    print(f"[AGVN mkxp-z] {filename}: SHA-256 khớp wrap")


def main(lock, src):
    cache = os.path.join(src, "subprojects", "packagecache")
    os.makedirs(cache, exist_ok=True)
    for line in open(lock, encoding="utf-8"):
        parts = line.split()
        if len(parts) < 3 or parts[0] != "subproject":
            continue
        name, kind, args = parts[1], parts[2], parts[3:]
        wrap = read_wrap(src, name)
        prefix = wrap.get("directory", name)
        if kind == "git":
            repo, ref, commit = args
            get = lambda dest, r=repo, f=ref, c=commit, p=prefix: git_tarball(r, f, c, p, dest)
        elif kind == "url":
            get = lambda dest, u=args[0]: download(u, dest)
        elif kind != "meson":
            fail(f"không hiểu dòng: {line.strip()}")
        if kind != "meson":
            fetch(cache, wrap["source_filename"], wrap["source_hash"], get)
        patch_url = wrap.get("patch_url", "")
        if urllib.parse.urlparse(patch_url).netloc == "wrapdb.mesonbuild.com":
            tag = patch_url.rstrip("/").split("/")[-2]
            url = f"https://github.com/mesonbuild/wrapdb/releases/download/{tag}/{wrap['patch_filename']}"
            fetch(cache, wrap["patch_filename"], wrap["patch_hash"], lambda dest, u=url: download(u, dest))


if __name__ == "__main__":
    if len(sys.argv) != 3:
        fail("cách dùng: fetch-subprojects.py <sources.lock> <thư mục mkxp-z>")
    main(sys.argv[1], sys.argv[2])

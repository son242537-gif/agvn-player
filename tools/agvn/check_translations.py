#!/usr/bin/env python3
# Copyright (c) 2026 agvn.io - MIT License (see LICENSE).
"""Checks the app's translations against its Vietnamese strings, before a build.

    python3 tools/agvn/check_translations.py [en th in tl zh-rCN]

For each language: app/src/main/res/values-<lang>/*.xml (the same file names as values/) and
app/src/main/assets/agvn/game-problems-<lang>.json ("Tự sửa lỗi": {"problems": {"<id>": {"title": ..., "cause": ...}}}).
A string must exist in values/ and be translatable there, keep every format argument (%1$s, %d, %%), escape its
apostrophes (\\') and keep its text on one level (no markup the original lacks); an array keeps its number of items; a
problem keeps every {placeholder} of its title and cause. Prints what is missing (allowed: Android then shows the
Vietnamese) and fails on what is wrong.
"""
import json
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RES = os.path.join(ROOT, "app", "src", "main", "res")
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets", "agvn")
FILES = ["strings.xml", "agvn_strings.xml", "agvn_repair_strings.xml", "agvn_guide_strings.xml",
         "agvn_update_strings.xml", "arrays.xml"] + sorted(
    f for f in os.listdir(os.path.join(RES, "values")) if f.startswith("agvn_ui") and f.endswith("_strings.xml"))
LANGS = ["en", "th", "in", "tl", "zh-rCN"]
FORMAT = re.compile(r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[sdfxXc%]")
PLACEHOLDER = re.compile(r"\{[A-Za-z0-9_]+\}")


def text(el):
    return "".join(el.itertext()) if el is not None else ""


def raw(el):
    return ET.tostring(el, encoding="unicode")


def load(path):
    entries = {}
    root = ET.parse(path).getroot()
    for el in root:
        name = el.get("name")
        if el.tag == "string":
            entries[("string", name)] = (el, el.get("translatable") != "false")
        elif el.tag in ("string-array", "plurals"):
            entries[(el.tag, name)] = (el, el.get("translatable") != "false")
    return entries


def formats(s):
    return sorted(f for f in FORMAT.findall(s) if f != "%%") + (["%%"] if "%%" in s else [])


def unescaped_apostrophe(s):
    return re.search(r"(?<!\\)'", s) is not None and not (s.startswith('"') and s.endswith('"'))


def check_xml(lang, errors, missing):
    for name in FILES:
        base_path = os.path.join(RES, "values", name)
        path = os.path.join(RES, "values-" + lang, name)
        base = load(base_path)
        if not os.path.isfile(path):
            missing.append("%s: no %s" % (lang, name))
            continue
        try:
            ours = load(path)
        except ET.ParseError as e:
            errors.append("%s/%s: not XML: %s" % (lang, name, e))
            continue
        for key, (el, _) in ours.items():
            where = "%s/%s %s" % (lang, name, key[1])
            if key not in base:
                errors.append(where + ": not in values/" + name)
                continue
            b, translatable = base[key]
            if not translatable:
                errors.append(where + ": translatable=\"false\" in values/")
                continue
            if key[0] == "string":
                if formats(text(el)) != formats(text(b)):
                    errors.append("%s: format %s, values/ has %s" % (where, formats(text(el)), formats(text(b))))
                if unescaped_apostrophe(text(el)):
                    errors.append(where + ": an apostrophe without \\")
                if len(list(el)) != len(list(b)):
                    errors.append(where + ": markup differs from values/")
            else:
                ti, bi = el.findall("item"), b.findall("item")
                if key[0] == "string-array" and len(ti) != len(bi):
                    errors.append("%s: %d items, values/ has %d" % (where, len(ti), len(bi)))
                for i, item in enumerate(ti):
                    if i < len(bi) and formats(text(item)) != formats(text(bi[i])):
                        errors.append("%s item %d: format differs" % (where, i))
                    if unescaped_apostrophe(text(item)):
                        errors.append("%s item %d: an apostrophe without \\" % (where, i))
        left = [k[1] for k, (_, t) in base.items() if t and k not in ours]
        if left:
            missing.append("%s/%s: %d untranslated (%s%s)" % (lang, name, len(left), ", ".join(left[:5]),
                                                              "…" if len(left) > 5 else ""))


def check_problems(lang, errors, missing):
    with open(os.path.join(ASSETS, "game-problems.json"), encoding="utf-8") as f:
        base = {p["id"]: p for p in json.load(f)["problems"]}
    path = os.path.join(ASSETS, "game-problems-%s.json" % lang.split("-")[0])
    if not os.path.isfile(path):
        missing.append("%s: no %s" % (lang, os.path.basename(path)))
        return
    try:
        with open(path, encoding="utf-8") as f:
            ours = json.load(f)["problems"]
    except (ValueError, KeyError) as e:
        errors.append("%s: %s: %s" % (lang, os.path.basename(path), e))
        return
    for pid, p in ours.items():
        if pid not in base:
            errors.append("%s problem %s: not in game-problems.json" % (lang, pid))
            continue
        for field in ("title", "cause"):
            if field in p and sorted(PLACEHOLDER.findall(p[field])) != sorted(PLACEHOLDER.findall(base[pid][field])):
                errors.append("%s problem %s %s: placeholders %s, the original has %s" % (
                    lang, pid, field, sorted(PLACEHOLDER.findall(p[field])),
                    sorted(PLACEHOLDER.findall(base[pid][field]))))
    left = [pid for pid in base if pid not in ours]
    if left:
        missing.append("%s problems: %d untranslated (%s…)" % (lang, len(left), ", ".join(left[:5])))


def main(langs):
    errors, missing = [], []
    for lang in langs:
        check_xml(lang, errors, missing)
        check_problems(lang, errors, missing)
    for m in missing:
        print("missing: " + m)
    for e in errors:
        print("ERROR: " + e)
    print("%d errors" % len(errors))
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:] or LANGS))

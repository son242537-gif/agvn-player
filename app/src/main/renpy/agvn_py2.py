# Copyright (c) 2026 agvn.io - MIT License (see LICENSE).
#
# A Ren'Py 6 or 7 game (Python 2) on AGVN's Ren'Py 8.5.3 ("Chạy nhẹ" on trial, main.py with AGVN_RENPY_PY2=1).
# Ren'Py 8 loads the game's .rpyc files and mends Python 2 syntax itself (print x, <>, 0777, `x`: renpy.compat.fixes),
# but what Python 2 did otherwise broke such games (Ren'Py 7.3.5's own tutorial and test games, 07/11/2026):
#   - its builtins: file(), unicode(), xrange()... ("NameError: name 'file' is not defined");
#   - "/" of two whole numbers gave a whole number; in Python 3 it gives a float, and Ren'Py reads a float position as a
#     share of the screen, so "xpos 1280 / 2" put a button 640 screens to the right;
#   - dict.iteritems() and the like, on the dicts the game's code makes (Ren'Py's RevertableDict);
#   - text: a line read from a file and then .decode("utf-8")-d, the way Python 2 games read their own files ("'str'
#     object has no attribute 'decode'"). The game's code (not Ren'Py's) gets Python 2's open() and file(): text that
#     takes bytes to write too, and .decode() of text gives the text.
# Code that asks for Python 3's division itself ("from __future__ import division") keeps it.

import __future__
import ast
import builtins
import copy
import functools
import os
import sys

import agvn_py2_ways


def py2_div(a, b):
    """Python 2's "/": two whole numbers (bools too) give a whole number, rounded down; anything else as Python 3."""
    if isinstance(a, int) and isinstance(b, int):
        return a // b
    return a / b


def py2_idiv_item(container, key, b):
    """container[key] /= b of Python 2."""
    container[key] = py2_div(container[key], b)


def py2_idiv_attr(obj, name, b):
    """obj.name /= b of Python 2, obj worked out once."""
    setattr(obj, name, py2_div(getattr(obj, name), b))


def py2_mode(mode):
    """Python 2's "U" (universal newlines), which Python 3.12 refuses: text mode reads so anyway."""
    return mode.replace("U", "") or "r"


def py2_decode(obj, *args, **kwargs):
    """obj.decode(...) of Python 2: text (unicode in Python 2) stays as it is, bytes decode as in Python 3."""
    if isinstance(obj, str):
        return obj
    return obj.decode(*args, **kwargs)


class Py2File:
    """A text file as Python 2's open() gave one to a game: it also takes bytes to write (UTF-8)."""

    def __init__(self, f):
        self.f = f

    def write(self, data):
        if isinstance(data, (bytes, bytearray)):
            data = bytes(data).decode("utf-8", "surrogateescape")
        return self.f.write(data)

    def writelines(self, lines):
        for line in lines:
            self.write(line)

    def __iter__(self):
        return iter(self.f)

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        self.f.close()
        return False

    def __getattr__(self, name):
        return getattr(self.f, name)


py3_open = builtins.open


def game_code(depth=2):
    """True when the caller's caller is the game's script (game/*.rpy), not Ren'Py or Python."""
    try:
        return sys._getframe(depth).f_code.co_filename.endswith((".rpy", ".rpym", "_ren.py"))
    except ValueError:  # called with no Python code above it
        return False


def py2_open(file, mode="r", buffering=-1, *args, **kwargs):
    """Python 2's open() for the game's own code; Ren'Py's and Python's code get Python 3's."""
    if not game_code():
        return py3_open(file, mode, buffering, *args, **kwargs)
    mode = py2_mode(mode)  # open(path, "rU") of a Python 2 game: "invalid mode: 'rU'"
    if "b" in mode or args or kwargs:
        return py3_open(file, mode, buffering, *args, **kwargs)
    return Py2File(py3_open(file, mode, buffering, encoding="utf-8", errors="surrogateescape"))


def py2_file(name, mode="r", buffering=-1):
    mode = py2_mode(mode)
    if "b" in mode:
        return py3_open(name, mode, buffering)
    return Py2File(py3_open(name, mode, buffering, encoding="utf-8", errors="surrogateescape"))


def py2_execfile(path, globals=None, locals=None):
    with open(path, "rb") as f:
        exec(compile(f.read(), path, "exec"), globals if globals is not None else {}, locals)


BUILTINS = {
    "__agvn_div__": py2_div,
    "__agvn_idiv_item__": py2_idiv_item,
    "__agvn_idiv_attr__": py2_idiv_attr,
    "__agvn_decode__": py2_decode,
    "file": py2_file,
    "unicode": str,
    "basestring": str,
    "long": int,
    "xrange": range,
    "unichr": chr,
    "raw_input": input,
    "reduce": functools.reduce,
    "intern": sys.intern,
    "cmp": lambda a, b: (a > b) - (a < b),
    "execfile": py2_execfile,
}


class Py2Decode(ast.NodeTransformer):
    """x.decode(...) -> __agvn_decode__(x, ...)."""

    def visit_Call(self, node):
        self.generic_visit(node)
        func = node.func
        if not isinstance(func, ast.Attribute) or func.attr != "decode":
            return node
        call = ast.Call(func=ast.Name(id="__agvn_decode__", ctx=ast.Load()), args=[func.value] + node.args,
                        keywords=node.keywords)
        return ast.copy_location(call, node)


class Py2Division(ast.NodeTransformer):
    """
    a / b -> __agvn_div__(a, b); a /= b -> a = __agvn_div__(a, b); a[i] /= b and a.x /= b through helpers that work
    the container out once (stats["hp"] /= 2 gave 2.5 where Python 2 gave 2; a slice target stays as it is).
    """

    def visit_BinOp(self, node):
        self.generic_visit(node)
        if not isinstance(node.op, ast.Div):
            return node
        call = ast.Call(func=ast.Name(id="__agvn_div__", ctx=ast.Load()), args=[node.left, node.right], keywords=[])
        return ast.copy_location(call, node)

    def visit_AugAssign(self, node):
        self.generic_visit(node)
        target = node.target
        if not isinstance(node.op, ast.Div):
            return node
        if isinstance(target, ast.Name):
            load = ast.Name(id=target.id, ctx=ast.Load())
            call = ast.Call(func=ast.Name(id="__agvn_div__", ctx=ast.Load()), args=[load, node.value], keywords=[])
            return ast.copy_location(ast.Assign(targets=[target], value=call), node)
        if isinstance(target, ast.Attribute):
            args = [target.value, ast.Constant(value=target.attr), node.value]
            name = "__agvn_idiv_attr__"
        elif isinstance(target, ast.Subscript) and not isinstance(target.slice, ast.Slice):
            args = [target.value, target.slice, node.value]
            name = "__agvn_idiv_item__"
        else:
            return node
        call = ast.Call(func=ast.Name(id=name, ctx=ast.Load()), args=args, keywords=[])
        return ast.copy_location(ast.Expr(value=call), node)


def asks_true_division(tree):
    return any(isinstance(n, ast.ImportFrom) and n.module == "__future__" and any(a.name == "division" for a in n.names)
               for n in ast.walk(tree))


class Py2Wrap:
    """Ren'Py's wrap_node (the game's dicts and lists made revertable), then Python 2's ways, .decode() and division."""

    def __init__(self, wrap):
        self.wrap = wrap

    def visit(self, tree):
        tree = Py2Decode().visit(agvn_py2_ways.Py2Ways().visit(self.wrap.visit(tree)))
        # a file that begins with "rpy python 3" (Ren'Py 7.4 to 7.6) asked for Python 3's division: Ren'Py keeps that
        # in the flags of the py_compile call this runs in
        try:
            flags = sys._getframe(1).f_locals.get("flags", 0)
        except ValueError:
            flags = 0
        if not asks_true_division(tree) and not flags & __future__.division.compiler_flag:
            tree = Py2Division().visit(tree)
        return ast.fix_missing_locations(tree)


def patch_analysis(module):
    """Screens stay as fast: Ren'Py may work out once what calls to these give (they depend on their arguments only)."""
    for name in ("__agvn_div__", "__agvn_decode__"):
        getattr(module, "pure_functions", set()).add(name)


def patch_python(module):
    wrap = getattr(module, "wrap_node", None)
    if wrap is None or isinstance(wrap, Py2Wrap):
        return
    module.wrap_node = Py2Wrap(wrap)
    blacklist = getattr(sys.modules.get("renpy"), "name_blacklist", None)
    if isinstance(blacklist, set):
        blacklist.add("renpy.python.wrap_node")  # kept through a reload, which restores Ren'Py's backup


DICT_METHODS = {"iteritems": lambda self: iter(self.items()), "iterkeys": lambda self: iter(self.keys()),
                "itervalues": lambda self: iter(self.values())}


# Bump when what this file does to the game's code changes: Ren'Py keeps compiled blocks in game/cache, by the
# block's text, and would go on running the ones compiled before (or by another Ren'Py 8 that ran this folder).
RULES = 3


def patch_script(module):
    """The trial's own bytecode cache (a string: Ren'Py's module backup takes it)."""
    module.BYTECODE_FILE = "cache/bytecode-agvn-py2-%d.rpyb" % RULES


def patch_revertable(module):
    cls = getattr(module, "RevertableDict", None)
    for name, method in DICT_METHODS.items():
        if cls is not None and not hasattr(cls, name):
            setattr(cls, name, method)


def install(hooks):
    """Python 2's builtins now; Ren'Py's modules as they are imported, through main.py's ModuleHooks ({name: fn})."""
    for name, value in BUILTINS.items():
        if not hasattr(builtins, name):
            setattr(builtins, name, value)
    builtins.open = py2_open
    agvn_py2_ways.install()
    hooks["renpy.python"] = patch_python
    hooks["renpy.revertable"] = patch_revertable
    hooks["renpy.script"] = patch_script
    hooks["renpy.pyanalysis"] = patch_analysis
    print("AGVN: Ren'Py 6/7 game (Python 2) on Ren'Py 8, on trial: Python 2 builtins, division, text and dict methods;"
          " the app keeps its saves, files copied from the game's: " + os.environ.get("AGVN_RENPY_PY2_SAVES", "?"))

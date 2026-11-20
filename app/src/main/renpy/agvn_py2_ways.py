# Copyright (c) 2026 agvn.io - MIT License (see LICENSE).
#
# More of Python 2's ways for a Ren'Py 6 or 7 game on trial (agvn_py2.py runs this on the game's code, after Ren'Py's
# own wrap_node). What games of that time wrote and Python 3 refuses (audit of 20/11/2026, section G):
#   - d.keys()[0], d.items() + [...], map()/filter()/zip()/range() used as lists: these gave lists;
#   - d.iteritems(), d.has_key(k) on a plain dict (**kwargs, json.load), it.next() on any iterator;
#   - sorted(xs, cmp=f) and xs.sort(cmp=f);
#   - classes with __nonzero__, __div__, __unicode__, next() or __cmp__ and none of Python 3's names for them;
#   - modules by their old names: cPickle, StringIO, cStringIO, itertools.izip..., string.letters...
# Each helper gives back what Python 3 has when the value is not one of Python 2's, so Python 3 code runs as before.

import ast
import builtins
import functools
import io
import itertools
import pickle
import string
import sys
import types

LAZY = (type({}.keys()), type({}.values()), type({}.items()), map, filter, zip, range)


def py2_list(value):
    """A list where Python 2 gave one: a dict's keys(), values(), items(), and map, filter, zip, range."""
    return list(value) if isinstance(value, LAZY) else value


def py2_has_key(d, key):
    return key in d


DICT_WAYS = {"iteritems": lambda d: iter(d.items()), "iterkeys": lambda d: iter(d.keys()),
             "itervalues": lambda d: iter(d.values()), "has_key": None}


def py2_method(obj, name):
    """obj.<name> of Python 2 where Python 3's object has none: a dict's iter*() and has_key(), an iterator's next()."""
    try:
        return getattr(obj, name)
    except AttributeError:
        if name == "next" and hasattr(obj, "__next__"):
            return obj.__next__
        if name in DICT_WAYS and hasattr(obj, "keys"):
            return functools.partial(py2_has_key if name == "has_key" else DICT_WAYS[name], obj)
        raise


def py2_cmp_class(cls):
    """A class with __cmp__ only gets Python 3's comparisons from it; its hash stays the one it had."""
    def make(test):
        def compare(self, other):
            result = self.__cmp__(other)
            return NotImplemented if result is NotImplemented else test(result)
        return compare
    tests = {"__lt__": lambda r: r < 0, "__le__": lambda r: r <= 0, "__gt__": lambda r: r > 0, "__ge__": lambda r: r >= 0,
             "__eq__": lambda r: r == 0, "__ne__": lambda r: r != 0}
    kept = cls.__dict__.get("__hash__", object.__hash__)
    for name, test in tests.items():
        if name not in cls.__dict__:
            setattr(cls, name, make(test))
    cls.__hash__ = kept if kept is not None else object.__hash__
    return cls


ALIASES = {"__nonzero__": "__bool__", "__div__": "__truediv__", "__rdiv__": "__rtruediv__", "__idiv__": "__itruediv__",
           "__unicode__": "__str__", "next": "__next__"}
LISTS = {"map", "filter", "zip", "range"}
VIEWS = {"keys", "values", "items"}
METHODS = {"iteritems", "iterkeys", "itervalues", "has_key", "next"}


def call(name, args):
    return ast.Call(func=ast.Name(id=name, ctx=ast.Load()), args=args, keywords=[])


class Py2Ways(ast.NodeTransformer):
    def visit_Call(self, node):
        self.generic_visit(node)
        func = node.func
        for keyword in node.keywords:  # sorted(xs, cmp=f), xs.sort(cmp=f)
            sorts = isinstance(func, ast.Name) and func.id == "sorted" or isinstance(func, ast.Attribute) and func.attr == "sort"
            if keyword.arg == "cmp" and sorts:
                keyword.arg = "key"
                keyword.value = call("__agvn_cmp_key__", [keyword.value])
        if isinstance(func, ast.Attribute) and func.attr in METHODS:
            node.func = ast.copy_location(call("__agvn_method__", [func.value, ast.Constant(value=func.attr)]), func)
            return node
        lazy = isinstance(func, ast.Name) and func.id in LISTS \
            or isinstance(func, ast.Attribute) and func.attr in VIEWS and not node.args and not node.keywords
        return ast.copy_location(call("__agvn_list__", [node]), node) if lazy else node

    def visit_ClassDef(self, node):
        self.generic_visit(node)
        names = set()
        for item in node.body:
            if isinstance(item, ast.FunctionDef):
                names.add(item.name)
            elif isinstance(item, ast.Assign):
                names.update(t.id for t in item.targets if isinstance(t, ast.Name))
        for old, new in ALIASES.items():
            if old in names and new not in names and (old != "next" or "__iter__" in names):
                node.body.append(ast.Assign(targets=[ast.Name(id=new, ctx=ast.Store())],
                                            value=ast.Name(id=old, ctx=ast.Load())))
        if "__cmp__" in names:
            node.decorator_list.append(ast.Name(id="__agvn_cmp_class__", ctx=ast.Load()))
        return node


BUILTINS = {"__agvn_list__": py2_list, "__agvn_method__": py2_method, "__agvn_cmp_class__": py2_cmp_class,
            "__agvn_cmp_key__": functools.cmp_to_key}
STRING = {"letters": string.ascii_letters, "lowercase": string.ascii_lowercase, "uppercase": string.ascii_uppercase,
          "join": lambda words, sep=" ": sep.join(words), "split": lambda s, *a: s.split(*a),
          "replace": lambda s, *a: s.replace(*a), "lower": lambda s: s.lower(), "upper": lambda s: s.upper(),
          "strip": lambda s, *a: s.strip(*a), "find": lambda s, *a: s.find(*a), "atoi": int, "atof": float}
ITERTOOLS = {"izip": zip, "imap": map, "ifilter": filter, "izip_longest": itertools.zip_longest,
             "ifilterfalse": itertools.filterfalse}


def old_module(name, **names):
    module = types.ModuleType(name)
    module.__dict__.update(names)
    return module


def install():
    """The helpers as builtins (the game's compiled code names them) and Python 2's module names."""
    for name, value in BUILTINS.items():
        setattr(builtins, name, value)
    for module, names in ((string, STRING), (itertools, ITERTOOLS)):
        for name, value in names.items():
            if not hasattr(module, name):
                setattr(module, name, value)
    sys.modules.setdefault("cPickle", pickle)
    sys.modules.setdefault("StringIO", old_module("StringIO", StringIO=io.StringIO))
    sys.modules.setdefault("cStringIO", old_module("cStringIO", StringIO=io.StringIO))

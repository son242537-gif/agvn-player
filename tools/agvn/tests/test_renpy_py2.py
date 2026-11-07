"""Tests for app/src/main/renpy/agvn_py2.py: a Ren'Py 6/7 game (Python 2) on AGVN's Ren'Py 8, on trial.

Run: python -m unittest discover -s tools/agvn/tests
With Ren'Py's SDKs at hand, scripts/agvn/renpy/run-py2-test.sh runs Ren'Py 7.3.5's own games and a Python 2 test game
on Ren'Py 8.5.3 with it.
"""
import ast
import builtins
import os
import subprocess
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
RENPY_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(HERE))), "app", "src", "main", "renpy")
sys.path.insert(0, RENPY_DIR)
import agvn_py2  # noqa: E402


class FakeWrap:
    def visit(self, tree):
        return tree


def run_game_code(source, filename="game/script.rpy"):
    """Compiles source as Ren'Py compiles the game's code with agvn_py2, and runs it."""
    tree = agvn_py2.Py2Wrap(FakeWrap()).visit(ast.parse(source, filename))
    scope = {"__builtins__": dict(vars(builtins), **agvn_py2.BUILTINS, open=agvn_py2.py2_open)}
    exec(compile(tree, filename, "exec"), scope)
    return scope


class Py2Test(unittest.TestCase):
    def test_whole_numbers_divide_as_in_python_2(self):
        s = run_game_code("a = 1280 / 2\nb = 7 / 2\nc = 7 / 2.0\nd = 10\nd /= 4\nclass P: pass\np = P()\np.x = 9\np.x /= 2\n")
        self.assertEqual((640, 3, 3.5, 2, 4), (s["a"], s["b"], s["c"], s["d"], s["p"].x))
        self.assertIs(type(s["a"]), int)  # Ren'Py reads a float position as a share of the screen

    def test_code_that_asks_for_true_division_keeps_it(self):
        s = run_game_code("from __future__ import division\na = 7 / 2\n")
        self.assertEqual(3.5, s["a"])

    def test_text_decodes_to_itself_and_bytes_as_bytes(self):
        s = run_game_code("a = u'ắ'.decode('utf-8')\nb = b'\\xe1\\xba\\xaf'.decode('utf-8')\n")
        self.assertEqual(("ắ", "ắ"), (s["a"], s["b"]))

    def test_python_2_builtins(self):
        s = run_game_code("x = list(xrange(3))\nu = unicode(5)\nl = long('7')\nok = isinstance(u, basestring)\n"
                          "r = reduce(lambda a, b: a + b, [1, 2, 3])\nc = cmp(1, 2)\n")
        self.assertEqual(([0, 1, 2], "5", 7, True, 6, -1), (s["x"], s["u"], s["l"], s["ok"], s["r"], s["c"]))

    def test_the_games_files_read_and_write_as_in_python_2(self):
        with tempfile.TemporaryDirectory() as d:
            path = os.path.join(d, "notes.txt")
            run_game_code("f = open(%r, 'w')\nf.write(u'ắ\\n'.encode('utf-8'))\nf.write(u'b\\n')\nf.close()\n" % path)
            s = run_game_code("lines = [l.decode('utf-8').rstrip() for l in file(%r, 'r')]\n" % path)
            self.assertEqual(["ắ", "b"], s["lines"])

    def test_renpys_own_code_keeps_python_3s_open(self):
        with tempfile.TemporaryDirectory() as d:
            path = os.path.join(d, "x.txt")
            scope = {"open": agvn_py2.py2_open, "path": path}
            exec(compile("f = open(file=path, mode='w')", "renpy/loader.py", "exec"), scope)
            self.assertNotIsInstance(scope["f"], agvn_py2.Py2File)
            scope["f"].close()

    def test_revertable_dicts_get_python_2s_methods(self):
        class Module:
            class RevertableDict(dict):
                pass
        agvn_py2.patch_revertable(Module)
        d = Module.RevertableDict(a=1)
        self.assertEqual([("a", 1)], list(d.iteritems()))
        self.assertEqual(["a"], list(d.iterkeys()))
        self.assertEqual([1], list(d.itervalues()))

    def test_install_hooks_renpys_modules_and_keeps_wrap_node_through_a_reload(self):
        code = ("import sys, types, agvn_py2\nhooks = {}\nagvn_py2.install(hooks)\n"
                "renpy = types.ModuleType('renpy'); renpy.name_blacklist = set(); sys.modules['renpy'] = renpy\n"
                "python = types.SimpleNamespace(wrap_node=object())\nhooks['renpy.python'](python)\n"
                "print(sorted(hooks), type(python.wrap_node).__name__, sorted(renpy.name_blacklist), file is not None)\n")
        out = subprocess.run([sys.executable, "-I", "-c", "import sys; sys.path.insert(0, %r)\n" % RENPY_DIR + code],
                             capture_output=True, text=True, check=True).stdout.splitlines()[-1]
        self.assertEqual("['renpy.pyanalysis', 'renpy.python', 'renpy.revertable'] Py2Wrap ['renpy.python.wrap_node'] True",
                         out)


if __name__ == "__main__":
    unittest.main()

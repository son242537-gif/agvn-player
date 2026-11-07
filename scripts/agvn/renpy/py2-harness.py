# Copyright (c) 2026 agvn.io - MIT License (see LICENSE).
# For run-py2-test.sh: runs Ren'Py 8.5.3's renpy.py as AGVN's main.py does with AGVN_RENPY_PY2=1 (agvn_py2.py
# hooked into Ren'Py's modules as they are imported), with the command line given ("<game> lint").
import importlib.abc, importlib.machinery, os, sys
sys.path.insert(0, os.environ["AGVN_RENPY_DIR"])
import agvn_py2

class ModuleHooks(importlib.abc.MetaPathFinder):
    HOOKS = {}
    def find_spec(self, name, path, target=None):
        hook = self.HOOKS.get(name)
        if hook is None:
            return None
        spec = importlib.machinery.PathFinder.find_spec(name, path)
        run = spec.loader.exec_module
        def exec_module(module):
            run(module)
            hook(module)
        spec.loader.exec_module = exec_module
        return spec

agvn_py2.install(ModuleHooks.HOOKS)
sys.meta_path.insert(0, ModuleHooks())
renpy_py = os.environ["RENPY_PY"]
sys.argv = [renpy_py] + sys.argv[1:]
__file__ = renpy_py
exec(compile(open(renpy_py, "rb").read(), renpy_py, "exec"))

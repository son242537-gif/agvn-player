# Copyright (c) 2026 agvn.io - MIT License (see LICENSE).
# For run-syntax-test.sh: Python 2 syntax a Ren'Py 7 game keeps in its .rpy files, in blocks that also hold what
# Ren'Py 8's own fixes broke (a float, a hex number beside an octal one). Hero Harem Guild's line is the first.
init python:
    class Clock(object):
        counter = 12.3456

    def numfloat(clock):
        return (str(clock.counter)+"00").split(".")[1][:02]

    def half():
        speed = 0.5
        mask = 0x1F
        mode = 0755
        return speed, mask, mode

    def caught():
        try:
            raise ValueError("no")
        except ValueError, e:
            return ur"\d" + str(e)
        except (KeyError, IndexError):
            return "other"

    def more():
        try:
            raise KeyError("k")
        except KeyError,e:
            pass
        try:
            raise ValueError("v")
        except ValueError: a, b = 1, 2
        try:
            import nothing_here
        except ImportError: import json, os
        return a, b, os.sep

    def older():
        big = 10L + 0xFFL
        if big: print "quiet", big
        scope = {}
        exec "made = 7" in scope
        code = "also = 8"
        exec code in scope
        return big, scope["made"], scope["also"]

label start:
    $ results = (numfloat(Clock()), half(), caught(), more(), older())
    $ renpy.write_log("AGVN-SYNTAX RESULTS %r", results)
    return

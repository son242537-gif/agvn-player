# Copyright (c) 2026 agvn.io - MIT License (see LICENSE).
# A Ren'Py 7 game of Python 2's ways, for run-py2-test.sh: compiled by Ren'Py 7.3.5, run by Ren'Py 8.5.3.
define config.name = "py2game"
define config.save_directory = "py2game-1"

init python:
    half = 1280 / 2
    third = 7 / 2
    mixed = 7 / 2.0
    count = 10
    count /= 4
    d = {"a": 1, "b": 2}
    pairs = sorted(k for k, v in d.iteritems())
    keys = sorted(d.iterkeys())
    u = unicode("abc")
    ok_str = isinstance(u, basestring)
    big = long(5)
    xs = list(xrange(3))
    print "AGVN-PY2 print statement works"
    try:
        f = file
        has_file = True
    except NameError:
        has_file = False
    results = [half == 640 and type(half) is int, third == 3, mixed == 3.5, count == 2, pairs == ["a", "b"],
               keys == ["a", "b"], ok_str, big == 5, xs == [0, 1, 2], has_file]
    renpy.write_log("AGVN-PY2 RESULTS %r" % (results,))

screen center_box():
    frame:
        xpos config.screen_width / 2
        ypos 720 / 4
        text "[half]"

label start:
    show screen center_box
    "half is [half]"
    return

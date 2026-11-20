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
    stats = {"hp": 5}
    stats["hp"] /= 2
    class Box(object):
        v = 7
    box = Box()
    box.v /= 2
    import os
    universal = open(os.path.join(config.gamedir, "script.rpyc"), "rU")
    universal.close()
    first = d.keys()[0] in ("a", "b")
    joined = sorted(d.items() + [("c", 3)])[-1] == ("c", 3)
    doubled = map(lambda x: x * 2, [1, 2])
    doubled.append(6)
    zipped = zip([1, 2], "ab")[1] == (2, "b")
    plain = dict(a=1)
    def kw(**kwargs):
        return kwargs.has_key("a") and [k for k, v in kwargs.iteritems()] == ["a"]
    it = iter([4, 5])
    nexted = it.next() == 4
    by_cmp = sorted([3, 1, 2], cmp=lambda a, b: cmp(b, a)) == [3, 2, 1]
    nums = [3, 1, 2]
    nums.sort(cmp=lambda a, b: cmp(a, b))
    class Old(object):
        def __init__(self, v):
            self.v = v
        def __nonzero__(self):
            return self.v != 0
        def __div__(self, other):
            return Old(self.v / other)
        def __cmp__(self, other):
            return cmp(self.v, other.v)
    classes = (not Old(0)) and bool(Old(1)) and (Old(9) / 2).v == 4 and Old(1) < Old(2) and Old(2) == Old(2) \
        and max([Old(1), Old(3)]).v == 3 and len({Old(1): 1}) == 1
    class Counter(object):
        def __init__(self):
            self.n = 0
        def __iter__(self):
            return self
        def next(self):
            self.n += 1
            if self.n > 2:
                raise StopIteration
            return self.n
    counted = list(Counter()) == [1, 2]
    import cPickle, StringIO, itertools, string
    modules = cPickle.loads(cPickle.dumps([1])) == [1] and StringIO.StringIO("x").read() == "x" \
        and list(itertools.izip([1], [2])) == [(1, 2)] and string.join(["a", "b"], "-") == "a-b" \
        and "a" in string.letters
    ranged = range(3) + [3] == [0, 1, 2, 3]
    ways = [first, joined, doubled == [2, 4, 6], zipped, kw(a=1), nexted, by_cmp, nums == [1, 2, 3], classes, counted,
            modules, ranged]
    renpy.write_log("AGVN-PY2 WAYS %r" % (ways,))
    results = [half == 640 and type(half) is int, third == 3, mixed == 3.5, count == 2, pairs == ["a", "b"],
               keys == ["a", "b"], ok_str, big == 5, xs == [0, 1, 2], has_file, stats["hp"] == 2, box.v == 3]
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

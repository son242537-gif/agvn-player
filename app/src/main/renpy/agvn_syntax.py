# Copyright (c) 2026 agvn.io - MIT License (see LICENSE).
#
# Ren'Py 8's own mending of Python 2 syntax (renpy.compat.fixes, run on a Python block that does not compile), mended:
#   - fix_octal_numbers turned every number starting with "0" into "0o...": "02" became "0o2", but "0.5" became
#     "0o.5" and "0x1F" "0ox1F". A block with a Python 2 octal and a float failed again once "fixed", and Ren'Py
#     then reported the octal: Hero Harem Guild (Ren'Py 7) stopped at "Parsing the script failed ... leading zeros in
#     decimal integer literals are not permitted" for "[:02]" (0.1.97, 20/11/2026; JoiPlay's Ren'Py 8.5.0 the same).
#     Only a whole number written the Python 2 way is changed now.
#   - "except X, e:" becomes "except X as e:", and ur"..." becomes r"...", which Ren'Py does not mend.
# main.py puts install() on renpy.compat.fixes for every game: a block that compiles as it is never comes here.

import re
import token
import tokenize

OCTAL = re.compile(r"0[0-7]+$")


def fix_octal_numbers(tokens):
    """Python 2's 0777 -> 0o777; 0, 0.5, 0x1F, 0b1, 0o7, 0e0 and 0j stay as they are."""
    for t in tokens:
        if t.type == token.NUMBER and OCTAL.match(t.string):
            yield tokenize.TokenInfo(token.NUMBER, "0o" + t.string[1:], t.start, t.end, t.line)
        else:
            yield t


def fix_ur_strings(tokens):
    """ur"..." (Python 3 reads the name ur, then a string) -> r"..."."""
    held = None
    for t in tokens:
        if held is not None:
            if t.type == token.STRING and t.start == held.end:
                yield tokenize.TokenInfo(token.STRING, "r" + t.string, held.start, t.end, t.line)
                held = None
                continue
            yield held
            held = None
        if t.type == token.NAME and t.string.lower() == "ur":
            held = t
        else:
            yield t
    if held is not None:
        yield held


def fix_except(tokens):
    """except X, e: -> except X as e: (the comma outside any bracket of a line that starts with except)."""
    start, depth, is_except = True, 0, False
    for t in tokens:
        if t.type in (token.NEWLINE, tokenize.NL, token.INDENT, token.DEDENT):
            start, depth, is_except = True, 0, False
            yield t
            continue
        if t.type == tokenize.COMMENT:
            yield t
            continue
        if start:
            is_except = t.type == token.NAME and t.string == "except"
            start = False
        if t.type == token.OP:
            if t.string in "([{":
                depth += 1
            elif t.string in ")]}":
                depth -= 1
            elif t.string == "," and is_except and depth == 0:
                yield tokenize.TokenInfo(token.NAME, " as", t.start, t.end, t.line)
                is_except = False
                continue
        yield t


def fix_spaceship(tokens):
    """Ren'Py's own (a <> b -> a != b), then the two above: fix_tokens calls this one between its other fixes."""
    for t in fix_except(fix_ur_strings(tokens)):
        if t.type == token.OP and t.string == "<>":
            yield tokenize.TokenInfo(token.OP, "!=", t.start, t.end, t.line)
        else:
            yield t


def install(fixes):
    """
    On renpy.compat.fixes, as it is imported: fix_tokens looks these up by name each time it runs. Functions of this
    module only, never a lambda or a function made in another: Ren'Py backs up its modules with pickle, which takes a
    function by its module and name, and one it cannot take stops every game as it starts (main.py, 0.1.56).
    """
    fixes.fix_octal_numbers = fix_octal_numbers
    fixes.fix_spaceship = fix_spaceship

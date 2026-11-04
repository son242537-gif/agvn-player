/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.xserver.XKeycode;

/**
 * How a character typed on the Android keyboard reaches a Windows game through the X server ({@link AgvnWinTyping}):
 * a to z, 0 to 9 and space by their own keys, as a PC keyboard types them; any other character (capitals,
 * punctuation, Vietnamese letters such as ă ê ơ ư đ ạ) on a key no keyboard has ({@link #SPARE}), mapped to the
 * character's keysym for the press. Wine then sends the game the character in WM_CHAR, which a text field reads
 * (Unity's InputField and TextMeshPro's). Pure Java (JVM-testable).
 */
final class AgvnWinKeys {
    /** A keycode the X server's keys leave free (they end at 119, KEY_DEL), among the 8 to 131 it keeps keysyms for. */
    static final byte SPARE = (byte) 130;

    /** The X server, as {@link #type} uses it. */
    interface Keys {
        /** Presses and releases {@code key}. */
        void key(XKeycode key);

        /** Presses and releases {@code keycode}, mapped to {@code keysym} first. */
        void character(byte keycode, int keysym);
    }

    private AgvnWinKeys() {}

    /** Types the character {@code codePoint}. */
    static void type(int codePoint, Keys keys) {
        if (codePoint == '\n' || codePoint == '\r') {
            keys.key(XKeycode.KEY_ENTER);
            return;
        }
        if (codePoint == '\t') {
            keys.key(XKeycode.KEY_TAB);
            return;
        }
        if (codePoint < 0x20 || codePoint == 0x7F) return; // other control characters type nothing
        XKeycode own = own(codePoint);
        if (own != null) keys.key(own);
        else keys.character(SPARE, keysym(codePoint));
    }

    /** Types every character of {@code text}. */
    static void type(CharSequence text, Keys keys) {
        String s = text.toString();
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            type(cp, keys);
            i += Character.charCount(cp);
        }
    }

    static void backspace(Keys keys) {
        keys.key(XKeycode.KEY_BKSP);
    }

    /** The key of a lower-case letter, a digit or space; null for anything else. */
    static XKeycode own(int codePoint) {
        if (codePoint >= 'a' && codePoint <= 'z') return XKeycode.valueOf("KEY_" + (char) (codePoint - 'a' + 'A'));
        if (codePoint >= '0' && codePoint <= '9') return XKeycode.valueOf("KEY_" + (char) codePoint);
        return codePoint == ' ' ? XKeycode.KEY_SPACE : null;
    }

    /** X's keysym of {@code codePoint}: a Latin-1 character is its own, any other is 0x01000000 plus the code point. */
    static int keysym(int codePoint) {
        boolean latin1 = codePoint >= 0x20 && codePoint <= 0x7E || codePoint >= 0xA0 && codePoint <= 0xFF;
        return latin1 ? codePoint : 0x01000000 | codePoint;
    }

    /** How long a start {@code before} and {@code now} share, never cutting a character in two. */
    static int same(String before, String now) {
        int same = 0;
        while (same < before.length() && same < now.length() && before.charAt(same) == now.charAt(same)) same++;
        if (same > 0 && Character.isHighSurrogate(before.charAt(same - 1))) same--;
        return same;
    }

    /**
     * The field went from {@code before} to {@code now} (the keyboard typed, deleted, or changed a letter it composes,
     * as Telex turns "aw" into "ă"): a Backspace for each character gone, then the new ones.
     */
    static void changed(String before, String now, Keys keys) {
        int same = same(before, now);
        int gone = before.codePointCount(same, before.length());
        for (int i = 0; i < gone; i++) backspace(keys);
        type(now.substring(same), keys);
    }
}

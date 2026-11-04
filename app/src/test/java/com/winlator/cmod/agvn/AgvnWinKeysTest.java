/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;

import com.winlator.cmod.xserver.XKeycode;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** What a Windows game gets for what the Android keyboard types (AgvnWinTyping, Body Pay OMG, 05/11/2026). */
public class AgvnWinKeysTest {
    /** The X server's presses, written down: a key by its name, a character as "spare U+xxxx". */
    private static final class Recorder implements AgvnWinKeys.Keys {
        final List<String> got = new ArrayList<>();

        @Override
        public void key(XKeycode key) {
            got.add(key.name());
        }

        @Override
        public void character(byte keycode, int keysym) {
            got.add((keycode == AgvnWinKeys.SPARE ? "spare " : "key " + keycode + " ") + String.format("%08X", keysym));
        }
    }

    private static List<String> typed(String text) {
        Recorder r = new Recorder();
        AgvnWinKeys.type(text, r);
        return r.got;
    }

    @Test
    public void lowerCaseLettersDigitsAndSpaceUseTheirOwnKeys() {
        assertEquals(Arrays.asList("KEY_L", "KEY_O", "KEY_N", "KEY_A", "KEY_SPACE", "KEY_2", "KEY_0"), typed("lona 20"));
    }

    @Test
    public void anyOtherCharacterGoesOnTheSpareKeyWithItsKeysym() {
        // a capital is its Latin-1 keysym, so no Shift that could stay down for the next letter
        assertEquals(Arrays.asList("spare 00000053", "KEY_A", "KEY_K", "KEY_U"), typed("Saku"));
        // Vietnamese letters: 0x01000000 + their code point (ă U+0103, ư U+01B0, ạ U+1EA1, đ U+0111)
        assertEquals(Arrays.asList("spare 01000103", "spare 010001B0", "spare 01001EA1", "spare 01000111"),
                typed("ăưạđ"));
        assertEquals("é is Latin-1", Arrays.asList("spare 000000E9"), typed("é"));
        assertEquals("a character beyond the first plane", Arrays.asList("spare 0101F600"), typed("😀"));
    }

    @Test
    public void enterTabAndControlCharacters() {
        assertEquals(Arrays.asList("KEY_ENTER", "KEY_TAB"), typed("\n\t\u0007"));
    }

    @Test
    public void aComposedLetterBecomesBackspaceAndTheNewLetter() {
        Recorder r = new Recorder();
        AgvnWinKeys.changed("", "a", r); // Telex: "a", then "w" turns it into "ă"
        AgvnWinKeys.changed("a", "ă", r);
        AgvnWinKeys.changed("ă", "ăn", r);
        assertEquals(Arrays.asList("KEY_A", "KEY_BKSP", "spare 01000103", "KEY_N"), r.got);
    }

    @Test
    public void aCharacterBeyondTheFirstPlaneIsDeletedWhole() {
        Recorder r = new Recorder();
        AgvnWinKeys.changed("x😀", "x😁", r);
        assertEquals(Arrays.asList("KEY_BKSP", "spare 0101F601"), r.got);
    }
}

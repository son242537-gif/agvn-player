/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AgvnSharpScreenTest {
    @Test
    public void aFlagshipStartsSharpWhenNothingChose() {
        assertEquals(AgvnSharpScreen.SGSR, AgvnSharpScreen.startMode(0, "", DeviceTier.FLAGSHIP));
        assertEquals(AgvnSharpScreen.SGSR, AgvnSharpScreen.startMode(0, null, DeviceTier.FLAGSHIP));
    }

    @Test
    public void theOtherPhonesStartAsBefore() {
        assertEquals(0, AgvnSharpScreen.startMode(0, "", DeviceTier.TRUNG_BINH));
        assertEquals(0, AgvnSharpScreen.startMode(0, "", DeviceTier.YEU));
    }

    @Test
    public void thePlayersChoiceForAGameComesFirst() {
        assertEquals(AgvnSharpScreen.SGSR, AgvnSharpScreen.startMode(0, "", DeviceTier.YEU, "1")); // on, on a weak phone
        assertEquals(0, AgvnSharpScreen.startMode(0, "", DeviceTier.FLAGSHIP, "0"));
        assertEquals(0, AgvnSharpScreen.startMode(3, "3", DeviceTier.FLAGSHIP, "0")); // off means off
        assertEquals(3, AgvnSharpScreen.startMode(3, "", DeviceTier.YEU, "1")); // the game's own filter stays
        assertEquals(AgvnSharpScreen.SGSR, AgvnSharpScreen.startMode(0, "", DeviceTier.FLAGSHIP, ""));
        assertEquals(0, AgvnSharpScreen.startMode(0, "", DeviceTier.YEU, null));
    }

    @Test
    public void aChoiceIsKept() {
        assertEquals(3, AgvnSharpScreen.startMode(3, "", DeviceTier.FLAGSHIP)); // FSR set for the game
        assertEquals(4, AgvnSharpScreen.startMode(4, "", DeviceTier.YEU));
        assertEquals(0, AgvnSharpScreen.startMode(0, "0", DeviceTier.FLAGSHIP)); // saved "off" in the sidebar
        assertEquals(0, AgvnSharpScreen.startMode(0, "3", DeviceTier.FLAGSHIP)); // the sidebar applies its own
    }
}

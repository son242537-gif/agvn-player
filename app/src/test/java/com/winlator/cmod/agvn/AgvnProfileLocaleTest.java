/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * A KiriKiri 2.30 game of a Chinese build (27/11/2026): "locale": "zh_CN" put in agvn-profile.json after the game was
 * added was not taken, moi-truong.txt went on saying LC_ALL=ja_JP.
 */
public class AgvnProfileLocaleTest {
    private static final String JA = "ja_JP.UTF-8", ZH = "zh_CN.UTF-8";

    @Test
    public void whatTheProfileSaysIsKeptApartFromNoRecord() {
        assertEquals(AgvnProfileLocale.NONE, AgvnProfileLocale.record(null));
        assertEquals("\"\" asks for the container's", AgvnProfileLocale.CONTAINER, AgvnProfileLocale.record(" "));
        assertEquals(ZH, AgvnProfileLocale.record("zh_CN"));
        assertEquals(ZH, AgvnProfileLocale.record(ZH));
        // the LC_ALL each gives: the app's own for a profile without "locale"
        assertEquals(JA, AgvnProfileLocale.lcAll(AgvnProfileLocale.NONE, JA));
        assertEquals("", AgvnProfileLocale.lcAll(AgvnProfileLocale.CONTAINER, JA));
        assertEquals(ZH, AgvnProfileLocale.lcAll(ZH, JA));
    }

    @Test
    public void aLocaleSetLaterIsTakenAndAChoiceMadeSinceStays() {
        String none = AgvnProfileLocale.NONE;
        assertEquals("added to the profile of a KiriKiri game", ZH, AgvnProfileLocale.apply(JA, none, ZH, JA));
        assertEquals("taken out again: the app's own", JA, AgvnProfileLocale.apply(ZH, ZH, none, JA));
        assertEquals("changed", "ko_KR.UTF-8", AgvnProfileLocale.apply(ZH, ZH, "ko_KR.UTF-8", JA));
        assertEquals("\"\": the container's", "", AgvnProfileLocale.apply(JA, none, AgvnProfileLocale.CONTAINER, JA));
        assertEquals("a Doctor fix set Chinese since: it stays", ZH, AgvnProfileLocale.apply(ZH, none, JA, ""));
        assertEquals("the player took the locale off since", "", AgvnProfileLocale.apply("", ZH, "ko_KR.UTF-8", JA));
    }

    /** A game added before the record was kept: nothing a player or a fix chose is undone on a guess. */
    @Test
    public void aGameWithNoRecordIsChangedOnlyWhereNobodyChose() {
        // the profile's other file still says what the import took
        assertEquals("edited since", ZH, AgvnProfileLocale.unrecorded(JA, AgvnProfileLocale.NONE, ZH, JA));
        assertEquals("edited since, from one locale to another", ZH,
                AgvnProfileLocale.unrecorded("ko_KR.UTF-8", "ko_KR.UTF-8", ZH, JA));
        assertEquals("the same in both files: a fix's Chinese stays", ZH, AgvnProfileLocale.unrecorded(ZH, JA, JA, ""));
        // no other file: only the LC_ALL the app gave by itself gives way
        assertEquals(ZH, AgvnProfileLocale.unrecorded(JA, null, ZH, JA));
        assertEquals("a game of no Japanese engine", ZH, AgvnProfileLocale.unrecorded("", null, ZH, ""));
        assertEquals("someone's choice stays", "ko_KR.UTF-8",
                AgvnProfileLocale.unrecorded("ko_KR.UTF-8", null, ZH, JA));
    }
}

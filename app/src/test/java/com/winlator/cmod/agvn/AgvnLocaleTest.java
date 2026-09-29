package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AgvnLocaleTest {
    private static AgvnProfile profile(String locale) {
        AgvnProfile p = AgvnProfile.defaultFor("Game");
        p.locale = locale;
        return p;
    }

    @Test
    public void japaneseEnginesGetJapaneseLocale() {
        for (GameExeResolver.Engine e : new GameExeResolver.Engine[]{GameExeResolver.Engine.KIRIKIRI,
                GameExeResolver.Engine.SIGLUS, GameExeResolver.Engine.NSCRIPTER, GameExeResolver.Engine.WOLFRPG})
            assertEquals(AgvnLocale.JAPANESE, AgvnLocale.forGame(profile(null), e, "Game", "Game.exe"));
        assertNull(AgvnLocale.forGame(profile(null), GameExeResolver.Engine.UNITY, "Game", "Game.exe"));
        assertNull(AgvnLocale.forGame(null, GameExeResolver.Engine.RPGMAKER, "Viet hoa", "Game.exe"));
    }

    @Test
    public void kanaInFolderOrExeNameMeansJapanese() {
        assertEquals(AgvnLocale.JAPANESE, AgvnLocale.forGame(null, GameExeResolver.Engine.RPGMAKER, "ゆめにっき", "RPG_RT.exe"));
        assertEquals(AgvnLocale.JAPANESE, AgvnLocale.forGame(null, GameExeResolver.Engine.UNKNOWN, "Game", "ｹﾞｰﾑ.exe"));
        assertNull(AgvnLocale.forGame(null, GameExeResolver.Engine.UNKNOWN, "東方", "game.exe")); // kanji only: may be Chinese
        assertNull(AgvnLocale.forGame(null, GameExeResolver.Engine.UNKNOWN, "Tình yêu", "game.exe"));
    }

    @Test
    public void profileLocaleWins() {
        assertNull(AgvnLocale.forGame(profile(""), GameExeResolver.Engine.KIRIKIRI, "Game", "Game.exe"));
        assertEquals("zh_CN.UTF-8", AgvnLocale.forGame(profile("zh_CN"), GameExeResolver.Engine.KIRIKIRI, "Game", "g.exe"));
        assertEquals("ja_JP.UTF-8", AgvnLocale.forGame(profile("ja_JP.UTF-8"), GameExeResolver.Engine.UNITY, "Game", "g.exe"));
    }

    @Test
    public void onlyWellFormedLocalesAreValid() {
        assertTrue(AgvnLocale.isValid(null));
        assertTrue(AgvnLocale.isValid(""));
        assertTrue(AgvnLocale.isValid("ja_JP"));
        assertTrue(AgvnLocale.isValid("vi_VN.UTF-8"));
        assertFalse(AgvnLocale.isValid("ja_JP; rm -rf"));
        assertFalse(AgvnLocale.isValid("japanese"));
    }
}

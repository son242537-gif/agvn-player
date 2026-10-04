/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.winlator.cmod.core.EnvVars;

import org.junit.Test;

/**
 * The wrapper's two shader rewrites on Mali, read as libvulkan_wrapper.so reads its switches (atoi(getenv(...)) != 0
 * turns a pass off), so "Tự sửa lỗi" offers only a pass that still runs.
 */
public class AgvnWrapperPassesTest {
    private static final String CONSTANTS = AgvnWrapperPasses.CONSTANTS, CLIP = AgvnWrapperPasses.CLIP;

    @Test
    public void onlyMaliGetsItsShadersRewritten() {
        assertTrue(AgvnWrapperPasses.mali("Mali-G925-Immortalis MC12"));
        assertTrue(AgvnWrapperPasses.mali("Mali-G615 MC2"));
        assertTrue(AgvnWrapperPasses.mali("Immortalis-G720"));
        assertFalse(AgvnWrapperPasses.mali("Adreno (TM) 830"));
        assertFalse(AgvnWrapperPasses.mali(null));
    }

    @Test
    public void aCrashCreatingAShaderAlsoWhenWineCutItsName() {
        assertTrue(AgvnWrapperPasses.creatingShader("vkCreateShaderModule"));
        assertTrue("cut before the name", AgvnWrapperPasses.creatingShader(null));
        assertTrue("cut inside it", AgvnWrapperPasses.creatingShader("vkCreateShaderMo"));
        assertFalse(AgvnWrapperPasses.creatingShader("vkCreateGraphicsPipelines"));
        assertFalse(AgvnWrapperPasses.creatingShader("vkCreateShadersEXT"));
        assertFalse(AgvnWrapperPasses.creatingShader("vkAllocateMemory"));
    }

    @Test
    public void bothPassesRunUntilASwitchSaysOtherwise() {
        assertTrue(AgvnWrapperPasses.runs("", "", CONSTANTS, false));
        assertTrue(AgvnWrapperPasses.runs(null, null, CLIP, false));
        assertFalse("AGVN turns it off for a Sarek", AgvnWrapperPasses.runs("", "", CONSTANTS, true));
        assertTrue("Sarek leaves the other pass alone", AgvnWrapperPasses.runs("", "", CLIP, true));
        assertFalse(AgvnWrapperPasses.runs("", "WINEESYNC=1 " + CLIP + "=1", CLIP, false));
        assertFalse("the container's switch", AgvnWrapperPasses.runs(CONSTANTS + "=1", "", CONSTANTS, false));
        assertTrue("the game's own switch wins", AgvnWrapperPasses.runs(CONSTANTS + "=1", CONSTANTS + "=0", CONSTANTS, false));
        assertTrue("a game can turn Sarek's back on", AgvnWrapperPasses.runs("", CONSTANTS + "=0", CONSTANTS, true));
        // atoi: a number turns the pass off unless it is zero; anything else reads as 0
        assertFalse(AgvnWrapperPasses.runs("", CLIP + "=2", CLIP, false));
        assertTrue(AgvnWrapperPasses.runs("", CLIP + "=000", CLIP, false));
        assertTrue(AgvnWrapperPasses.runs("", CLIP + "=yes", CLIP, false));
        assertTrue(AgvnWrapperPasses.runs("", CLIP + "=", CLIP, false));
    }

    @Test
    public void aFixTurnsOnePassOffForThatGame() {
        String vars = AgvnWrapperPasses.off("DXVK_HUD=fps " + CONSTANTS + "=0", CONSTANTS);
        EnvVars read = new EnvVars(vars);
        assertEquals("1", read.get(CONSTANTS));
        assertEquals("fps", read.get("DXVK_HUD"));
        assertFalse(AgvnWrapperPasses.runs("", vars, CONSTANTS, false));
        assertTrue(AgvnWrapperPasses.runs("", vars, CLIP, false));
        assertEquals(CLIP + "=1", AgvnWrapperPasses.off(null, CLIP));
    }
}

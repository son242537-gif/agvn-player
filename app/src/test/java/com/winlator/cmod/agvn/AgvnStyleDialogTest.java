package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;

import com.winlator.cmod.inputcontrols.ControlElement;

import org.junit.Test;

import java.util.EnumSet;

public class AgvnStyleDialogTest {
    @Test
    public void opacitySliderSnapsToTenPercentSteps() {
        assertEquals(0, AgvnStyleDialog.opacityStep(0f));      // never fully invisible: 10% at the left end
        assertEquals(0, AgvnStyleDialog.opacityStep(0.1f));
        assertEquals(5, AgvnStyleDialog.opacityStep(0.6f));    // AGVN layouts use 0.6
        assertEquals(9, AgvnStyleDialog.opacityStep(1f));
        assertEquals(9, AgvnStyleDialog.opacityStep(3f));
        assertEquals(0.1f, AgvnStyleDialog.opacityAt(0), 1e-6);
        assertEquals(0.6f, AgvnStyleDialog.opacityAt(5), 1e-6);
        assertEquals(1f, AgvnStyleDialog.opacityAt(12), 1e-6);
        for (int step = 0; step <= 9; step++) assertEquals(step, AgvnStyleDialog.opacityStep(AgvnStyleDialog.opacityAt(step)));
    }

    @Test
    public void everyShapeIsOffered() {
        assertEquals(EnumSet.allOf(ControlElement.Shape.class), EnumSet.of(AgvnStyleDialog.SHAPES[0], AgvnStyleDialog.SHAPES));
    }
}

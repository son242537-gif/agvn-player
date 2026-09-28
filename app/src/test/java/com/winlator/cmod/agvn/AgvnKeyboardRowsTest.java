package com.winlator.cmod.agvn;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.winlator.cmod.inputcontrols.Binding;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class AgvnKeyboardRowsTest {
    @Test
    public void everyNamedCellIsARealBindingAndKeysAreUnique() {
        Set<Binding> seen = new HashSet<>();
        int count = 0;
        for (String[] row : AgvnKeyboardRows.ROWS) {
            for (String cell : row) count += check(cell, seen);
        }
        for (String cell : AgvnKeyboardRows.MOUSE) count += check(cell, seen);
        assertTrue("keyboard too small: " + count, count >= 80);
        assertTrue(seen.contains(Binding.MOUSE_LEFT_BUTTON));
        assertTrue(seen.contains(Binding.KEY_F12));
    }

    private static int check(String cell, Set<Binding> seen) {
        String[] parts = cell.split("\\|", -1);
        if (parts[0].isEmpty()) return 0;
        Binding b = AgvnKeyboardRows.binding(parts[0]);
        assertNotNull("unknown binding " + parts[0], b);
        assertTrue("duplicate " + b, seen.add(b));
        if (parts.length > 2) Float.parseFloat(parts[2]);
        return 1;
    }
}

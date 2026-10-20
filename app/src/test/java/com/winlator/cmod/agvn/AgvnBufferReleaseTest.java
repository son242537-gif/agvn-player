package com.winlator.cmod.agvn;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The X server gives a freed pixmap's and cursor's buffer back to Android: it kept them all, and The Blood of Yidhra,
 * which made cursors without end, closed the app after 5 to 7 minutes on "Drawable data initialized as null!"
 * (20/10/2026). The buffers are native, so the hooks are checked in the sources.
 */
public class AgvnBufferReleaseTest {
    private static String source(String path) throws Exception {
        return new String(Files.readAllBytes(new File("src/main/" + path).toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void aCursorGivesItsImageBackOnceFreedAndShownByNoWindow() {
        AgvnUseCount cursor = new AgvnUseCount();
        assertFalse("shown by a window", cursor.use(true));
        assertFalse("a second window", cursor.use(true));
        assertFalse("freed, still shown: X keeps it", cursor.free());
        assertFalse(cursor.use(false));
        assertTrue("the last window takes another cursor", cursor.use(false));
        assertFalse("only once", cursor.use(false));
        assertFalse(cursor.free());

        AgvnUseCount unused = new AgvnUseCount();
        assertTrue("freed and never shown: at once", unused.free());

        AgvnUseCount shown = new AgvnUseCount();
        assertFalse(shown.use(true));
        assertFalse("shown, not freed: kept", shown.use(false));
        assertTrue(shown.free());
    }

    @Test
    public void freedPixmapsAndCursorsGiveTheirBuffersBack() throws Exception {
        String drawable = source("java/com/winlator/cmod/xserver/Drawable.java");
        assertTrue(drawable.contains("public void release()") && drawable.contains("releaseBuffer(ownBuffer);"));
        assertTrue("the field a renderer checks goes to 0", drawable.contains("if (backingAHB == ownBuffer) backingAHB = 0;"));
        assertTrue(source("cpp/winlator/drawable.c").contains("Java_com_winlator_cmod_xserver_Drawable_releaseBuffer"));
        assertTrue(source("java/com/winlator/cmod/xserver/PixmapManager.java").contains("pixmap.drawable.release();"));
        assertTrue(source("java/com/winlator/cmod/xserver/CursorManager.java").contains("cursor.free();"));
        String attributes = source("java/com/winlator/cmod/xserver/WindowAttributes.java");
        assertTrue(attributes.contains("next.shownBy(true);") && attributes.contains("cursor.shownBy(false);"));
        assertTrue(source("java/com/winlator/cmod/xserver/WindowManager.java").contains("window.attributes.dropCursor();"));
        // the EGL and DisplayX renderers keep the cursor's buffer with a reference of their own
        assertTrue(source("cpp/winlator/renderer/renderer_jni.cpp").contains("AHardwareBuffer_acquire(drawable->ahb)"));
        assertTrue(source("cpp/winlator/renderer/cursor.cpp").contains("AHardwareBuffer_release(cursor->image->ahb)"));
        // a request Android gives no buffer for gets BadAlloc, not the whole app closed
        String handler = source("java/com/winlator/cmod/xserver/XClientRequestHandler.java");
        assertTrue(handler.contains("catch (Drawable.NoBuffer e)") && handler.contains("new BadAlloc().sendError(client, opcode);"));
    }
}

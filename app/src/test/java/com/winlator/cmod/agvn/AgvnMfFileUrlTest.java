/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class AgvnMfFileUrlTest {
    private static final String ASSETS = "src/main/assets/";

    @Test
    public void registersTheHandlerAsWineReadsIt() throws Exception {
        // the keys scripts/agvn/mfurl/run-test.sh writes with reg.exe, where Wine 9.0 put them
        Assume.assumeTrue("rename-over needs a POSIX file system", File.separatorChar == '/');
        File dir = Files.createTempDirectory("mfurl").toFile();
        File system = new File(dir, "system.reg"), user = new File(dir, "user.reg");
        Files.write(system.toPath(), "WINE REGISTRY Version 2\n;; All keys relative to \\\\Machine\n\n#arch=win64\n"
                .getBytes(StandardCharsets.UTF_8));
        Files.write(user.toPath(), "WINE REGISTRY Version 2\n;; All keys relative to \\\\User\\\\S-1-5-21-0-0-0-1000\n\n#arch=win64\n"
                .getBytes(StandardCharsets.UTF_8));
        AgvnMfFileUrl.register(system, user);
        String s = new String(Files.readAllBytes(system.toPath()), StandardCharsets.UTF_8);
        for (String view : new String[]{"", "Wow6432Node\\\\"}) {
            String key = "[Software\\\\Classes\\\\" + view + "CLSID\\\\{D241A047-AB6F-43EF-8C31-78A96B9D1EA2}\\\\InprocServer32]";
            int at = s.indexOf(key);
            assertTrue(view, at >= 0);
            String body = s.substring(at, s.indexOf("\n\n", at) < 0 ? s.length() : s.indexOf("\n\n", at));
            assertTrue(body, body.contains("\n@=\"C:\\\\windows\\\\system32\\\\agvn-mfurl.dll\""));
            assertTrue(body, body.contains("\n\"ThreadingModel\"=\"Both\""));
        }
        String u = new String(Files.readAllBytes(user.toPath()), StandardCharsets.UTF_8);
        assertTrue(u, u.contains("[Software\\\\Microsoft\\\\Windows Media Foundation\\\\SchemeHandlers\\\\file:]"));
        assertTrue(u, u.contains("\n\"{D241A047-AB6F-43EF-8C31-78A96B9D1EA2}\"=\"AGVN file: URL\""));
    }

    @Test
    public void theDllsAreForTheirViews() throws Exception {
        assertEquals("AMD64", 0x8664, machine(new File(ASSETS + AgvnMfFileUrl.ASSET_64)));
        assertEquals("i386", 0x14c, machine(new File(ASSETS + AgvnMfFileUrl.ASSET_32)));
    }

    @Test
    public void aDllAlreadyInPlaceIsNotWrittenAgain() throws Exception {
        File target = new File(Files.createTempDirectory("mfurl").toFile(), "system32/" + AgvnMfFileUrl.DLL);
        assertTrue(target.getParentFile().mkdirs());
        byte[] dll = {'M', 'Z', 1, 2};
        assertTrue(AgvnMfFileUrl.install(dll, target));
        assertArrayEquals(dll, Files.readAllBytes(target.toPath()));
        assertTrue(target.setLastModified(1000));
        assertTrue(AgvnMfFileUrl.install(dll, target));
        assertEquals(1000, target.lastModified());
        assertTrue(!AgvnMfFileUrl.install(new byte[0], target));
    }

    /** The PE header's machine field. */
    private static int machine(File dll) throws Exception {
        ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(dll.toPath())).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals("MZ", 0x5a4d, b.getShort(0) & 0xffff);
        int pe = b.getInt(0x3c);
        assertEquals("PE", 0x4550, b.getInt(pe));
        return b.getShort(pe + 4) & 0xffff;
    }
}

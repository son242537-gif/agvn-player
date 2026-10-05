/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

/** Wine Mono for .NET games: which exe is a .NET program, which Wine Mono a Wine asks for, and where it goes. */
public class AgvnWineMonoTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /** A PE file, PE32 (0x10b) or PE32+ (0x20b), with {@code dirs} data directories and maybe a CLR header. */
    private File exe(String name, int magic, int dirs, boolean clr) throws IOException {
        int pe = 0x80, at = magic == 0x20b ? 112 : 96, optional = at + 16 * 8;
        byte[] b = new byte[pe + 24 + optional + 40];
        b[0] = 'M';
        b[1] = 'Z';
        put(b, 0x3c, pe, 4);
        b[pe] = 'P';
        b[pe + 1] = 'E';
        put(b, pe + 4, magic == 0x20b ? 0x8664 : 0x14c, 2);
        put(b, pe + 6, 1, 2);
        put(b, pe + 20, optional, 2);
        put(b, pe + 24, magic, 2);
        put(b, pe + 24 + at - 4, dirs, 4);
        if (clr) {
            put(b, pe + 24 + at + 14 * 8, 0x2008, 4); // where a C# compiler puts it
            put(b, pe + 24 + at + 14 * 8 + 4, 0x48, 4);
        }
        File f = new File(tmp.getRoot(), name);
        Files.write(f.toPath(), b);
        return f;
    }

    private static void put(byte[] b, int at, long value, int bytes) {
        for (int i = 0; i < bytes; i++) b[at + i] = (byte) (value >> (8 * i));
    }

    private static byte[] utf16(String s) {
        return s.getBytes(StandardCharsets.UTF_16LE);
    }

    @Test
    public void aDotNetProgramHasACliHeader() throws IOException {
        assertTrue(AgvnWineMono.isDotNet(exe("YARISUTEMESUBUTA + AGVN.exe", 0x10b, 16, true)));
        assertTrue(AgvnWineMono.isDotNet(exe("Tool64.exe", 0x20b, 16, true)));
        assertFalse(AgvnWineMono.isDotNet(exe("Game.exe", 0x10b, 16, false)));
        assertFalse(AgvnWineMono.isDotNet(exe("Game64.exe", 0x20b, 16, false)));
        assertFalse("its header has no CLR entry", AgvnWineMono.isDotNet(exe("Old.exe", 0x10b, 14, true)));
        File text = tmp.newFile("readme.exe");
        Files.write(text.toPath(), "MZ but nothing after".getBytes(StandardCharsets.US_ASCII));
        assertFalse(AgvnWineMono.isDotNet(text));
        assertFalse(AgvnWineMono.isDotNet(tmp.newFile("empty.exe")));
        assertFalse(AgvnWineMono.isDotNet(new File(tmp.getRoot(), "gone.exe")));
    }

    @Test
    public void theWineMonoAWineAsksFor() throws IOException {
        // as in Proton 9.0 arm64ec's i386 mscoree.dll, where the folder name follows Wine's unix path prefix
        byte[] dll = utf16("o\0\\\\?\\unix\\\\wine-mono-9.3.1\0\0" + "01234567");
        assertEquals("9.3.1", AgvnWineMono.versionIn(dll));
        assertEquals("10.0.0", AgvnWineMono.versionIn(utf16("\\wine-mono-\0\\wine-mono-10.0.0\0")));
        assertEquals("", AgvnWineMono.versionIn("\\wine-mono-9.3.1".getBytes(StandardCharsets.US_ASCII)));
        assertEquals("", AgvnWineMono.versionIn(new byte[0]));

        File wine = tmp.newFolder("proton-9.0-arm64ec");
        assertEquals("", AgvnWineMono.wantedVersion(wine));
        File i386 = new File(wine, "lib/wine/i386-windows");
        assertTrue(i386.mkdirs());
        Files.write(new File(i386, "mscoree.dll").toPath(), dll);
        assertEquals("9.3.1", AgvnWineMono.wantedVersion(wine));
    }

    @Test
    public void itGoesWhereProtonPutsIt() throws IOException {
        File wine = tmp.newFolder("proton-9.0-arm64ec");
        File dir = AgvnWineMono.dir(wine);
        assertEquals(new File(wine, "share/wine/mono/wine-mono-9.3.1"), dir);
        assertFalse(AgvnWineMono.isInstalled(dir));
        File bin = new File(dir, "bin");
        assertTrue(bin.mkdirs());
        assertTrue(new File(bin, "libmono-2.0-x86.dll").createNewFile());
        assertFalse("the 64-bit runtime is missing", AgvnWineMono.isInstalled(dir));
        assertTrue(new File(bin, "libmono-2.0-x86_64.dll").createNewFile());
        assertTrue(AgvnWineMono.isInstalled(dir));
    }

    @Test
    public void theBuildBundlesThisArchive() throws IOException {
        String gradle = new String(Files.readAllBytes(new File("build.gradle").toPath()), StandardCharsets.UTF_8);
        assertTrue(gradle.contains("/wine-mono-" + AgvnWineMono.VERSION + "/" + AgvnWineMono.ASSET + "\""));
        File asset = new File("src/main/assets/" + AgvnWineMono.ASSET); // downloaded and checked by the build
        assumeTrue(asset.isFile());
        long bytes = 0;
        Set<String> tops = new HashSet<>();
        try (InputStream in = new XZCompressorInputStream(new BufferedInputStream(Files.newInputStream(asset.toPath())));
             TarArchiveInputStream tar = new TarArchiveInputStream(in)) {
            for (TarArchiveEntry e; (e = tar.getNextTarEntry()) != null; ) {
                tops.add(e.getName().split("/")[0]);
                bytes += e.getSize();
                assertFalse(e.getName(), e.isSymbolicLink() || e.isLink());
            }
        }
        assertEquals("one folder, the one AgvnWineMono renames", Set.of("wine-mono-" + AgvnWineMono.VERSION), tops);
        assertEquals("the progress counts these", AgvnWineMono.UNPACKED_BYTES, bytes);
    }
}

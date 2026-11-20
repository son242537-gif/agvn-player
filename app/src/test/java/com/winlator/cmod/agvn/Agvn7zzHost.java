/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 7-Zip's console program for the build machine, for the tests that unpack real archives: built by
 * "scripts/agvn/7zip/build-7zz.sh --host" into build/agvn-7zip/host/7zz, or named by the environment variable AGVN_7ZZ.
 * Without it those tests are skipped (a Windows build, a fresh checkout); the build never needs it.
 */
final class Agvn7zzHost {
    private Agvn7zzHost() {}

    /** The program, or null when this machine has none. */
    static File program() {
        List<File> tries = new ArrayList<>();
        String named = System.getenv("AGVN_7ZZ");
        if (named != null && !named.isEmpty()) tries.add(new File(named));
        // Gradle runs the tests in app/
        tries.add(new File("../build/agvn-7zip/host/7zz"));
        tries.add(new File("build/agvn-7zip/host/7zz"));
        for (File f : tries) if (f.isFile() && f.canExecute()) return f.getAbsoluteFile();
        return null;
    }

    /** Runs 7-Zip in {@code dir} ("a", "-mx1", "out.7z", "Game"...); fails the test when 7-Zip does. */
    static void run(File dir, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(program().getPath());
        command.addAll(Arrays.asList(args));
        command.add("-bso0");
        command.add("-bsp0");
        Process p = new ProcessBuilder(command).directory(dir).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(new File(dir, "7zz.log"))).start();
        p.getOutputStream().close();
        if (p.waitFor() != 0) throw new AssertionError("7zz " + String.join(" ", args) + ": " + AgvnUnpackFixtures.read(new File(dir, "7zz.log")));
    }

    /** 7-Zip reads zstd but does not write it: packed by the machine's zstd, when it has one (false: it has none). */
    static boolean zstd(File from, File to) throws InterruptedException {
        try {
            return new ProcessBuilder("zstd", "-q", "-f", "-o", to.getPath(), from.getPath()).inheritIO().start().waitFor() == 0;
        } catch (java.io.IOException e) {
            return false;
        }
    }
}

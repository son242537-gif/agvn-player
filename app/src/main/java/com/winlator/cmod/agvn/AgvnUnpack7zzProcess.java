/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs 7-Zip's console program (libagvn7zz.so, see scripts/agvn/7zip/build-7zz.sh) and waits for it: one program, or
 * two with the first one's output as the second one's input (a .tar.gz: unpack the gzip, read the TAR). Its input is
 * closed at once, so it never waits for an answer. Stopped when the player cancels, and soon after its first error:
 * nothing is kept of an archive that failed, so the rest need not be unpacked.
 *
 * The command line is never logged: it carries the archive's password. On Android only the app itself (its uid) can
 * read the arguments of its processes.
 */
final class AgvnUnpack7zzProcess {
    interface Stdout {
        /** Reads the last program's output to its end. */
        void read(InputStream in) throws IOException;
    }

    static final class Outcome {
        /** The exit code that tells most: the first program's when it failed, else the last one's. */
        int exit;
        /** Everything written as errors. */
        String errors = "";
        volatile boolean cancelled;
    }

    /** After an error line, the time its further lines get before the program is stopped. */
    private static final long ERROR_GRACE_MS = 600;

    private AgvnUnpack7zzProcess() {}

    /**
     * {@code second}: null for one program. {@code stdout}: reads the last program's output; null when it is progress
     * ({@code -bsp1}). {@code firstProgress}: the progress the first program writes among its errors ({@code -bsp2}).
     */
    static Outcome run(List<String> first, List<String> second, Stdout stdout, AgvnUnpack7zzConsole.Progress firstProgress,
                       AgvnUnpack7zzConsole.Progress lastProgress, AgvnUnpack.Listener listener) throws IOException {
        Outcome outcome = new Outcome();
        List<Process> processes = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();
        List<AgvnUnpack7zzConsole> errors = new ArrayList<>();
        try {
            for (List<String> command : second != null ? java.util.Arrays.asList(first, second) : java.util.Collections.singletonList(first)) {
                Process p = new ProcessBuilder(command).directory(new File(command.get(0)).getParentFile()).start();
                processes.add(p);
                AgvnUnpack7zzConsole err = new AgvnUnpack7zzConsole(p.getErrorStream(), processes.size() == 1 ? firstProgress : null);
                errors.add(err);
                threads.add(start(err));
            }
            processes.get(0).getOutputStream().close();
            Process last = processes.get(processes.size() - 1);
            if (second != null) threads.add(start(() -> pump(processes.get(0).getInputStream(), last.getOutputStream())));
            Thread watch = start(() -> {
                while (alive(processes)) {
                    boolean stop = listener != null && listener.cancelled();
                    if (stop) outcome.cancelled = true;
                    for (AgvnUnpack7zzConsole err : errors) stop |= err.failedFor(ERROR_GRACE_MS);
                    if (stop) for (Process p : processes) p.destroy();
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            });
            try {
                if (stdout != null) stdout.read(last.getInputStream());
                else new AgvnUnpack7zzConsole(last.getInputStream(), lastProgress).run();
            } catch (IOException ignored) {
                // stopped while its output was read: the exit code and the errors tell why
            }
            int firstExit = processes.get(0).waitFor(), lastExit = last.waitFor();
            watch.interrupt();
            for (Thread t : threads) t.join(2000);
            outcome.exit = firstExit != 0 ? firstExit : lastExit;
            StringBuilder all = new StringBuilder();
            for (AgvnUnpack7zzConsole err : errors) all.append(err.text());
            outcome.errors = all.toString();
            return outcome;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            outcome.cancelled = true;
            return outcome;
        } finally {
            for (Process p : processes) stop(p);
        }
    }

    private static Thread start(Runnable work) {
        Thread t = new Thread(work, "agvn-7zz");
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static boolean alive(List<Process> processes) {
        for (Process p : processes) if (p.isAlive()) return true;
        return false;
    }

    private static void pump(InputStream in, OutputStream out) {
        byte[] buffer = new byte[1 << 16];
        try (InputStream from = in; OutputStream to = out) {
            for (int n; (n = from.read(buffer)) > 0; ) to.write(buffer, 0, n);
        } catch (IOException ignored) {
            // one of the two ended: the other sees its pipe close
        }
    }

    private static void stop(Process p) {
        if (!p.isAlive()) return;
        p.destroy();
        try {
            if (!p.waitFor(2, TimeUnit.SECONDS)) p.destroyForcibly();
        } catch (InterruptedException e) {
            p.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }
}

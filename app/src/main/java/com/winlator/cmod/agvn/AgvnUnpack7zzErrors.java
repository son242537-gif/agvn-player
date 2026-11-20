/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.agvn.AgvnUnpackException.Reason;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What 7-Zip's error text means for the player. The wordings are 7-Zip 25.01's (UI/Console/ExtractCallbackConsole.cpp,
 * List.cpp), as captured in app/src/test/resources/agvn/7zz. Pure Java (JVM-testable).
 */
final class AgvnUnpack7zzErrors {
    /** 7-Zip's exit code when it had not enough memory. */
    static final int EXIT_NO_MEMORY = 8;

    private static final Pattern MISSING = Pattern.compile("(?m)^Missing volume : (.+)$");
    /** A file that failed with a locked archive's doubt: wrong password, or damage. */
    private static final Pattern LOCKED_FILE = Pattern.compile("(?m)^ERROR: (?:Data Error|CRC Failed) in encrypted file\\. Wrong password\\? : (.+)$");
    /** RAR5 checks the password itself. */
    private static final Pattern WRONG = Pattern.compile("(?m)^ERROR: Wrong password(?: : .*)?$");
    private static final Pattern METHOD = Pattern.compile("(?m)^ERROR: Unsupported Method : (.+)$");
    private static final Pattern BROKEN_FILE = Pattern.compile(
            "(?m)^ERROR: (?:CRC Failed|Data Error|Unexpected end of data|Unavailable data|Headers Error|Is not archive) : (.+)$");
    private static final Pattern WRITE = Pattern.compile("(?m)^(?:ERROR: )?Cannot (?:open output file|create [a-z ]+|delete [a-z ]+) : .*?([^:\\n]+)$");

    /**
     * The file is written, only its Unix permissions or owner (a TAR's, a ZIP's from Linux or macOS) could not be set:
     * a phone's shared storage has none. Counted by 7-Zip as an error (exit code 2), not by the app.
     */
    private static final Pattern HARMLESS = Pattern.compile("(?m)^ERROR: Cannot set (?:file attribute|owner) : .*\\n?");

    private AgvnUnpack7zzErrors() {}

    static boolean harmless(String line) {
        return HARMLESS.matcher(line).lookingAt();
    }

    /**
     * {@code text}: all 7-Zip wrote as errors. {@code exit}: its exit code. {@code hadPassword}: one was given.
     * {@code entries}: the archive's list when it could be read, else null. {@code set}: the archive has several files.
     */
    static AgvnUnpackException classify(String all, int exit, boolean hadPassword, List<AgvnUnpack7zzList.Entry> entries, boolean set) {
        String text = HARMLESS.matcher(all).replaceAll("");
        Reason password = hadPassword ? Reason.WRONG_PASSWORD : Reason.NEED_PASSWORD;
        Matcher m;
        if (text.contains("errno=28") || text.contains("No space left on device")) return new AgvnUnpackException(Reason.NO_SPACE);
        if ((m = MISSING.matcher(text)).find()) return new AgvnUnpackException(Reason.MISSING_PART, m.group(1).trim());
        if (text.contains("Cannot open encrypted archive. Wrong password?") || text.contains("Headers Error in encrypted archive. Wrong password?")
                || WRONG.matcher(text).find())
            return new AgvnUnpackException(password);
        if ((m = LOCKED_FILE.matcher(text)).find()) {
            String name = m.group(1);
            // 7-Zip reads in the list's order: a locked file before this one was read and checked with this password
            return lockedBefore(entries, name) ? new AgvnUnpackException(Reason.DAMAGED, name) : new AgvnUnpackException(password, name);
        }
        if ((m = METHOD.matcher(text)).find()) return new AgvnUnpackException(Reason.UNSUPPORTED_METHOD, m.group(1));
        if (text.contains("Unsupported method") || text.contains("Unsupported feature") || text.contains("Can't allocate required memory")
                || exit == EXIT_NO_MEMORY)
            return new AgvnUnpackException(Reason.UNSUPPORTED_METHOD);
        if ((m = WRITE.matcher(text)).find()) return new AgvnUnpackException(Reason.CANNOT_WRITE, m.group(1).trim());
        boolean cutShort = text.contains("Unexpected end of archive") || text.contains("Unexpected end of data")
                || text.contains("Unavailable data") || text.contains("Unavailable start of archive");
        if (cutShort && set) return new AgvnUnpackException(Reason.MISSING_PART);
        if ((m = BROKEN_FILE.matcher(text)).find()) return new AgvnUnpackException(Reason.DAMAGED, m.group(1));
        if (cutShort || text.contains("Headers Error") || text.contains("Data Error") || text.contains("CRC Error"))
            return new AgvnUnpackException(Reason.DAMAGED);
        if (text.contains("Cannot open the file as archive") || text.contains("Is not archive")) return new AgvnUnpackException(Reason.NOT_ARCHIVE);
        // killed by the phone with nothing said: most often memory, for a dictionary bigger than the phone gives
        if (text.trim().isEmpty() && exit > 128) return new AgvnUnpackException(Reason.UNSUPPORTED_METHOD);
        return new AgvnUnpackException(Reason.DAMAGED, null, 0, new java.io.IOException("7-Zip exit " + exit + ": " + firstLine(text)));
    }

    /**
     * 7-Zip said nothing went wrong: exit 0, or 1 (a warning, such as bytes after the archive's end) with no error, or
     * 2 for nothing but permissions it could not set.
     */
    static boolean ok(int exit, String text) {
        String real = HARMLESS.matcher(text).replaceAll("");
        return (exit == 0 || exit == 1 || (exit == 2 && real.length() < text.length())) && !real.contains("ERROR");
    }

    private static boolean lockedBefore(List<AgvnUnpack7zzList.Entry> entries, String name) {
        if (entries == null) return false;
        for (AgvnUnpack7zzList.Entry e : entries) {
            if (e.path.equals(name)) return false;
            if (e.encrypted && !e.dir && e.size > 0) return true;
        }
        return false;
    }

    private static String firstLine(String text) {
        for (String line : text.split("\n")) if (!line.trim().isEmpty()) return line.trim();
        return "";
    }
}

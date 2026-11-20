/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

/** Why an archive could not be unpacked, told apart so the player is told what to do about it. */
public final class AgvnUnpackException extends Exception {
    public enum Reason {
        /** The archive is locked and no password was given yet. */
        NEED_PASSWORD,
        WRONG_PASSWORD,
        /** {@link #detail}: the name of the part that is not there. */
        MISSING_PART,
        /** {@link #bytes}: how much more room is needed. */
        NO_SPACE,
        /** A broken or half-downloaded archive; {@link #detail}: the file that failed its check, or null. */
        DAMAGED,
        /** Packed in a way neither Java's readers nor 7-Zip can read, or needing more memory than the phone gives. */
        UNSUPPORTED_METHOD,
        /** Not a ZIP, 7z, RAR or TAR at all. */
        NOT_ARCHIVE,
        /** The game folder could not be written; {@link #detail}: the file or folder. */
        CANNOT_WRITE,
        CANCELLED
    }

    public final Reason reason;
    public final String detail;
    public final long bytes;

    public AgvnUnpackException(Reason reason, String detail, long bytes, Throwable cause) {
        super(reason + (detail != null ? ": " + detail : ""), cause);
        this.reason = reason;
        this.detail = detail;
        this.bytes = bytes;
    }

    public AgvnUnpackException(Reason reason, String detail) {
        this(reason, detail, 0, null);
    }

    public AgvnUnpackException(Reason reason) {
        this(reason, null, 0, null);
    }
}

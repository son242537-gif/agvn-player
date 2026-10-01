/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.util.List;

/**
 * Pauses of a second or more between game frames, and what the FPS limit and the game's CPU did meanwhile, so the logs
 * tell whether the limit kept the game waiting, the game was busy (loading, computing) or it waited on something else.
 * Pure logic: {@link AgvnFrameWatch} and {@link AgvnVsyncLimiter} feed it.
 *
 * <p>Without a limit, and under upstream's timer, every buffer goes back at once or within a frame, so the limit cannot
 * hold a game for a second. Under the vsync limit the held buffers go back one per few vsyncs: once frames stop, all
 * are back within a fraction of a second. A pause in which buffers stayed held, or the vsync thread did not run, is
 * the vsync limit's doing.
 */
final class AgvnFrameStalls {
    static final long STALL_NS = 1_000_000_000L;
    /** Held buffers are all back well within this once frames stop (3 buffers, 3 vsyncs each on 60 Hz: 150 ms). */
    static final long BACK_WITHIN_MS = 300;
    /** The game's busiest thread counts as busy from half a core, and as waiting under a tenth of one. */
    static final int BUSY_PERCENT = 50, WAITING_PERCENT = 10;

    enum Mode { NO_LIMIT, TIMER, VSYNC }

    /** One pause, for the logs. */
    static final class Stall {
        final long ms;
        final Mode mode;
        final int limit;
        /** Vsync limit: buffers still held when frames came back. */
        final int held;
        /** Vsync limit: buffers given back when the limiter stopped (no frame for 5 s, or turned off); -1 = it did not. */
        final int drained;
        /** Vsync limit: when the last buffer went back, in ms into the pause; -1 = none went back during it. */
        final long lastBackMs;
        /** Vsync limit: the longest time its thread did not run during the pause, in ms. */
        final long longestPauseMs;
        /** The game's threads from 0.5 s into the pause, busiest first; null when not measured. */
        List<AgvnGameThreads.Busy> cpu;

        Stall(long ms, Mode mode, int limit, int held, int drained, long lastBackMs, long longestPauseMs) {
            this.ms = ms;
            this.mode = mode;
            this.limit = limit;
            this.held = held;
            this.drained = drained;
            this.lastBackMs = lastBackMs;
            this.longestPauseMs = longestPauseMs;
        }

        /** True when the vsync limit may have kept the game waiting. */
        boolean limitHeld() {
            long within = Math.max(BACK_WITHIN_MS, 5000L / Math.max(1, limit)); // 4-5 frames at low limits
            return mode == Mode.VSYNC && (held > 0 || drained > 0 || lastBackMs > within || longestPauseMs > within);
        }

        /** For logcat. */
        String english() {
            String what = mode == Mode.NO_LIMIT ? "no FPS limit"
                    : "limit " + limit + " FPS, " + (mode == Mode.VSYNC ? "vsync pacing" : "timer");
            StringBuilder s = new StringBuilder("frame stall ").append(ms).append(" ms (").append(what).append("): ");
            if (mode == Mode.VSYNC) {
                s.append("held ").append(held).append(" game buffers when frames came back");
                if (drained >= 0) s.append(", gave back ").append(drained).append(" when the limiter stopped");
                s.append(", last buffer back ").append(lastBackMs >= 0 ? "at +" + lastBackMs + " ms" : "never");
                s.append(", longest vsync thread pause ").append(longestPauseMs).append(" ms -> ");
            }
            s.append(limitHeld() ? "the FPS limit may have held the game" : "not held by the FPS limit");
            if (cpu == null) return s.toString();
            s.append("; game CPU: ");
            if (cpu.isEmpty()) return s.append("no thread ran -> waiting, not computing").toString();
            AgvnGameThreads.Busy b = cpu.get(0);
            s.append('"').append(b.name).append("\" ").append(b.percent).append("% of a core on cpu ").append(b.core)
                    .append(", all threads ").append(AgvnGameThreads.total(cpu)).append('%');
            if (b.percent >= BUSY_PERCENT) s.append(" -> busy (loading or computing)");
            else if (b.percent < WAITING_PERCENT) s.append(" -> waiting, not computing");
            return s.toString();
        }

        /** For the game's session events. */
        String vietnamese() {
            String what = mode == Mode.NO_LIMIT ? "không giới hạn FPS"
                    : "giới hạn " + limit + " FPS, " + (mode == Mode.VSYNC ? "khớp nhịp màn hình" : "bộ hẹn giờ");
            StringBuilder s = new StringBuilder("Đứng hình ").append(ms).append(" ms (").append(what).append("): ");
            if (mode == Mode.VSYNC) {
                s.append("app còn giữ ").append(held).append(" bộ đệm của game lúc game chạy lại");
                if (drained >= 0) s.append(", trả ").append(drained).append(" bộ đệm khi bộ giới hạn dừng");
                s.append(", bộ đệm cuối trả ").append(lastBackMs >= 0 ? "ở +" + lastBackMs + " ms" : "không có");
                s.append(", luồng vsync nghỉ lâu nhất ").append(longestPauseMs).append(" ms → ");
            }
            s.append(limitHeld() ? "có thể do giới hạn FPS giữ game" : "không phải do giới hạn FPS giữ game");
            if (cpu == null) return s.toString();
            s.append("; CPU của game: ");
            if (cpu.isEmpty()) return s.append("không thread nào chạy → game đang chờ, không tính gì").toString();
            AgvnGameThreads.Busy b = cpu.get(0);
            s.append("thread \"").append(b.name).append("\" dùng ").append(b.percent).append("% một nhân (nhân ")
                    .append(b.core).append("), cả game ").append(AgvnGameThreads.total(cpu)).append('%');
            if (b.percent >= BUSY_PERCENT) s.append(" → game đang bận (tải hoặc tính)");
            else if (b.percent < WAITING_PERCENT) s.append(" → game đang chờ, không tính gì");
            return s.toString();
        }
    }

    private long lastFrameNs, lastTickNs, lastBackNs, longestPauseNs;
    private int drained = -1;

    /** A game frame came; {@code held} = buffers the vsync limiter holds before it. Returns the pause before it, or null. */
    synchronized Stall onFrame(long nowNs, Mode mode, int limit, int held) {
        Stall stall = null;
        if (lastFrameNs != 0 && nowNs - lastFrameNs >= STALL_NS) {
            stall = new Stall(ms(nowNs - lastFrameNs), mode, limit, held, drained,
                    lastBackNs > lastFrameNs ? ms(lastBackNs - lastFrameNs) : -1, ms(longestPauseNs));
        }
        lastFrameNs = nowNs;
        lastBackNs = 0;
        longestPauseNs = 0;
        drained = -1;
        return stall;
    }

    /** The vsync limiter's thread ran. */
    synchronized void onTick(long nowNs) {
        if (lastFrameNs != 0) longestPauseNs = Math.max(longestPauseNs, nowNs - Math.max(lastTickNs, lastFrameNs));
        lastTickNs = nowNs;
    }

    /** The vsync limiter gave buffers back. */
    synchronized void onBack(long nowNs) {
        lastBackNs = nowNs;
    }

    /** The vsync limiter stopped, giving back {@code count} buffers; its thread is gone until the next frame. */
    synchronized void onStop(long nowNs, int count) {
        if (count > 0) lastBackNs = nowNs;
        drained = Math.max(drained, 0) + count;
        lastTickNs = 0;
    }

    private static long ms(long ns) {
        return ns / 1_000_000L;
    }
}

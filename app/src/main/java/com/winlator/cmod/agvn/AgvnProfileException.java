/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

/** Invalid agvn-profile.json; the message is Vietnamese and shown to the player as is. */
public class AgvnProfileException extends Exception {
    public AgvnProfileException(String message) {
        super(message);
    }
}

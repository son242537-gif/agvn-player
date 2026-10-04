/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import com.winlator.cmod.core.EnvVars;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The bundled Vulkan wrapper's two shader rewrites on Mali. On the ARM driver only, libvulkan_wrapper.so's
 * vkCreateShaderModule copies each shader the game makes, turns its OpConstantComposite into OpSpecConstantComposite
 * (patch_OpConstantComposite_to_OpSpecConstantComposite) and takes its ClipDistance and CullDistance out
 * (remove_ClipDistance_CullDistance), each unless its switch is "1". A crash there is Wine's "Assertion failed! ...
 * vkCreateShaderModule" box. "Tự sửa lỗi" turns them off one at a time, for that game. AGVN turns the first off for any
 * DXVK-Sarek (XServerDisplayActivity), before the game's own variables, which win. Pure Java (JVM-testable).
 */
final class AgvnWrapperPasses {
    static final String CONSTANTS = "WRAPPER_NO_PATCH_OPCONSTCOMP", CLIP = "WRAPPER_NO_REMOVE_CLIP_DISTANCE";
    static final String SHADER_CALL = "vkCreateShaderModule";
    private static final Pattern LEADING_NUMBER = Pattern.compile("^\\s*[+-]?(\\d+)");

    private AgvnWrapperPasses() {}

    /** True for a Mali or Immortalis GPU, the only ones the wrapper rewrites shaders for. */
    static boolean mali(String gpu) {
        String g = gpu == null ? "" : gpu.toLowerCase(Locale.ROOT);
        return g.contains("mali") || g.contains("immortalis");
    }

    /**
     * True when Wine's box may name {@link #SHADER_CALL}: Wine traces the box's text cut after about 290 characters,
     * so a long game folder cuts the call's name ("vkCreateShaderMo", read as its start) or leaves none (null).
     */
    static boolean creatingShader(String call) {
        return call == null || SHADER_CALL.startsWith(call);
    }

    /**
     * True when the pass {@code name} still runs for a game with these variables (the container's, then the game's,
     * which win); {@code sarek}: the game's DXVK is a Sarek, which has the constants pass off.
     */
    static boolean runs(String containerVars, String gameVars, String name, boolean sarek) {
        EnvVars game = new EnvVars(gameVars == null ? "" : gameVars), container = new EnvVars(containerVars == null ? "" : containerVars);
        EnvVars set = game.has(name) ? game : container.has(name) ? container : null;
        if (set == null) return !(sarek && CONSTANTS.equals(name));
        Matcher number = LEADING_NUMBER.matcher(set.get(name)); // the wrapper reads atoi(value) != 0 as "off"
        return !number.find() || number.group(1).replace("0", "").isEmpty();
    }

    /** The game's variables with the pass {@code name} off. */
    static String off(String gameVars, String name) {
        EnvVars vars = new EnvVars(gameVars == null ? "" : gameVars);
        vars.put(name, "1");
        return vars.toString();
    }
}

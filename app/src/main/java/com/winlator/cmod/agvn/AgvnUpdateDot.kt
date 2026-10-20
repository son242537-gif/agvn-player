/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager

/**
 * True while a newer AGVN Player is published ([AgvnUpdateBadge]); it changes as soon as a check (when the app opens,
 * by hand, or in the background) writes its answer.
 */
@Composable
fun rememberAgvnUpdateDot(): Boolean {
    val context = LocalContext.current
    var shown by remember { mutableStateOf(AgvnUpdateBadge.shown(context)) }
    DisposableEffect(context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == AgvnUpdateBadge.PREF_FOUND_CODE) shown = AgvnUpdateBadge.shown(context)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return shown
}

/** The red dot: a newer AGVN Player is out. */
@Composable
fun AgvnRedDot(modifier: Modifier = Modifier) {
    Box(modifier.size(10.dp).background(Color(0xFFE53935), CircleShape))
}

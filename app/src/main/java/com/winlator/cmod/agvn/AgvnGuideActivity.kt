/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.winlator.cmod.ui.applyAppFullscreen
import com.winlator.cmod.ui.theme.WinZTheme

/** "Hướng dẫn": simple how-to cards for players (add a game, play, graphics, on-screen keys, mouse, problems). */
class AgvnGuideActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyAppFullscreen(this)
        val topic = intent.getStringExtra(EXTRA_TOPIC)
        setContent { WinZTheme { AgvnGuideScreen(topic) { finish() } } }
    }

    companion object {
        const val EXTRA_TOPIC = "agvn_guide_topic"
        /** Bump the suffix to show the guide once more after an update that changes it a lot. */
        const val PREF_SHOWN = "agvn_guide_shown_v1"

        /** Opens the guide the first time the library starts after install or update; later calls do nothing. */
        @JvmStatic
        fun openOnce(activity: android.app.Activity) {
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(activity)
            if (prefs.getBoolean(PREF_SHOWN, false)) return
            prefs.edit().putBoolean(PREF_SHOWN, true).apply()
            open(activity)
        }

        /** Opens the guide, optionally with one topic (see [AgvnGuideContent]) already open. */
        @JvmStatic
        @JvmOverloads
        fun open(context: Context, topic: String? = null) {
            val intent = Intent(context, AgvnGuideActivity::class.java)
            if (topic != null) intent.putExtra(EXTRA_TOPIC, topic)
            if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}

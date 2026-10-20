/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BuildCircle
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.ui.graphics.vector.ImageVector
import com.winlator.cmod.R

/** One customer guide topic: plain-Vietnamese title, one-line summary and numbered steps (string resources). */
data class AgvnGuideTopic(
    val id: String,
    val icon: ImageVector,
    val title: Int,
    val summary: Int,
    val steps: Int
)

object AgvnGuideContent {
    const val ADD = "add"
    const val PLAY = "play"
    const val GRAPHICS = "graphics"
    const val CONTROLS = "controls"
    const val MOUSE = "mouse"
    const val TROUBLE = "trouble"
    const val EXPERT = "expert"

    /** Every topic, in the order a new player needs them. */
    val ALL: List<AgvnGuideTopic> = listOf(
        AgvnGuideTopic(ADD, Icons.Outlined.Add, R.string.agvn_guide_add_title, R.string.agvn_guide_add_summary, R.array.agvn_guide_add_steps),
        AgvnGuideTopic(PLAY, Icons.Outlined.PlayArrow, R.string.agvn_guide_play_title, R.string.agvn_guide_play_summary, R.array.agvn_guide_play_steps),
        AgvnGuideTopic(GRAPHICS, Icons.Outlined.Speed, R.string.agvn_guide_graphics_title, R.string.agvn_guide_graphics_summary, R.array.agvn_guide_graphics_steps),
        AgvnGuideTopic(CONTROLS, Icons.Outlined.Keyboard, R.string.agvn_guide_controls_title, R.string.agvn_guide_controls_summary, R.array.agvn_guide_controls_steps),
        AgvnGuideTopic(MOUSE, Icons.Outlined.Mouse, R.string.agvn_guide_mouse_title, R.string.agvn_guide_mouse_summary, R.array.agvn_guide_mouse_steps),
        AgvnGuideTopic(TROUBLE, Icons.Outlined.ReportProblem, R.string.agvn_guide_trouble_title, R.string.agvn_guide_trouble_summary, R.array.agvn_guide_trouble_steps),
        AgvnGuideTopic(EXPERT, Icons.Outlined.BuildCircle, R.string.agvn_guide_expert_title, R.string.agvn_guide_expert_summary, R.array.agvn_guide_expert_steps)
    )

    /** The short tour shown while the first setup runs. */
    val TOUR: List<AgvnGuideTopic> = ALL.filter { it.id in setOf(ADD, PLAY, GRAPHICS, CONTROLS) }
}

/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.winlator.cmod.agvn.AgvnSettingHelp

/**
 * The "?" next to a setting: what it is, why a player would change it, and how. Shows nothing when
 * assets/agvn/setting-help.txt has no text for [label], so every settings row can call it.
 */
@Composable
internal fun SettingHelpButton(label: String) {
    val context = LocalContext.current
    val help = remember(label) { AgvnSettingHelp.get(context, label) } ?: return
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = Modifier.size(36.dp)) {
        Icon(
            Icons.Outlined.HelpOutline,
            contentDescription = "Giải thích: $label",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Đã hiểu") } },
            title = { Text(label) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    HelpPart("Là gì", help.what)
                    HelpPart("Vì sao chỉnh", help.why)
                    HelpPart("Chỉnh thế nào", help.how)
                }
            }
        )
    }
}

@Composable
private fun HelpPart(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Text(body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
}

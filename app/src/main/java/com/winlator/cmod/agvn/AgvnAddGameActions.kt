/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.winlator.cmod.R

/**
 * The other ways to add a game, at the bottom of "Thêm game": a folder the scan does not reach, a downloaded archive
 * (unpacked by the app, [AgvnUnpackDialog]) and an .exe picked by hand.
 */
@Composable
fun AgvnAddGameActions(landscape: Boolean, onPickFolder: () -> Unit, onPickArchive: () -> Unit, onPickExe: () -> Unit) {
    val folder: @Composable (Modifier) -> Unit = { m ->
        ActionButton(Icons.Outlined.FolderOpen, stringResource(R.string.agvn_add_pick_folder), onPickFolder, m)
    }
    val archive: @Composable (Modifier) -> Unit = { m ->
        ActionButton(Icons.Outlined.FolderZip, stringResource(R.string.agvn_unpack_action), onPickArchive, m)
    }
    val exe: @Composable (Modifier) -> Unit = { m ->
        TextButton(onClick = onPickExe, modifier = m) { Text(stringResource(R.string.agvn_add_pick_exe), textAlign = TextAlign.Center) }
    }
    if (landscape) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            folder(Modifier.weight(1f))
            archive(Modifier.weight(1f))
            exe(Modifier.weight(1f))
        }
    } else {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            folder(Modifier.fillMaxWidth())
            archive(Modifier.fillMaxWidth())
            exe(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

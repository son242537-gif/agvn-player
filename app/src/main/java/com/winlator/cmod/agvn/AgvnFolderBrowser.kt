/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn

import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.winlator.cmod.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Folder picker for "Chọn thư mục khác": storage list, then folders only; "Quét thư mục này" scans the open folder. */
@Composable
fun AgvnFolderBrowser(onBack: () -> Unit, onScan: (File) -> Unit) {
    var path by rememberSaveable { mutableStateOf<String?>(null) }
    val current = path?.let { File(it) }
    val context = LocalContext.current
    val volumes = remember { AgvnGameImporter.storageVolumes() }
    val internal = remember { Environment.getExternalStorageDirectory() }
    fun volumeLabel(v: File): String =
        if (v == internal) context.getString(R.string.agvn_internal_storage) else context.getString(R.string.agvn_folder_sd, v.name)
    fun goUp() {
        path = if (current == null || volumes.any { it == current }) null else current.parent
    }
    BackHandler { if (current == null) onBack() else goUp() }

    val children by produceState<List<File>?>(null, path) {
        value = null
        value = if (current == null) volumes else withContext(Dispatchers.IO) {
            (current.listFiles { f -> f.isDirectory && !f.name.startsWith(".") } ?: emptyArray())
                .sortedBy { AgvnGameTitle.searchKey(it.name) }
        }
    }
    val shownPath = current?.let { dir ->
        val volume = volumes.firstOrNull { dir.absolutePath == it.absolutePath || dir.absolutePath.startsWith(it.absolutePath + "/") }
        if (volume == null) dir.absolutePath else volumeLabel(volume) + dir.absolutePath.removePrefix(volume.absolutePath)
    } ?: stringResource(R.string.agvn_folder_drives)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxSize().padding(horizontal = 12.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.agvn_add_back)) }
                Text(stringResource(R.string.agvn_folder_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Text(
                stringResource(R.string.agvn_folder_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { goUp() }, enabled = current != null) {
                    Icon(Icons.Outlined.ArrowUpward, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.agvn_folder_up))
                }
                Spacer(Modifier.size(10.dp))
                Text(shownPath, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            HorizontalDivider()
            val list = children
            Box(Modifier.fillMaxWidth().weight(1f)) {
                if (list != null && list.isEmpty()) {
                    Text(
                        stringResource(R.string.agvn_folder_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp)
                    )
                } else if (list != null) {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(list, key = { it.absolutePath }) { dir ->
                            val icon = when {
                                current != null -> Icons.Outlined.Folder
                                dir == internal -> Icons.Outlined.Smartphone
                                else -> Icons.Outlined.SdStorage
                            }
                            FolderRow(icon, if (current == null) volumeLabel(dir) else dir.name) { path = dir.absolutePath }
                        }
                    }
                }
            }
            Button(
                onClick = { current?.let(onScan) },
                enabled = current != null,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) { Text(stringResource(R.string.agvn_folder_scan)) }
        }
    }
}

@Composable
private fun FolderRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

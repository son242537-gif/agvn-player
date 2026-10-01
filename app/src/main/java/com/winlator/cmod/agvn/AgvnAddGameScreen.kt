/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.winlator.cmod.R

/** "Thêm game": search, "Chưa thêm" then "Đã có trong thư viện", and the two other ways to add a game at the bottom. */
@Composable
fun AgvnAddGameScreen(
    entries: List<AgvnImportEntry>?,
    onClose: () -> Unit,
    onRescan: () -> Unit,
    onPick: (AgvnImportEntry) -> Unit,
    onPickFolder: () -> Unit,
    onPickExe: () -> Unit
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var query by rememberSaveable { mutableStateOf("") }
    val icons = remember { HashMap<String, ImageBitmap?>() }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxSize().padding(horizontal = 12.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.agvn_add_back)) }
                Text(stringResource(R.string.agvn_import_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.size(12.dp))
                if (landscape) SearchField(query, { query = it }, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                IconButton(onClick = onRescan, enabled = entries != null) {
                    Icon(Icons.Outlined.Refresh, stringResource(R.string.agvn_add_rescan))
                }
            }
            if (!landscape) {
                Text(
                    stringResource(R.string.agvn_add_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
                Spacer(Modifier.height(10.dp))
                SearchField(query, { query = it }, Modifier.fillMaxWidth())
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when {
                    entries == null -> Message(stringResource(R.string.agvn_import_scanning), loading = true)
                    entries.isEmpty() -> EmptyState(onRescan)
                    else -> GameList(entries.filter { AgvnGameTitle.matches(it.title, query) }, query, icons, onPick)
                }
            }
            BottomActions(landscape, onPickFolder, onPickExe)
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        modifier = modifier,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.agvn_add_search)) },
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Outlined.Close, stringResource(R.string.agvn_add_clear_search)) }
        }
    )
}

@Composable
private fun GameList(visible: List<AgvnImportEntry>, query: String, icons: HashMap<String, ImageBitmap?>, onPick: (AgvnImportEntry) -> Unit) {
    if (visible.isEmpty()) {
        Message(stringResource(R.string.agvn_add_no_match, query.trim()))
        return
    }
    val fresh = visible.filter { it.existing == null }
    val added = visible.filter { it.existing != null }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (fresh.isNotEmpty()) {
            item(key = "h-new") { SectionHeader(stringResource(R.string.agvn_add_section_new, fresh.size)) }
            items(fresh, key = { it.key }) { AgvnAddGameRow(it, icons) { onPick(it) } }
        }
        if (added.isNotEmpty()) {
            item(key = "h-added") { SectionHeader(stringResource(R.string.agvn_add_section_added, added.size)) }
            items(added, key = { it.key }) { AgvnAddGameRow(it, icons) { onPick(it) } }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 6.dp, top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun Message(text: String, loading: Boolean = false) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (loading) {
            CircularProgressIndicator()
            Spacer(Modifier.height(14.dp))
        }
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyState(onRescan: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Outlined.FolderOpen, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.agvn_add_empty_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.agvn_add_empty_body), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onRescan) { Text(stringResource(R.string.agvn_add_rescan)) }
    }
}

@Composable
private fun BottomActions(landscape: Boolean, onPickFolder: () -> Unit, onPickExe: () -> Unit) {
    val folder: @Composable (Modifier) -> Unit = { m ->
        OutlinedButton(onClick = onPickFolder, modifier = m) {
            Icon(Icons.Outlined.FolderOpen, null, Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.agvn_add_pick_folder))
        }
    }
    val exe: @Composable (Modifier) -> Unit = { m ->
        TextButton(onClick = onPickExe, modifier = m) { Text(stringResource(R.string.agvn_add_pick_exe)) }
    }
    if (landscape) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            folder(Modifier.weight(1f))
            exe(Modifier.weight(1f))
        }
    } else {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            folder(Modifier.fillMaxWidth())
            exe(Modifier.fillMaxWidth())
        }
    }
}

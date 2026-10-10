/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.winlator.cmod.R
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/** Exe icons are read two at a time so a long list does not hammer the storage. */
private val iconDispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
private const val ICON_PX = 96

private val TILE_COLORS = listOf(0xFF5B6CFF, 0xFF00A884, 0xFFE0822B, 0xFFB75CE6, 0xFF2A9FD6, 0xFFD6455D).map { Color(it) }

/** One game: small picture, one-line title, "Engine · chỗ để"; games already in the library are dimmed and ticked. */
@Composable
fun AgvnAddGameRow(entry: AgvnImportEntry, icons: HashMap<String, ImageBitmap?>, onClick: () -> Unit) {
    val added = entry.existing != null
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.alpha(if (added) 0.55f else 1f)) { GameThumb(entry, icons) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).alpha(if (added) 0.6f else 1f)) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (entry.hasProfile) {
                        Spacer(Modifier.width(6.dp))
                        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(
                                stringResource(R.string.agvn_add_profile_badge),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
            if (added) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.CheckCircle, stringResource(R.string.agvn_add_added), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/**
 * The game's icon (loaded lazily, cached per screen): for Ren'Py and RPG Maker its own picture rather than the engine's
 * exe icon (AgvnGameIcons), else the exe icon; a coloured tile with the title's first letter when there is none.
 */
@Composable
private fun GameThumb(entry: AgvnImportEntry, icons: HashMap<String, ImageBitmap?>) {
    val key = entry.key
    val icon by produceState(icons[key], key) {
        if (icons.containsKey(key)) return@produceState
        val loaded = withContext(iconDispatcher) { loadIcon(entry) }
        icons[key] = loaded
        value = loaded
    }
    val shape = RoundedCornerShape(10.dp)
    val current = icon
    if (current != null) {
        Image(current, null, Modifier.size(44.dp).clip(shape))
    } else {
        val letter = entry.title.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: '?'
        Surface(Modifier.size(44.dp), shape = shape, color = TILE_COLORS[Math.floorMod(entry.title.hashCode(), TILE_COLORS.size)]) {
            Box(contentAlignment = Alignment.Center) {
                Text(letter.toString(), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun loadIcon(entry: AgvnImportEntry): ImageBitmap? = try {
    AgvnGameIcons.make(entry.dir, entry.engine, entry.exe, ICON_PX)?.asImageBitmap()
} catch (t: Throwable) {
    null
}

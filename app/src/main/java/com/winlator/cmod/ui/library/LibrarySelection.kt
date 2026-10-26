/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Picking several games of the library to remove them at once: "Chọn nhiều game" in a game's menu starts it with that
 * game picked, a tap picks or unpicks a game, and the bar over the list removes the picked ones (after asking) or ends
 * it, as does Back. Only the library's entries go; the games' own files stay on the phone.
 */
@Stable
internal class LibrarySelection {
    var active by mutableStateOf(false)
        private set
    val picked = mutableStateListOf<String>()

    fun start(shortcutPath: String) {
        active = true
        if (shortcutPath !in picked) picked.add(shortcutPath)
    }

    fun toggle(shortcutPath: String) {
        if (!picked.remove(shortcutPath)) picked.add(shortcutPath)
    }

    fun isPicked(shortcutPath: String) = active && shortcutPath in picked

    fun end() {
        active = false
        picked.clear()
    }

    /** The library changed: games no longer there are unpicked, and once the removed ones were all of them, it ends. */
    fun keepOnly(shortcutPaths: Set<String>) {
        val before = picked.size
        picked.retainAll(shortcutPaths)
        if (active && picked.isEmpty() && before > 0) active = false
    }
}

internal val LocalLibrarySelection = staticCompositionLocalOf<LibrarySelection?> { null }

/** Over the list while picking: how many, all or none of {@code visible}, remove, end. */
@Composable
internal fun LibrarySelectionBar(selection: LibrarySelection, visible: List<LibraryItem>, onRemove: (List<String>) -> Unit) {
    val all = visible.isNotEmpty() && visible.all { it.shortcutPath in selection.picked }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = selection::end) { Icon(Icons.Outlined.Close, "Thoát chọn nhiều") }
            Text(
                "Đã chọn ${selection.picked.size}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = {
                if (all) selection.picked.clear()
                else visible.forEach { if (it.shortcutPath !in selection.picked) selection.picked.add(it.shortcutPath) }
            }) { Text(if (all) "Bỏ chọn hết" else "Chọn tất cả") }
            Button(
                onClick = { onRemove(selection.picked.toList()) },
                enabled = selection.picked.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Icon(Icons.Outlined.DeleteOutline, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Xóa")
            }
        }
    }
}

/** On a game's card while picking, where its play and menu buttons are. */
@Composable
internal fun LibrarySelectionMark(picked: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.size(42.dp).background(Color.Black.copy(alpha = .45f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (picked) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            if (picked) "Đã chọn" else "Chưa chọn",
            tint = if (picked) MaterialTheme.colorScheme.primary else Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}

package com.winlator.cmod.ui.library

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import com.winlator.cmod.R
import androidx.activity.ComponentDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.winlator.cmod.container.Shortcut
import com.winlator.cmod.core.GameSaveManager
import com.winlator.cmod.ui.theme.WinZTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object GameSavesComposeDialog {
    @JvmStatic
    fun show(fragment: Fragment, shortcut: Shortcut) {
        val dialog = ComponentDialog(fragment.requireContext(), R.style.GameSavesDialog)
        dialog.show()
        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            decorView.setBackgroundColor(Color.TRANSPARENT)
            clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0f)
        }
        dialog.setContentView(ComposeView(fragment.requireContext()).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setContent {
                WinZTheme {
                    GameSavesPanel(shortcut = shortcut, onClose = dialog::dismiss)
                }
            }
        })
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
}

@Composable
private fun GameSavesPanel(shortcut: Shortcut, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val globalAutoBackup = GameSaveManager.isGlobalAutoBackupEnabled(context)
    var roots by remember(shortcut.file.path) { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var autoBackup by remember { mutableStateOf(GameSaveManager.isAutoBackupEnabled(shortcut)) }
    var latest by remember { mutableStateOf(GameSaveManager.getLatestBackup(shortcut)) }
    var message by remember { mutableStateOf<String?>(null) }

    fun refreshLatest() {
        latest = GameSaveManager.getLatestBackup(shortcut)
    }

    fun rescan() {
        if (busy) return
        busy = true
        message = context.getString(R.string.agvn_ui_library_scanning_common)
        scope.launch {
            roots = withContext(Dispatchers.IO) { GameSaveManager.rediscoverSaveRoots(shortcut) }
            message = if (roots.isEmpty()) {
                context.getString(R.string.agvn_ui_library_no_own_folder_whole)
            } else {
                context.getString(R.string.agvn_ui_library_saves_found, roots.size)
            }
            busy = false
        }
    }

    LaunchedEffect(shortcut.file.path) {
        roots = withContext(Dispatchers.IO) { GameSaveManager.getSaveRoots(shortcut) }
        latest = GameSaveManager.getLatestBackup(shortcut)
        loading = false
    }

    Box(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 8.dp,
            shadowElevation = 18.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.agvn_ui_library_saves_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(shortcut.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onClose, enabled = !busy) { Text(stringResource(R.string.agvn_ui_library_close)) }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(stringResource(R.string.agvn_ui_library_backup_folder), style = MaterialTheme.typography.labelLarge)
                        Text(
                            "AGVN-Player/Saves/${GameSaveManager.getGameDir(shortcut).name}/",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(stringResource(R.string.agvn_ui_library_latest_backup), style = MaterialTheme.typography.labelLarge)
                        Text(
                            latest?.let(::backupLabel) ?: stringResource(R.string.agvn_ui_library_no_backup),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.agvn_ui_library_auto_backup_short), fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(
                                if (globalAutoBackup) R.string.agvn_ui_library_auto_backup_global
                                else R.string.agvn_ui_library_auto_backup_summary
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = globalAutoBackup || autoBackup,
                        onCheckedChange = {
                            autoBackup = it
                            GameSaveManager.setAutoBackupEnabled(shortcut, it)
                        },
                        enabled = !busy && !globalAutoBackup
                    )
                }

                Text(stringResource(R.string.agvn_ui_library_save_locations), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (loading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.height(22.dp))
                        Text("  " + stringResource(R.string.agvn_ui_library_detecting), style = MaterialTheme.typography.bodyMedium)
                    }
                } else if (roots.isEmpty()) {
                    Text(
                        stringResource(R.string.agvn_ui_library_no_specific_folder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    roots.forEach {
                        Text("• $it", style = MaterialTheme.typography.bodySmall)
                    }
                }

                OutlinedButton(
                    onClick = { rescan() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy && !loading
                ) {
                    Text(stringResource(R.string.agvn_ui_library_rescan))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            if (!busy) {
                                busy = true
                                message = context.getString(R.string.agvn_ui_library_backing_up)
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        GameSaveManager.backup(shortcut, false)
                                    }
                                    refreshLatest()
                                    message = when {
                                        result.ok && result.wholeProfile ->
                                            context.getString(R.string.agvn_ui_library_backup_done_whole, result.fileCount)
                                        result.ok -> context.getString(R.string.agvn_ui_library_backup_done, result.fileCount)
                                        else -> context.getString(R.string.agvn_ui_library_backup_failed, result.error ?: context.getString(R.string.agvn_ui_library_unknown_error))
                                    }
                                    busy = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !busy && !loading
                    ) {
                        Text(stringResource(R.string.agvn_ui_library_backup_now))
                    }

                    OutlinedButton(
                        onClick = {
                            if (!busy) {
                                busy = true
                                message = context.getString(R.string.agvn_ui_library_restoring)
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        GameSaveManager.restoreLatest(shortcut)
                                    }
                                    message = if (result.ok) {
                                        context.getString(R.string.agvn_ui_library_restored, result.fileCount)
                                    } else {
                                        context.getString(R.string.agvn_ui_library_restore_failed, result.error ?: context.getString(R.string.agvn_ui_library_unknown_error))
                                    }
                                    busy = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !busy && latest != null
                    ) {
                        Text(stringResource(R.string.agvn_ui_library_restore))
                    }
                }

                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun backupLabel(file: File): String {
    val date = SimpleDateFormat("dd MMM yyyy • HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
    return "${file.name}\n$date"
}

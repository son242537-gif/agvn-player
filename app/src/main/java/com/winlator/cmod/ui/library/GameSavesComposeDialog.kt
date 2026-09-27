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
        message = "Scanning common save locations…"
        scope.launch {
            roots = withContext(Dispatchers.IO) { GameSaveManager.rediscoverSaveRoots(shortcut) }
            message = if (roots.isEmpty()) {
                "Chưa tìm thấy thư mục lưu riêng của game. Sao lưu thủ công sẽ lưu toàn bộ hồ sơ Wine."
            } else {
                "Detected ${roots.size} save location${if (roots.size == 1) "" else "s"}."
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
                        Text("Bản lưu game", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(shortcut.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onClose, enabled = !busy) { Text("Đóng") }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Thư mục sao lưu", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "AGVN-Player/Saves/${GameSaveManager.getGameDir(shortcut).name}/",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(3.dp))
                        Text("Bản sao lưu mới nhất", style = MaterialTheme.typography.labelLarge)
                        Text(
                            latest?.let(::backupLabel) ?: "Chưa có bản sao lưu",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Tự sao lưu", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (globalAutoBackup) "Đang bật chung trong Cài đặt"
                            else "Ghi đè auto-latest.zip khi thoát game",
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

                Text("Vị trí lưu", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (loading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.height(22.dp))
                        Text("  Detecting…", style = MaterialTheme.typography.bodyMedium)
                    }
                } else if (roots.isEmpty()) {
                    Text(
                        "Chưa tìm thấy thư mục cụ thể.",
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
                    Text("Quét lại vị trí lưu")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            if (!busy) {
                                busy = true
                                message = "Backing up saves…"
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        GameSaveManager.backup(shortcut, false)
                                    }
                                    refreshLatest()
                                    message = when {
                                        result.ok && result.wholeProfile ->
                                            "Backup complete: ${result.fileCount} files. No per-game folder was detected, so the Wine profile was used."
                                        result.ok -> "Backup complete: ${result.fileCount} files."
                                        else -> "Backup failed: ${result.error ?: "unknown error"}"
                                    }
                                    busy = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !busy && !loading
                    ) {
                        Text("Sao lưu ngay")
                    }

                    OutlinedButton(
                        onClick = {
                            if (!busy) {
                                busy = true
                                message = "Restoring latest backup…"
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        GameSaveManager.restoreLatest(shortcut)
                                    }
                                    message = if (result.ok) {
                                        "Restored ${result.fileCount} files."
                                    } else {
                                        "Restore failed: ${result.error ?: "unknown error"}"
                                    }
                                    busy = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !busy && latest != null
                    ) {
                        Text("Khôi phục")
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

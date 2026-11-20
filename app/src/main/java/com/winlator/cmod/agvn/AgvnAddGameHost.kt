/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn

import android.os.Environment
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import com.winlator.cmod.MainActivity
import com.winlator.cmod.R
import com.winlator.cmod.ui.theme.WinZTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Shows the "Thêm game" screen full screen above the library; Back closes it. */
object AgvnAddGameHost {
    @Suppress("DEPRECATION") // systemUiVisibility: keeps the library's immersive mode on the dialog window
    @JvmStatic
    fun show(activity: MainActivity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val dialog = ComponentDialog(activity, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen)
        val view = ComposeView(activity)
        view.setContent { WinZTheme { AgvnAddGameFlow(activity) { dialog.dismiss() } } }
        dialog.setContentView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        dialog.window?.let { w ->
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            w.decorView.systemUiVisibility = activity.window.decorView.systemUiVisibility
        }
        dialog.show()
    }
}

/**
 * Game list, or the folder picker while "Chọn thư mục khác" is open (it also adds a tapped game file) or "Thêm từ tệp
 * nén" picks an archive. Scans run off the main thread.
 */
@Composable
private fun AgvnAddGameFlow(activity: MainActivity, onClose: () -> Unit) {
    var entries by remember { mutableStateOf<List<AgvnImportEntry>?>(null) }
    var scanRevision by remember { mutableIntStateOf(0) }
    var browsing by rememberSaveable { mutableStateOf(false) }
    var pickedFolder by remember { mutableStateOf<File?>(null) }
    // "Thêm từ tệp nén": the picker lists archives, opened at Download or at the folder a scan found archives in
    var archives by rememberSaveable { mutableStateOf(false) }
    var archiveStart by remember { mutableStateOf<File?>(null) }
    val scope = rememberCoroutineScope()

    /** Unpacks into internal storage/AGVN, then the game inside goes to the usual settings preview. */
    fun unpack(archive: File) {
        AgvnUnpackFlow.run(activity, archive, { onClose() }) { scanRevision++ } // the list behind shows the new folder
    }

    LaunchedEffect(scanRevision) {
        entries = null
        val found = withContext(Dispatchers.IO) {
            try {
                AgvnImportEntry.scan(activity)
            } catch (e: Exception) {
                emptyList()
            }
        }
        entries = found
        pickedFolder?.let { folder ->
            val prefix = folder.absolutePath
            if (found.none { it.dir.absolutePath == prefix || it.dir.absolutePath.startsWith("$prefix/") }) {
                // downloads still packed: offered to be unpacked here rather than with another app
                val packed = withContext(Dispatchers.IO) { AgvnArchiveKind.pickable(folder.list()) }
                if (packed.isEmpty()) {
                    Toast.makeText(activity, R.string.agvn_add_folder_nothing, Toast.LENGTH_LONG).show()
                } else {
                    AgvnUnpackFlow.offer(activity, packed) {
                        if (packed.size == 1) {
                            unpack(File(folder, packed[0]))
                        } else {
                            archiveStart = folder
                            archives = true
                            browsing = true
                        }
                    }
                }
            }
            pickedFolder = null
        }
    }

    if (browsing) {
        AgvnFolderBrowser(
            onBack = { browsing = false },
            onScan = { folder ->
                AgvnGameRoots.add(activity, folder)
                pickedFolder = folder
                browsing = false
                scanRevision++
            },
            archives = archives,
            start = if (archives) archiveStart else null,
            onPickFile = { file ->
                if (archives) {
                    browsing = false
                    unpack(file)
                } else scope.launch {
                    val entry = withContext(Dispatchers.IO) { runCatching { AgvnImportEntry.forFile(activity, file) }.getOrNull() }
                    if (entry != null && !activity.isFinishing) {
                        AgvnImportDialog.preview(activity, entry.dir, entry.variant, DeviceTierManager.current(activity), entry.existing) { onClose() }
                    }
                }
            }
        )
    } else {
        AgvnAddGameScreen(
            entries = entries,
            onClose = onClose,
            onRescan = { scanRevision++ },
            onPick = { entry ->
                AgvnImportDialog.preview(activity, entry.dir, entry.variant, DeviceTierManager.current(activity), entry.existing) { onClose() }
            },
            onPickFolder = {
                archives = false
                browsing = true
            },
            onPickArchive = {
                archiveStart = File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_DOWNLOADS)
                archives = true
                browsing = true
            },
            onPickExe = {
                onClose()
                activity.openFileManagerFromLibrary()
            }
        )
    }
}

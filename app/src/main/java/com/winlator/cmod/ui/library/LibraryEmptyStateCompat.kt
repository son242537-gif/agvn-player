package com.winlator.cmod.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.winlator.cmod.MainActivity
import com.winlator.cmod.R
import com.winlator.cmod.ui.LandscapeMainNavigation

@Composable
internal fun LibraryRootWithoutEmptyDescription(
    items: List<LibraryItem>,
    grid: Boolean,
    query: String,
    selectedShortcutPath: MutableState<String?>,
    callbacks: LibraryCallbacks
) {
    if (items.isNotEmpty() || query.isNotBlank()) {
        LibraryRoot(items, grid, query, selectedShortcutPath, callbacks)
        return
    }

    val activity = LocalContext.current as? MainActivity
    val configuration = LocalConfiguration.current
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp
    // Same bars as the filled library: landscape uses the in-screen navigation, portrait the toolbar + bottom bar.
    DisposableEffect(activity, landscape) {
        activity?.setBottomNavigationVisible(!landscape)
        activity?.setMainToolbarVisible(!landscape)
        onDispose {
            activity?.setBottomNavigationVisible(true)
            activity?.setMainToolbarVisible(true)
        }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (landscape) {
            LandscapeMainNavigation(
                activity, R.id.main_menu_shortcuts, "Thư viện",
                actionIcon = Icons.Outlined.HelpOutline,
                actionDescription = "Hướng dẫn",
                onAction = { activity?.let { com.winlator.cmod.agvn.AgvnGuideActivity.open(it) } }
            )
        }
        EmptyLibraryContent(activity, Modifier.weight(1f))
    }
}

@Composable
private fun EmptyLibraryContent(activity: MainActivity?, modifier: Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))
        Text("Chưa có game nào", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Chép thư mục game vào máy (ví dụ Bộ nhớ trong/AGVN/Tên game), rồi bấm \"Thêm game\". App tự tìm game và tự chỉnh phím, đồ họa cho bạn.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 520.dp)
        )
        Spacer(Modifier.height(20.dp))
        EmptyActionCard(Icons.Outlined.Add, "Thêm game", "Tự tìm game trong máy") {
            activity?.let { com.winlator.cmod.agvn.AgvnImportDialog.show(it) }
        }
        Spacer(Modifier.height(12.dp))
        EmptyActionCard(Icons.Outlined.HelpOutline, "Xem hướng dẫn", "Thêm game, chơi game, chỉnh đồ họa và phím ảo") {
            activity?.let { com.winlator.cmod.agvn.AgvnGuideActivity.open(it) }
        }
    }
}

@Composable
private fun EmptyActionCard(icon: ImageVector, title: String, subtitle: String, click: () -> Unit) {
    Surface(
        onClick = click,
        modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(56.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, modifier = Modifier.size(28.dp)) }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/* Copyright (c) 2026 agvn.io — MIT License (see LICENSE). */
package com.winlator.cmod.agvn

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.winlator.cmod.R

private enum class StepStatus { WAITING, RUNNING, DONE, FAILED }

/** The three first-start steps with their status, the storage-access request and "Thử lại" on failure. */
@Composable
internal fun AgvnSetupSteps(state: AgvnSetupState, actions: AgvnSetupActions) {
    val coreStatus = when {
        state.coreFailed -> StepStatus.FAILED
        state.coreDone -> StepStatus.DONE
        else -> StepStatus.RUNNING
    }
    val containerStatus = when {
        state.containerFailed -> StepStatus.FAILED
        state.containerDone -> StepStatus.DONE
        state.coreDone -> StepStatus.RUNNING
        else -> StepStatus.WAITING
    }
    val accessStatus = if (state.accessDone) StepStatus.DONE else StepStatus.WAITING
    val coreDetail = when {
        state.coreDone -> stringResource(R.string.agvn_setup_done)
        state.coreProgress < 75 -> stringResource(R.string.agvn_setup_step_core_system, state.coreProgress)
        state.coreProgress < 88 -> stringResource(R.string.agvn_setup_step_core_wine)
        else -> stringResource(R.string.agvn_setup_step_core_driver)
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            StepRow(coreStatus, stringResource(R.string.agvn_setup_step_core), coreDetail)
            if (coreStatus == StepStatus.RUNNING) {
                LinearProgressIndicator(
                    progress = { state.coreProgress / 100f },
                    modifier = Modifier.fillMaxWidth().padding(start = 52.dp, end = 16.dp, bottom = 8.dp)
                )
            }
            Divider()
            StepRow(containerStatus, stringResource(R.string.agvn_setup_step_container),
                if (containerStatus == StepStatus.DONE) stringResource(R.string.agvn_setup_done) else null)
            Divider()
            StepRow(accessStatus, stringResource(R.string.agvn_setup_step_access),
                if (state.accessDone) stringResource(R.string.agvn_setup_done) else stringResource(R.string.agvn_setup_access_why))
            if (!state.accessDone && !state.accessSkipped) {
                if (state.accessAsked) {
                    Text(
                        stringResource(
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) R.string.agvn_setup_access_denied
                            else R.string.agvn_setup_access_denied_legacy
                        ),
                        color = Color(0xFFFFB74D),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 52.dp, end = 16.dp, bottom = 6.dp)
                    )
                }
                Row(Modifier.padding(start = 52.dp, end = 16.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = actions::onAllowAccess) { Text(stringResource(R.string.agvn_setup_access_button)) }
                    OutlinedButton(onClick = actions::onAccessLater) { Text(stringResource(R.string.agvn_setup_access_later)) }
                }
            }
        }
    }
    if (state.failed) {
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.agvn_setup_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        Button(onClick = actions::onRetry, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text(stringResource(R.string.agvn_setup_retry)) }
    }
}

@Composable
private fun StepRow(status: StepStatus, title: String, detail: String?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
            when (status) {
                StepStatus.RUNNING -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.5.dp)
                StepStatus.DONE -> Icon(Icons.Outlined.CheckCircle, null, tint = Color(0xFF66BB6A))
                StepStatus.FAILED -> Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                StepStatus.WAITING -> Icon(Icons.Outlined.RadioButtonUnchecked, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (!detail.isNullOrBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(Modifier.padding(start = 52.dp, end = 14.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
}

/** Swipeable short tour (add a game, play, graphics, on-screen keys) to read while the install runs. */
@Composable
internal fun AgvnSetupTour(done: Boolean) {
    val topics = AgvnGuideContent.TOUR
    val pager = rememberPagerState { topics.size }
    Text(
        stringResource(if (done) R.string.agvn_setup_guide_header_done else R.string.agvn_setup_guide_header),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Text(stringResource(R.string.agvn_setup_swipe_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    HorizontalPager(state = pager, pageSpacing = 12.dp, verticalAlignment = Alignment.Top) { page ->
        AgvnGuideCard(topics[page], expanded = true)
    }
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center) {
        topics.indices.forEach { i ->
            Box(
                Modifier.padding(horizontal = 4.dp).size(if (i == pager.currentPage) 10.dp else 7.dp)
                    .background(
                        if (i == pager.currentPage) Color(0xFFFFD97A) else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape
                    )
            )
        }
    }
}

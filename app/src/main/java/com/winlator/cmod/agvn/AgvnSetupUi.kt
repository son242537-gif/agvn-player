/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.winlator.cmod.R
import com.winlator.cmod.ui.applyAppFullscreen
import com.winlator.cmod.ui.theme.WinZTheme

/** What the first-start screen shows; written by [AgvnSetupActivity] on the main thread. */
class AgvnSetupState {
    var coreProgress by mutableIntStateOf(0)
    var coreDone by mutableStateOf(false)
    var coreFailed by mutableStateOf(false)
    var containerDone by mutableStateOf(false)
    var containerFailed by mutableStateOf(false)
    var accessDone by mutableStateOf(false)
    var accessAsked by mutableStateOf(false)
    var accessSkipped by mutableStateOf(false)

    val ready: Boolean get() = coreDone && containerDone && (accessDone || accessSkipped)
    val failed: Boolean get() = coreFailed || containerFailed
}

interface AgvnSetupActions {
    fun onRetry()
    fun onAllowAccess()
    fun onAccessLater()
    fun onStartPlaying()
}

object AgvnSetupHost {
    @JvmStatic
    fun attach(activity: ComponentActivity, state: AgvnSetupState, actions: AgvnSetupActions) {
        applyAppFullscreen(activity)
        activity.setContent { WinZTheme { AgvnSetupScreen(state, actions) } }
    }
}

@Composable
private fun AgvnSetupScreen(state: AgvnSetupState, actions: AgvnSetupActions) {
    val configuration = LocalConfiguration.current
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp
    val background = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    if (landscape) {
        Row(background.padding(horizontal = 24.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                SetupHeader(compact = true)
                Spacer(Modifier.height(12.dp))
                AgvnSetupSteps(state, actions)
                StartButton(state, actions)
            }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                AgvnSetupTour(state.ready)
            }
        }
    } else {
        Column(background.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 18.dp)) {
            SetupHeader(compact = false)
            Spacer(Modifier.height(16.dp))
            AgvnSetupSteps(state, actions)
            StartButton(state, actions)
            Spacer(Modifier.height(18.dp))
            AgvnSetupTour(state.ready)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SetupHeader(compact: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.agvn_logo), "AGVN Player", Modifier.size(if (compact) 52.dp else 64.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(stringResource(R.string.agvn_setup_welcome), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.agvn_setup_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StartButton(state: AgvnSetupState, actions: AgvnSetupActions) {
    if (!state.ready) return
    Spacer(Modifier.height(14.dp))
    Button(onClick = actions::onStartPlaying, modifier = Modifier.fillMaxWidth().height(54.dp)) {
        Text(stringResource(R.string.agvn_setup_start), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

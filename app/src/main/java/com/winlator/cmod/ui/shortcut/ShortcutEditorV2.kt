@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.winlator.cmod.ui.shortcut

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.Monitor
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.winlator.cmod.R
import com.winlator.cmod.ShortcutsFragment
import com.winlator.cmod.XrActivity
import com.winlator.cmod.XServerDisplayActivity
import com.winlator.cmod.box64.Box64PresetManager
import com.winlator.cmod.container.Container
import com.winlator.cmod.container.ContainerManager
import com.winlator.cmod.container.Shortcut
import com.winlator.cmod.core.DefaultVersion
import com.winlator.cmod.core.FileUtils
import com.winlator.cmod.core.GPUInformation
import com.winlator.cmod.core.GameSaveManager
import com.winlator.cmod.core.OpenGLDriverDefaults
import com.winlator.cmod.core.StringUtils
import com.winlator.cmod.fexcore.FEXCorePresetManager
import com.winlator.cmod.inputcontrols.InputControlsManager
import com.winlator.cmod.midi.MidiManager
import com.winlator.cmod.ui.settings.ContainersSettingsActivity
import com.winlator.cmod.ui.settings.CpuSelectorRow
import com.winlator.cmod.ui.settings.DriverOption
import com.winlator.cmod.ui.settings.DxvkAsyncMode
import com.winlator.cmod.ui.settings.EnvironmentVariablesEditor
import com.winlator.cmod.ui.settings.DDrawWrapperChoice
import com.winlator.cmod.ui.settings.SettingChoice
import com.winlator.cmod.ui.settings.SettingDriverChoice
import com.winlator.cmod.ui.settings.SettingInstallChoice
import com.winlator.cmod.ui.settings.SettingMappedChoice
import com.winlator.cmod.ui.settings.SettingText
import com.winlator.cmod.ui.settings.SettingToggle
import com.winlator.cmod.ui.settings.SettingsCard
import com.winlator.cmod.ui.settings.SettingsCatalog
import com.winlator.cmod.ui.settings.SettingsDivider
import com.winlator.cmod.ui.settings.WineRuntimeOption
import com.winlator.cmod.ui.settings.dxvkAsyncMode
import com.winlator.cmod.ui.settings.envPut
import com.winlator.cmod.ui.settings.envValue
import com.winlator.cmod.ui.settings.filterDxvkForVkd3d
import com.winlator.cmod.ui.settings.installAdrenoDriver
import com.winlator.cmod.ui.settings.installRuntimeComponent
import com.winlator.cmod.ui.settings.isDxvkCompatibleWithVkd3d
import com.winlator.cmod.ui.settings.isTurnipDriver
import com.winlator.cmod.ui.settings.isVkd3dEnabled
import com.winlator.cmod.ui.settings.loadSettingsCatalog
import com.winlator.cmod.ui.settings.loadWineRuntimeOptions
import com.winlator.cmod.ui.settings.localeDisplayValue
import com.winlator.cmod.ui.settings.normalizeLocaleValue
import com.winlator.cmod.ui.settings.normalizeResolution
import com.winlator.cmod.ui.settings.readConfig
import com.winlator.cmod.ui.settings.writeConfig
import com.winlator.cmod.winhandler.WinHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val shortcutComponentRowsV2 = listOf(
    "direct3d" to "Direct3D",
    "directsound" to "DirectSound",
    "directmusic" to "DirectMusic",
    "directshow" to "DirectShow",
    "directplay" to "DirectPlay",
    "xaudio" to "XAudio",
    "vcrun2010" to "Visual C++ 2010"
)

private val execArgumentPresetsV2 = listOf(
    "-force-gfx-direct",
    "-force-d3d11-singlethreaded",
    "-force-dx9",
    "-force-d3d9",
    "-force-d3d11",
    "--force-gfx-direct",
    "--force-d3d11-singlethreaded",
    "--force-dx9",
    "--force-d3d9",
    "--force-d3d11",
    "/d3d9"
)

private data class ShortcutCategoryItemV2(val label: String, val icon: ImageVector)

private class ShortcutEditorStateV2(val shortcut: Shortcut) {
    val container: Container = shortcut.container
    val arm64 = container.getWineVersion().contains("arm64ec", true)

    var name by mutableStateOf(shortcut.name)
    var screen by mutableStateOf(normalizeResolution(shortcut.getExtra("screenSize", container.getScreenSize())))
    var renderer by mutableStateOf(if (shortcut.getUseDisplayX()) "DisplayX" else if (shortcut.getRendererNative()) "EGL" else "Vulkan")
    var presentMode by mutableStateOf(shortcut.getRendererPresentMode())
    var rendererDriver by mutableStateOf(shortcut.getRendererDriverId())
    var filterMode by mutableIntStateOf(shortcut.getRendererFilterMode())
    var surfaceFormat by mutableStateOf(shortcut.getSurfaceFormat())
    var trueDisplayX by mutableStateOf(shortcut.getTrueDisplayX())
    var displayXPerformanceMode by mutableStateOf(shortcut.getDisplayXPerformanceMode())
    var displayXPresentAtRefreshRate by mutableStateOf(shortcut.getDisplayXPresentAtRefreshRate())
    var displayXBackPressure by mutableStateOf(shortcut.getDisplayXBackPressure())
    var displayXPrecisePresentation by mutableStateOf(shortcut.getDisplayXPrecisePresentation())
    var graphicsDriver by mutableStateOf(StringUtils.parseIdentifier(shortcut.getExtra("graphicsDriver", container.getGraphicsDriver())))
    private val defaultDriverVersion = runCatching {
        val context = container.manager.context
        if (GPUInformation.isDriverSupported(DefaultVersion.WRAPPER_ADRENO, context)) DefaultVersion.WRAPPER_ADRENO else DefaultVersion.WRAPPER
    }.getOrDefault(DefaultVersion.WRAPPER)
    var graphicsConfig by mutableStateOf(
        shortcut.getExtra("graphicsDriverConfig", container.getGraphicsDriverConfig()).let { raw ->
            if (readConfig(raw, "version", ';').isBlank()) writeConfig(raw, "version", defaultDriverVersion, ';') else raw
        }
    )
    var driverVersion by mutableStateOf(readConfig(graphicsConfig, "version", ';').ifBlank { defaultDriverVersion })
    var vulkanVersion by mutableStateOf(readConfig(graphicsConfig, "vulkanVersion", ';').ifBlank { "1.3" })
    var maxMemory by mutableStateOf(readConfig(graphicsConfig, "maxDeviceMemory", ';').ifBlank { "0" })
    var graphicsPresentMode by mutableStateOf(readConfig(graphicsConfig, "presentMode", ';').ifBlank { "mailbox" })
    var syncFrame by mutableStateOf(readConfig(graphicsConfig, "syncFrame", ';') == "1")
    var disablePresentWait by mutableStateOf(readConfig(graphicsConfig, "disablePresentWait", ';') == "1")
    var resourceType by mutableStateOf(readConfig(graphicsConfig, "resourceType", ';').ifBlank { "auto" })
    var bcn by mutableStateOf(readConfig(graphicsConfig, "bcnEmulation", ';').ifBlank { "auto" })
    var bcnType by mutableStateOf(readConfig(graphicsConfig, "bcnEmulationType", ';').ifBlank { "compute" })
    var bcnCache by mutableStateOf(readConfig(graphicsConfig, "bcnEmulationCache", ';') == "1")
    var gpuName by mutableStateOf(readConfig(graphicsConfig, "gpuName", ';').ifBlank { "Device" })
    var blacklistedExtensions by mutableStateOf(readConfig(graphicsConfig, "blacklistedExtensions", ';'))

    var audio by mutableStateOf(StringUtils.parseIdentifier(shortcut.getExtra("audioDriver", container.getAudioDriver())))
    var oboeProfile by mutableStateOf(shortcut.getExtra("oboeProfile", container.getExtra("oboeProfile", "low")))
    var oboeApi by mutableStateOf(shortcut.getExtra("oboeApi", container.getExtra("oboeApi", "auto")))
    var oboeAdaptive by mutableStateOf(shortcut.getExtra("oboeAdaptive", container.getExtra("oboeAdaptive", "1")) != "0")
    var oboeExclusive by mutableStateOf(shortcut.getExtra("oboeExclusive", container.getExtra("oboeExclusive", "0")) == "1")
    var wrapper by mutableStateOf(StringUtils.parseIdentifier(shortcut.getExtra("dxwrapper", container.getDXWrapper())))
    var wrapperConfig by mutableStateOf(shortcut.getExtra("dxwrapperConfig", container.getDXWrapperConfig()))
    var dxvkVersion by mutableStateOf(readConfig(wrapperConfig, "version", ',').ifBlank { DefaultVersion.DXVK })
    var vkd3dVersion by mutableStateOf(readConfig(wrapperConfig, "vkd3dVersion", ',').ifBlank { DefaultVersion.VKD3D })
    var vkd3dLevel by mutableStateOf(readConfig(wrapperConfig, "vkd3dLevel", ',').ifBlank { "12_1" })
    var frameRate by mutableStateOf(readConfig(wrapperConfig, "framerate", ',').ifBlank { "0" })
    var maxFrameLatency by mutableStateOf(readConfig(wrapperConfig, "maxFrameLatency", ',') == "1")
    var async by mutableStateOf(readConfig(wrapperConfig, "async", ',') == "1")
    var asyncCache by mutableStateOf(readConfig(wrapperConfig, "asyncCache", ',') == "1")
    var ddrawWrapper by mutableStateOf(readConfig(wrapperConfig, "ddrawrapper", ',').ifBlank { "wined3d" })
    var csmt by mutableStateOf(readConfig(wrapperConfig, "csmt", ',') != "0")
    var strictShaderMath by mutableStateOf(readConfig(wrapperConfig, "strict_shader_math", ',') == "1")
    var offscreenMode by mutableStateOf(readConfig(wrapperConfig, "OffscreenRenderingMode", ',').ifBlank { "fbo" })
    var wineRenderer by mutableStateOf(readConfig(wrapperConfig, "renderer", ',').ifBlank { "vulkan" })
    var videoMemory by mutableStateOf(readConfig(wrapperConfig, "videoMemorySize", ',').ifBlank { "2048" })

    private val emulatorRaw = StringUtils.parseIdentifier(shortcut.getExtra("emulator", container.getEmulator()))
    var emulator by mutableStateOf(if (!arm64) "Box64" else if (emulatorRaw.equals("FEXCore", true)) "FEXCore" else "WOWBox64")
    var fexVersion by mutableStateOf(shortcut.getExtra("fexcoreVersion", container.getFEXCoreVersion().orEmpty()))
    var boxVersion by mutableStateOf(shortcut.getExtra("box64Version", container.getBox64Version().orEmpty()))
    var fexPreset by mutableStateOf(shortcut.getExtra("fexcorePreset", container.getFEXCorePreset()))
    var boxPreset by mutableStateOf(shortcut.getExtra("box64Preset", container.getBox64Preset()))

    var controlsProfile by mutableStateOf(shortcut.getExtra("controlsProfile", "")) // AGVN: missing = automatic layout, "0" = off
    var fullscreen by mutableStateOf(shortcut.getExtra("fullscreenStretched", "0") == "1")
    private var inputType by mutableIntStateOf(shortcut.getExtra("inputType", container.getInputType().toString()).toIntOrNull() ?: container.getInputType())
    var exclusive by mutableStateOf(shortcut.getExtra("exclusiveXInput").let { if (it.isBlank()) container.isExclusiveXInput() else it == "1" })
    var xinput by mutableStateOf((inputType and WinHandler.FLAG_INPUT_TYPE_XINPUT.toInt()) != 0)
    var dinput by mutableStateOf((inputType and WinHandler.FLAG_INPUT_TYPE_DINPUT.toInt()) != 0)
    var disableXInput by mutableStateOf(shortcut.getExtra("disableXinput", "0") == "1")
    var relativeMouse by mutableStateOf(shortcut.getExtra("enableRelativeMouse", "0") == "1")
    var disableMouse by mutableStateOf(shortcut.getExtra("disableMouse", "0") == "1")
    var simulatedTouch by mutableStateOf(shortcut.getExtra("simTouchScreen", "0") == "1")
    var syncCpu by mutableStateOf(shortcut.getExtra("syncCpuTopology", if (container.isSyncCpuTopology()) "1" else "0") == "1")
    val cpu = mutableStateListOf<Boolean>().apply {
        val count = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val selected = shortcut.getExtra("cpuList", container.getCPUList(true)).split(',').map(String::trim).toSet()
        addAll(List(count) { it.toString() in selected })
    }

    var startup by mutableIntStateOf(shortcut.getExtra("startupSelection", container.getStartupSelection().toString()).toIntOrNull()?.coerceIn(0, 2) ?: 0)
    var sharpnessEffect by mutableStateOf(shortcut.getExtra("sharpnessEffect", "None"))
    var sharpnessLevel by mutableStateOf(shortcut.getExtra("sharpnessLevel", "100"))
    var sharpnessDenoise by mutableStateOf(shortcut.getExtra("sharpnessDenoise", "100"))
    var lcAll by mutableStateOf(shortcut.getExtra("lc_all", container.getLC_ALL()))
    var midiSoundFont by mutableStateOf(shortcut.getExtra("midiSoundFont", container.getMIDISoundFont()))
    var gameSavesEnabled by mutableStateOf(GameSaveManager.isEnabled(shortcut))
    var autoSaveBackup by mutableStateOf(GameSaveManager.isAutoBackupEnabled(shortcut))
    var execArgs by mutableStateOf(shortcut.getExtra("execArgs"))
    var autoMesaGlVersionOverride by mutableStateOf(
        shortcut.getExtra(
            "autoMesaGlVersionOverride",
            container.getExtra("autoMesaGlVersionOverride", "0")
        ) == "1"
    )
    var envVars by mutableStateOf(shortcut.getExtra("envVars", container.getEnvVars()))
    var winlatorHudMode by mutableIntStateOf(
        shortcut.getExtra("hudMode", container.getExtra("hudMode", if (container.isShowFPS) "1" else "0")).toIntOrNull() ?: 0
    )
    var dxvkHud by mutableStateOf(!envValue(envVars, "DXVK_HUD").isNullOrBlank())
    var renderingMode by mutableStateOf(
        when {
            envValue(envVars, "TU_AUTOTUNE_ALGO").equals("profiled", true) -> "Autotuner Profiled"
            envValue(envVars, "TU_DEBUG")?.split(',')?.any { it.equals("gmem", true) } == true -> "Gmem"
            envValue(envVars, "TU_DEBUG")?.split(',')?.any { it.equals("sysmem", true) } == true -> "Sysmem"
            else -> "None"
        }
    )
    val components = mutableStateMapOf<String, Int>().apply {
        putAll(parseShortcutComponentsV2(shortcut.getExtra("wincomponents", container.getWinComponents())))
    }

    var revision by mutableIntStateOf(0)
    var installing by mutableStateOf<Set<String>>(emptySet())

    private fun save() = shortcut.saveData()

    fun extra(key: String, value: String?) {
        shortcut.putExtra(key, value)
        save()
    }

    fun graphics(key: String, value: String) {
        graphicsConfig = writeConfig(graphicsConfig, key, value, ';')
        shortcut.putExtra("graphicsDriverConfig", graphicsConfig)
        save()
    }

    fun wrapperValue(key: String, value: String) {
        wrapperConfig = writeConfig(wrapperConfig, key, value, ',')
        shortcut.putExtra("dxwrapperConfig", wrapperConfig)
        save()
    }

    fun selectDxvkVersion(version: String) {
        dxvkVersion = version
        wrapperValue("version", version)
        when (dxvkAsyncMode(version)) {
            DxvkAsyncMode.NONE -> {
                async = false; asyncCache = false
                wrapperValue("async", "0"); wrapperValue("asyncCache", "0")
            }
            DxvkAsyncMode.ASYNC -> {
                async = true; asyncCache = false
                wrapperValue("async", "1"); wrapperValue("asyncCache", "0")
            }
            DxvkAsyncMode.GPL_ASYNC -> {
                async = true; asyncCache = true
                wrapperValue("async", "1"); wrapperValue("asyncCache", "1")
            }
        }
    }

    fun setEnvironment(raw: String) {
        if (autoMesaGlVersionOverride && envValue(raw, "MESA_GL_VERSION_OVERRIDE") != "3.3") {
            autoMesaGlVersionOverride = false
            shortcut.putExtra("autoMesaGlVersionOverride", "0")
        }
        envVars = raw
        shortcut.putExtra("envVars", raw.ifBlank { null })
        save()
    }

    fun selectGraphicsDriver(value: String) {
        graphicsDriver = value
        shortcut.putExtra("graphicsDriver", value)
        if (value.equals("freedreno", true)) {
            if (envValue(envVars, "MESA_GL_VERSION_OVERRIDE").isNullOrBlank()) {
                envVars = envPut(envVars, "MESA_GL_VERSION_OVERRIDE", "3.3")
                autoMesaGlVersionOverride = true
            }
        } else if (autoMesaGlVersionOverride) {
            if (envValue(envVars, "MESA_GL_VERSION_OVERRIDE") == "3.3") {
                envVars = envPut(envVars, "MESA_GL_VERSION_OVERRIDE", null)
            }
            autoMesaGlVersionOverride = false
        }
        shortcut.putExtra("autoMesaGlVersionOverride", if (autoMesaGlVersionOverride) "1" else "0")
        shortcut.putExtra("envVars", envVars.ifBlank { null })
        save()
    }

    fun syncOpenGlEnvironment() {
        if (graphicsDriver.equals("freedreno", true) &&
            envValue(envVars, "MESA_GL_VERSION_OVERRIDE").isNullOrBlank()
        ) {
            envVars = envPut(envVars, "MESA_GL_VERSION_OVERRIDE", "3.3")
            autoMesaGlVersionOverride = true
            shortcut.putExtra("autoMesaGlVersionOverride", "1")
            shortcut.putExtra("envVars", envVars)
            save()
        }
    }

    fun updateDxvkHud(enabled: Boolean) {
        dxvkHud = enabled
        setEnvironment(envPut(envVars, "DXVK_HUD", if (enabled) "fps,devinfo,gpuload,version" else null))
    }

    fun applyRenderingMode(mode: String) {
        renderingMode = mode
        var next = envPut(envVars, "TU_DEBUG", null)
        next = envPut(next, "TU_AUTOTUNE_ALGO", null)
        next = when (mode) {
            "Sysmem" -> envPut(next, "TU_DEBUG", "sysmem")
            "Gmem" -> envPut(next, "TU_DEBUG", "gmem")
            "Autotuner Profiled" -> envPut(next, "TU_AUTOTUNE_ALGO", "profiled")
            else -> next
        }
        setEnvironment(next)
    }

    fun saveRenderer() {
        shortcut.setRendererNative(renderer == "EGL")
        shortcut.setUseDisplayX(renderer == "DisplayX")
        shortcut.setRendererPresentMode(presentMode)
        shortcut.setRendererDriverId(rendererDriver)
        shortcut.setRendererFilterMode(filterMode)
        shortcut.setSurfaceFormat(surfaceFormat)
        shortcut.setTrueDisplayX(trueDisplayX)
        shortcut.setDisplayXPerformanceMode(displayXPerformanceMode)
        shortcut.setDisplayXPresentAtRefreshRate(displayXPresentAtRefreshRate)
        shortcut.setDisplayXBackPressure(displayXBackPressure)
        shortcut.setDisplayXPrecisePresentation(displayXPrecisePresentation)
        save()
    }

    fun saveInput() {
        inputType = 0
        if (xinput) inputType = inputType or WinHandler.FLAG_INPUT_TYPE_XINPUT.toInt()
        if (dinput) inputType = inputType or WinHandler.FLAG_INPUT_TYPE_DINPUT.toInt()
        shortcut.putExtra("inputType", inputType.toString())
        shortcut.putExtra("exclusiveXInput", if (exclusive) "1" else "0")
        save()
    }

    fun saveCpu() {
        shortcut.putExtra("cpuList", cpu.indices.filter { cpu[it] }.joinToString(","))
        shortcut.putExtra("syncCpuTopology", if (syncCpu) "1" else "0")
        save()
    }

    fun saveComponents() {
        shortcut.putExtra("wincomponents", shortcutComponentRowsV2.joinToString(",") { (key, _) -> "$key=${components[key] ?: 0}" })
        save()
    }
}

@Composable
internal fun ShortcutEditorV2(fragment: Fragment, shortcut: Shortcut, close: () -> Unit) {
    val context = fragment.requireContext()
    val state = remember(shortcut.file.path) {
        OpenGLDriverDefaults.initialize(context, shortcut.container)
        ShortcutEditorStateV2(shortcut)
    }
    val scope = rememberCoroutineScope()
    LaunchedEffect(shortcut.file.path) {
        state.syncOpenGlEnvironment()
    }
    val manager = remember { ContainerManager(context) }
    val containers = remember { manager.containers.toList() }

    val runtimeOptions by produceState<List<WineRuntimeOption>>(emptyList()) {
        value = loadWineRuntimeOptions(context)
    }
    fun environmentLabel(container: Container): String {
        val runtime = runtimeOptions.firstOrNull { it.id == container.wineVersion }?.label
            ?: container.wineVersion
        return "${container.name} · $runtime"
    }

    val screenEntries = remember { context.resources.getStringArray(R.array.screen_size_entries).toList() }
    val graphicsEntries = remember { context.resources.getStringArray(R.array.graphics_driver_entries).toList() }
    val audioEntries = remember { context.resources.getStringArray(R.array.audio_driver_entries).toList() }
    val wrapperEntries = remember { context.resources.getStringArray(R.array.dxwrapper_entries).toList() }
    val localeEntries = remember { listOf("Mặc định") + context.resources.getStringArray(R.array.some_lc_all).toList() }
    val soundFonts = remember { loadShortcutSoundFontsV2(context) }
    val gpuNames = remember { loadShortcutGpuNamesV2(context) }
    val fexPresets = remember { FEXCorePresetManager.getPresets(context).associate { it.id to it.name } }
    val boxPresets = remember { Box64PresetManager.getPresets("box64", context).associate { it.id to it.name } }
    val profiles = remember {
        linkedMapOf("" to context.getString(R.string.agvn_controls_auto), "0" to "Tắt phím ảo").apply { // AGVN: "" = AGVN picks the layout
            InputControlsManager(context).getProfiles(true).forEach { put(it.id.toString(), it.name) }
        }
    }
    val catalog by produceState<SettingsCatalog?>(null, state.arm64, state.revision, state.dxvkVersion, state.vkd3dVersion, state.driverVersion) {
        value = loadSettingsCatalog(
            context, state.arm64, state.dxvkVersion, state.vkd3dVersion,
            state.fexVersion, state.boxVersion, state.driverVersion
        )
    }

    fun installRuntime(type: String, version: String, done: (String) -> Unit) {
        val key = "$type:$version"
        if (key in state.installing) return
        state.installing = state.installing + key
        scope.launch {
            val installed = installRuntimeComponent(context, type, version)
            state.installing = state.installing - key
            if (installed == null) Toast.makeText(context, "Không cài được $version", Toast.LENGTH_SHORT).show()
            else {
                done(installed)
                state.revision++
            }
        }
    }

    fun installDriver(option: DriverOption) {
        val key = "driver:${option.remoteUrl ?: option.id}"
        if (key in state.installing) return
        state.installing = state.installing + key
        scope.launch {
            val installed = installAdrenoDriver(context, option)
            state.installing = state.installing - key
            if (installed == null) Toast.makeText(context, "Không cài được ${option.label}", Toast.LENGTH_SHORT).show()
            else {
                state.driverVersion = installed
                state.graphics("version", installed)
                state.revision++
            }
        }
    }

    fun closeEditor() {
        renameShortcutV2(shortcut, state.name)
        if (fragment is ShortcutsFragment) fragment.loadShortcutsList()
        close()
    }

    fun enterContainer() {
        val activity = fragment.requireActivity()
        if (!XrActivity.isEnabled(context)) {
            activity.startActivity(Intent(activity, XServerDisplayActivity::class.java).putExtra("container_id", state.container.id))
        } else {
            XrActivity.openIntent(activity, state.container.id, null)
        }
    }

    fun createContainer() {
        context.startActivity(Intent(context, ContainersSettingsActivity::class.java))
    }

    fun changeContainer(targetId: Int) {
        val target = containers.firstOrNull { it.id == targetId } ?: return
        if (target.id == state.container.id) return
        if (shortcut.cloneToContainer(target)) {
            Toast.makeText(context, "Đã chép lối tắt sang ${target.name}", Toast.LENGTH_SHORT).show()
            if (fragment is ShortcutsFragment) fragment.loadShortcutsList()
            close()
        }
    }

    var category by remember { mutableStateOf("Chung") }
    val categories = listOf(
        ShortcutCategoryItemV2("Chung", Icons.Outlined.Settings),
        ShortcutCategoryItemV2("Hình ảnh", Icons.Outlined.Monitor),
        ShortcutCategoryItemV2("Tương thích", Icons.Outlined.Tune),
        ShortcutCategoryItemV2("Điều khiển", Icons.Outlined.Gamepad),
        ShortcutCategoryItemV2("Môi trường chạy", Icons.Outlined.Terminal),
        ShortcutCategoryItemV2("Nâng cao", Icons.Outlined.Terminal)
    )
    val landscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp

    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(state.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = ::closeEditor) { Icon(Icons.Outlined.ArrowBack, null) } }
            )
        }
    ) { padding ->
        if (landscape) {
            Row(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    Modifier.width(220.dp).fillMaxHeight(),
                    shape = RoundedCornerShape(15.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        item {
                            Text(
                                "Cài đặt lối tắt",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                        items(categories) { item ->
                            ShortcutNavItemV2(item.label, item.icon, category == item.label) { category = item.label }
                        }
                    }
                }
                LazyColumn(
                    Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(category) {
                        ShortcutCategoryV2(
                            category, state, catalog, screenEntries, graphicsEntries, audioEntries,
                            wrapperEntries, localeEntries, soundFonts, gpuNames, fexPresets, boxPresets,
                            profiles, containers, ::environmentLabel, ::changeContainer, ::createContainer, ::enterContainer,
                            ::installRuntime, ::installDriver, context
                        )
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding)) {
                LazyRow(
                    Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(categories) { item ->
                        ShortcutNavItemV2(item.label, item.icon, category == item.label) { category = item.label }
                    }
                }
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 14.dp),
                    contentPadding = PaddingValues(bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(category) {
                        ShortcutCategoryV2(
                            category, state, catalog, screenEntries, graphicsEntries, audioEntries,
                            wrapperEntries, localeEntries, soundFonts, gpuNames, fexPresets, boxPresets,
                            profiles, containers, ::environmentLabel, ::changeContainer, ::createContainer, ::enterContainer,
                            ::installRuntime, ::installDriver, context
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortcutGameSavesCard(s: ShortcutEditorStateV2, context: Context) {
    val globalEnabled = GameSaveManager.isGlobalAutoBackupEnabled(context)
    val active = globalEnabled || s.gameSavesEnabled
    val scope = rememberCoroutineScope()
    var roots by remember(s.shortcut.file.path) { mutableStateOf<List<String>>(emptyList()) }
    var latestBackup by remember(s.shortcut.file.path) {
        mutableStateOf(GameSaveManager.getLatestBackup(s.shortcut))
    }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(active, refreshKey) {
        if (active) {
            roots = withContext(Dispatchers.IO) { GameSaveManager.getSaveRoots(s.shortcut) }
            latestBackup = GameSaveManager.getLatestBackup(s.shortcut)
        } else {
            roots = emptyList()
            latestBackup = GameSaveManager.getLatestBackup(s.shortcut)
        }
    }

    SettingsCard {
        SettingToggle(
            "Lưu game",
            active,
            enabled = !globalEnabled
        ) { enabled ->
            s.gameSavesEnabled = enabled
            if (!enabled) s.autoSaveBackup = false
            GameSaveManager.setEnabled(s.shortcut, enabled)
        }

        if (globalEnabled) {
            SettingsDivider()
            Text(
                "Đang bật chung trong Cài đặt. Mọi game được sao lưu khi thoát.",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (active) {
            SettingsDivider()
            SettingToggle(
                "Tự động sao lưu",
                globalEnabled || s.autoSaveBackup,
                enabled = !globalEnabled
            ) { enabled ->
                s.autoSaveBackup = enabled
                GameSaveManager.setAutoBackupEnabled(s.shortcut, enabled)
            }

            SettingsDivider()
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text("Thư mục sao lưu", style = MaterialTheme.typography.labelLarge)
                Text(
                    "AGVN-Player/Saves/${GameSaveManager.getGameDir(s.shortcut).name}/",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("Bản sao lưu mới nhất", style = MaterialTheme.typography.labelLarge)
                Text(
                    latestBackup?.let(::shortcutBackupLabel) ?: "Chưa có bản sao lưu",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("Vị trí lưu game", style = MaterialTheme.typography.labelLarge)
                if (roots.isEmpty()) {
                    Text(
                        "Không tìm thấy thư mục lưu riêng.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    roots.forEach { root ->
                        Text("• $root", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            SettingsDivider()
            OutlinedButton(
                onClick = {
                    if (!busy) {
                        busy = true
                        message = "Đang quét vị trí lưu game…"
                        scope.launch {
                            roots = withContext(Dispatchers.IO) {
                                GameSaveManager.rediscoverSaveRoots(s.shortcut)
                            }
                            message = if (roots.isEmpty()) {
                                "Không tìm thấy thư mục lưu riêng của game."
                            } else {
                                "Tìm thấy ${roots.size} vị trí lưu game."
                            }
                            refreshKey++
                            busy = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                enabled = !busy
            ) {
                Text("Quét lại vị trí lưu game")
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        if (!busy) {
                            busy = true
                            message = "Đang sao lưu…"
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    GameSaveManager.backup(s.shortcut, false)
                                }
                                message = when {
                                    result.ok && result.wholeProfile ->
                                        "Sao lưu xong: ${result.fileCount} file. Đã sao lưu cả hồ sơ Wine vì không tìm thấy thư mục lưu riêng của game."
                                    result.ok -> "Sao lưu xong: ${result.fileCount} file."
                                    else -> "Sao lưu thất bại: ${result.error ?: "lỗi không rõ"}"
                                }
                                refreshKey++
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !busy
                ) {
                    Text("Sao lưu ngay")
                }

                OutlinedButton(
                    onClick = {
                        if (!busy) {
                            busy = true
                            message = "Đang khôi phục bản sao lưu mới nhất…"
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    GameSaveManager.restoreLatest(s.shortcut)
                                }
                                message = if (result.ok) {
                                    "Đã khôi phục ${result.fileCount} file."
                                } else {
                                    "Khôi phục thất bại: ${result.error ?: "lỗi không rõ"}"
                                }
                                refreshKey++
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !busy && latestBackup != null
                ) {
                    Text("Khôi phục")
                }
            }

            message?.let {
                Text(
                    it,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun shortcutBackupLabel(file: File): String {
    val date = SimpleDateFormat("dd MMM yyyy • HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
    return "${file.name}\n$date"
}

@Composable
private fun ShortcutNavItemV2(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, null, modifier = Modifier.size(16.dp))
            Text(
                label,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun ShortcutCategoryV2(
    category: String,
    s: ShortcutEditorStateV2,
    catalog: SettingsCatalog?,
    screenEntries: List<String>,
    graphicsEntries: List<String>,
    audioEntries: List<String>,
    wrapperEntries: List<String>,
    localeEntries: List<String>,
    soundFonts: List<String>,
    gpuNames: List<String>,
    fexPresets: Map<String, String>,
    boxPresets: Map<String, String>,
    profiles: Map<String, String>,
    containers: List<Container>,
    environmentLabel: (Container) -> String,
    changeContainer: (Int) -> Unit,
    createContainer: () -> Unit,
    enterContainer: () -> Unit,
    installRuntime: (String, String, (String) -> Unit) -> Unit,
    installDriver: (DriverOption) -> Unit,
    context: Context
) {
    when (category) {
        "Chung" -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsCard { SettingText("Tên", s.name) { s.name = it } }
            SettingsCard {
                val currentLabel = environmentLabel(s.container)
                if (containers.size > 1) {
                    val labels = containers.associate { it.id to environmentLabel(it) }
                    SettingChoice("Môi trường chạy", currentLabel, labels.values.toList()) { selected ->
                        labels.entries.firstOrNull { it.value == selected }?.key?.let(changeContainer)
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Môi trường chạy", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(currentLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        }
                        IconButton(onClick = createContainer) { Icon(Icons.Outlined.Add, "Tạo môi trường chạy") }
                    }
                }
                SettingsDivider()
                Button(onClick = enterContainer, modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Icon(Icons.Outlined.PlayArrow, null)
                    Spacer(Modifier.size(7.dp))
                    Text("Vào môi trường chạy")
                }
            }
            SettingsCard {
                SettingChoice("Driver âm thanh", audioEntries.firstOrNull { StringUtils.parseIdentifier(it).equals(s.audio, true) } ?: s.audio, audioEntries) {
                    s.audio = StringUtils.parseIdentifier(it); s.extra("audioDriver", s.audio)
                }
                if (s.audio == "oboe") {
                    SettingsDivider()
                    val latencyLabel = when (s.oboeProfile) {
                        "ultra" -> "Độ trễ thấp"
                        "stable" -> "Ổn định"
                        else -> "Tự động"
                    }
                    SettingChoice("Độ trễ Oboe", latencyLabel, listOf("Tự động", "Độ trễ thấp", "Ổn định")) {
                        s.oboeProfile = when (it) {
                            "Độ trễ thấp" -> "ultra"
                            "Ổn định" -> "stable"
                            else -> "low"
                        }
                        s.extra("oboeProfile", s.oboeProfile)
                    }
                    SettingsDivider()
                    val apiLabel = when (s.oboeApi) {
                        "aaudio" -> "AAudio"
                        "opensles" -> "OpenSL ES"
                        else -> "Tự động"
                    }
                    SettingChoice("Backend Oboe", apiLabel, listOf("Tự động", "AAudio", "OpenSL ES")) {
                        s.oboeApi = when (it) {
                            "AAudio" -> "aaudio"
                            "OpenSL ES" -> "opensles"
                            else -> "auto"
                        }
                        s.extra("oboeApi", s.oboeApi)
                    }
                }
                SettingsDivider()
                SettingToggle("Kéo giãn toàn màn hình", s.fullscreen) {
                    s.fullscreen = it; s.extra("fullscreenStretched", if (it) "1" else null)
                }
            }
            SettingsCard {
                val hudEntries = listOf("Tắt", "Cổ điển", "Hiện đại")
                SettingChoice("HUD", hudEntries.getOrElse(s.winlatorHudMode) { "Tắt" }, hudEntries) {
                    s.winlatorHudMode = hudEntries.indexOf(it).coerceAtLeast(0)
                    s.extra("hudMode", s.winlatorHudMode.toString())
                }
                SettingsDivider()
                SettingToggle("DXVK HUD", s.dxvkHud) { s.updateDxvkHud(it) }
            }
            SettingsCard {
                val locale = localeDisplayValue(s.lcAll)
                SettingChoice("Ngôn ngữ vùng (LC_ALL)", locale, localeEntries) {
                    s.lcAll = normalizeLocaleValue(it)
                    s.extra("lc_all", s.lcAll.ifBlank { null })
                }
                SettingsDivider()
                val sound = s.midiSoundFont.ifBlank { "Tắt" }
                SettingChoice("MIDI SoundFont", sound, soundFonts) {
                    s.midiSoundFont = if (it == "Tắt") "" else it; s.extra("midiSoundFont", s.midiSoundFont.ifBlank { null })
                }
            }
            ShortcutGameSavesCard(s, context)
        }

        "Hình ảnh" -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsCard {
                var customScreenSelected by remember {
                    mutableStateOf(screenEntries.none { normalizeResolution(it).equals(s.screen, true) })
                }
                val screenChoice = if (customScreenSelected) "Custom"
                else screenEntries.firstOrNull { normalizeResolution(it).equals(s.screen, true) } ?: "Custom"
                SettingChoice("Độ phân giải", screenChoice, screenEntries) {
                    if (it.equals("Custom", true)) {
                        customScreenSelected = true
                    } else {
                        customScreenSelected = false
                        s.screen = normalizeResolution(it)
                        s.extra("screenSize", s.screen)
                    }
                }
                if (screenChoice == "Custom") {
                    SettingsDivider()
                    SettingText("Độ phân giải tùy chỉnh", s.screen) { value ->
                        s.screen = value
                        if (Regex("\\d{2,5}x\\d{2,5}").matches(value.trim())) s.extra("screenSize", normalizeResolution(value))
                    }
                }
                SettingsDivider()
                SettingChoice("Bộ dựng hình", s.renderer, listOf("Vulkan", "EGL", "DisplayX")) {
                    s.renderer = it
                    s.surfaceFormat = if (it == "DisplayX") "rgba8" else "bgra8"
                    if (it == "EGL" && s.filterMode > 1) s.filterMode = 0
                    s.saveRenderer()
                }
                SettingsDivider()
                SettingChoice(
                    "Định dạng bề mặt",
                    if (s.surfaceFormat == "bgra8") "BGRA" else "RGBA",
                    listOf("RGBA", "BGRA")
                ) {
                    s.surfaceFormat = if (it == "BGRA") "bgra8" else "rgba8"
                    s.saveRenderer()
                }
                if (s.renderer == "DisplayX") {
                    SettingsDivider()
                    SettingToggle("Bỏ qua X11", s.trueDisplayX) {
                        s.trueDisplayX = it
                        s.saveRenderer()
                    }
                    SettingsDivider()
                    SettingToggle("Chế độ hiệu năng", s.displayXPerformanceMode) {
                        s.displayXPerformanceMode = it
                        s.saveRenderer()
                    }
                    SettingsDivider()
                    SettingToggle("Trình chiếu theo tần số quét", s.displayXPresentAtRefreshRate) {
                        s.displayXPresentAtRefreshRate = it
                        s.saveRenderer()
                    }
                    SettingsDivider()
                    SettingToggle("Gửi mọi buffer", s.displayXBackPressure) {
                        s.displayXBackPressure = it
                        s.saveRenderer()
                    }
                    SettingsDivider()
                    SettingToggle("Trình chiếu chính xác", s.displayXPrecisePresentation) {
                        s.displayXPrecisePresentation = it
                        s.saveRenderer()
                    }
                } else {
                    if (s.renderer != "EGL") {
                        SettingsDivider()
                        SettingChoice("Chế độ trình chiếu", s.presentMode, listOf("mailbox", "fifo")) {
                            s.presentMode = it
                            s.saveRenderer()
                        }
                        catalog?.let { c ->
                            SettingsDivider()
                            SettingMappedChoice("Driver dựng hình", s.rendererDriver, c.rendererDrivers) {
                                s.rendererDriver = it
                                s.saveRenderer()
                            }
                        }
                    }
                    SettingsDivider()
                    val filters = if (s.renderer == "EGL") listOf("Bilinear", "Điểm gần nhất")
                    else listOf("Bilinear", "Điểm gần nhất", "Snapdragon Super Resolution", "AMD FidelityFX Super Resolution", "Lanczos 2 (16-tap)", "Tăng màu")
                    SettingChoice("Lọc texture", filters.getOrElse(s.filterMode) { filters.first() }, filters) {
                        s.filterMode = filters.indexOf(it).coerceAtLeast(0)
                        s.saveRenderer()
                    }
                }
            }
            SettingsCard {
                SettingChoice("Driver đồ họa", graphicsEntries.firstOrNull { StringUtils.parseIdentifier(it).equals(s.graphicsDriver, true) } ?: s.graphicsDriver, graphicsEntries) {
                    s.selectGraphicsDriver(StringUtils.parseIdentifier(it))
                }
                catalog?.let { c ->
                    SettingsDivider()
                    SettingDriverChoice("Phiên bản driver", s.driverVersion, c.drivers, s.installing, installDriver) {
                        s.driverVersion = it; s.graphics("version", it)
                    }
                }
                SettingsDivider()
                ShortcutVulkanExtensionsV2(context, s.driverVersion, s.blacklistedExtensions) {
                    s.blacklistedExtensions = it; s.graphics("blacklistedExtensions", it)
                }
                if (isTurnipDriver(s.driverVersion)) {
                    SettingsDivider()
                    SettingChoice("Chế độ dựng hình", s.renderingMode, listOf("None", "Sysmem", "Gmem", "Autotuner Profiled")) { s.applyRenderingMode(it) }
                }
                SettingsDivider()
                SettingChoice("Phiên bản Vulkan", s.vulkanVersion, listOf("1.1", "1.2", "1.3")) { s.vulkanVersion = it; s.graphics("vulkanVersion", it) }
                SettingsDivider()
                SettingChoice("Tên GPU", s.gpuName, gpuNames) { s.gpuName = it; s.graphics("gpuName", it) }
                SettingsDivider()
                SettingChoice("Bộ nhớ thiết bị tối đa", s.maxMemory, listOf("0", "512", "1024", "2048", "4096", "8192", "12288", "16384")) {
                    s.maxMemory = it; s.graphics("maxDeviceMemory", it)
                }
                SettingsDivider()
                SettingChoice("Chế độ trình chiếu của driver", s.graphicsPresentMode, listOf("mailbox", "fifo", "immediate", "relaxed")) {
                    s.graphicsPresentMode = it; s.graphics("presentMode", it)
                }
                SettingsDivider()
                SettingToggle("Đồng bộ khung hình", s.syncFrame) { s.syncFrame = it; s.graphics("syncFrame", if (it) "1" else "0") }
                SettingsDivider()
                SettingToggle("Tắt Present Wait", s.disablePresentWait) { s.disablePresentWait = it; s.graphics("disablePresentWait", if (it) "1" else "0") }
                SettingsDivider()
                SettingChoice("Loại tài nguyên", s.resourceType, listOf("auto", "dmabuf", "ahb", "opaque")) { s.resourceType = it; s.graphics("resourceType", it) }
                SettingsDivider()
                SettingChoice("Giả lập BCN", s.bcn, listOf("none", "partial", "full", "auto")) { s.bcn = it; s.graphics("bcnEmulation", it) }
                SettingsDivider()
                SettingChoice("Kiểu giả lập BCN", s.bcnType, listOf("software", "compute")) { s.bcnType = it; s.graphics("bcnEmulationType", it) }
                SettingsDivider()
                SettingToggle("Bộ đệm giả lập BCN", s.bcnCache) { s.bcnCache = it; s.graphics("bcnEmulationCache", if (it) "1" else "0") }
            }
        }

        "Tương thích" -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsCard {
                val shown = wrapperEntries.firstOrNull { StringUtils.parseIdentifier(it).equals(s.wrapper, true) } ?: s.wrapper
                SettingChoice("DX Wrapper", shown, wrapperEntries) { s.wrapper = StringUtils.parseIdentifier(it); s.extra("dxwrapper", s.wrapper) }
                if (s.wrapper.contains("dxvk", true)) {
                    catalog?.let { c ->
                        val dxvkCatalog = filterDxvkForVkd3d(c.dxvk, s.vkd3dVersion)
                        SettingsDivider()
                        SettingInstallChoice("Phiên bản DXVK", s.dxvkVersion, dxvkCatalog, s.installing, "DXVK",
                            { v -> installRuntime("DXVK", v) { installed -> s.selectDxvkVersion(installed) } }) { s.selectDxvkVersion(it) }
                        SettingsDivider()
                        SettingInstallChoice("Phiên bản VKD3D", s.vkd3dVersion, c.vkd3d, s.installing, "VKD3D",
                            { v -> installRuntime("VKD3D", v) { installed ->
                                s.vkd3dVersion = installed
                                s.wrapperValue("vkd3dVersion", installed)
                                if (isVkd3dEnabled(installed) && !isDxvkCompatibleWithVkd3d(s.dxvkVersion)) {
                                    c.dxvk.all.firstOrNull { candidate -> isDxvkCompatibleWithVkd3d(candidate) && candidate in c.dxvk.installed }?.let(s::selectDxvkVersion)
                                }
                            } }) {
                            s.vkd3dVersion = it
                            s.wrapperValue("vkd3dVersion", it)
                            if (isVkd3dEnabled(it) && !isDxvkCompatibleWithVkd3d(s.dxvkVersion)) {
                                c.dxvk.all.firstOrNull { candidate -> isDxvkCompatibleWithVkd3d(candidate) && candidate in c.dxvk.installed }?.let(s::selectDxvkVersion)
                            }
                        }
                    }
                    SettingsDivider()
                    SettingChoice("Mức tính năng VKD3D", s.vkd3dLevel, listOf("12_0", "12_1", "12_2", "11_1", "11_0", "10_1", "10_0", "9_3", "9_2", "9_1")) { s.vkd3dLevel = it; s.wrapperValue("vkd3dLevel", it) }
                    SettingsDivider()
                    SettingText("Giới hạn FPS", s.frameRate) { s.frameRate = it.filter(Char::isDigit).take(4); s.wrapperValue("framerate", s.frameRate.ifBlank { "0" }) }
                    SettingsDivider()
                    SettingToggle("Độ trễ khung hình tối đa", s.maxFrameLatency) { s.maxFrameLatency = it; s.wrapperValue("maxFrameLatency", if (it) "1" else "0") }
                    val asyncMode = dxvkAsyncMode(s.dxvkVersion)
                    if (asyncMode != DxvkAsyncMode.NONE) {
                        SettingsDivider()
                        SettingToggle("Async", s.async) { s.async = it; s.wrapperValue("async", if (it) "1" else "0") }
                    }
                    if (asyncMode == DxvkAsyncMode.GPL_ASYNC) {
                        SettingsDivider()
                        SettingToggle("Bộ đệm Async", s.asyncCache) { s.asyncCache = it; s.wrapperValue("asyncCache", if (it) "1" else "0") }
                    }
                    SettingsDivider()
                    DDrawWrapperChoice(s.ddrawWrapper) { s.ddrawWrapper = it; s.wrapperValue("ddrawrapper", it) }
                } else {
                    SettingsDivider()
                    SettingToggle("CSMT", s.csmt) { s.csmt = it; s.wrapperValue("csmt", if (it) "3" else "0") }
                    SettingsDivider()
                    SettingToggle("Tính shader chính xác", s.strictShaderMath) { s.strictShaderMath = it; s.wrapperValue("strict_shader_math", if (it) "1" else "0") }
                    SettingsDivider()
                    SettingChoice("Chế độ vẽ ngoài màn hình", s.offscreenMode, listOf("fbo", "backbuffer")) { s.offscreenMode = it; s.wrapperValue("OffscreenRenderingMode", it) }
                    SettingsDivider()
                    SettingChoice("Bộ dựng hình Wine", s.wineRenderer, listOf("vulkan", "gl")) { s.wineRenderer = it; s.wrapperValue("renderer", it) }
                    SettingsDivider()
                    SettingText("Bộ nhớ video", s.videoMemory) { s.videoMemory = it.filter(Char::isDigit).take(6); s.wrapperValue("videoMemorySize", s.videoMemory) }
                }
            }
            SettingsCard {
                if (s.arm64) {
                    SettingChoice("Giả lập 32-bit", s.emulator, listOf("FEXCore", "WOWBox64")) {
                        s.emulator = it; s.extra("emulator", if (it == "FEXCore") "FEXCore" else "Box64")
                    }
                    catalog?.let { c ->
                        SettingsDivider()
                        SettingInstallChoice("Phiên bản FEXCore", s.fexVersion, c.fex, s.installing, "FEXCore",
                            { v -> installRuntime("FEXCore", v) { installed -> s.fexVersion = installed; s.extra("fexcoreVersion", installed) } }) {
                            s.fexVersion = it; s.extra("fexcoreVersion", it)
                        }
                    }
                    SettingsDivider()
                    SettingMappedChoice("FEXCore Preset", s.fexPreset, fexPresets) { s.fexPreset = it; s.extra("fexcorePreset", it) }
                }
                if (!s.arm64 || s.emulator == "WOWBox64") {
                    catalog?.let { c ->
                        val versionCatalog = if (s.arm64) c.wow else c.box
                        val type = if (s.arm64) "WOWBox64" else "Box64"
                        SettingsDivider()
                        SettingInstallChoice("Phiên bản $type", s.boxVersion, versionCatalog, s.installing, type,
                            { v -> installRuntime(type, v) { installed -> s.boxVersion = installed; s.extra("box64Version", installed) } }) {
                            s.boxVersion = it; s.extra("box64Version", it)
                        }
                    }
                    SettingsDivider()
                    SettingMappedChoice("Box64 Preset", s.boxPreset, boxPresets) { s.boxPreset = it; s.extra("box64Preset", it) }
                }
            }
        }

        "Điều khiển" -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsCard {
                SettingMappedChoice("Cấu hình phím ảo", s.controlsProfile, profiles) { s.controlsProfile = it; s.shortcut.putExtra(com.winlator.cmod.agvn.AgvnLayouts.EXTRA_AUTO, null); s.extra("controlsProfile", it.ifEmpty { null }) } // AGVN: player's choice; "0" = controls off, "" = automatic
                SettingsDivider()
                SettingToggle("Nhập độc quyền", s.exclusive) {
                    s.exclusive = it
                    if (!it) { s.xinput = true; s.dinput = true } else if (s.xinput && s.dinput) s.dinput = false
                    s.saveInput()
                }
                SettingsDivider()
                SettingToggle("Bật XInput", s.xinput, s.exclusive) { s.xinput = it; if (s.exclusive && it && s.dinput) s.dinput = false; s.saveInput() }
                SettingsDivider()
                SettingToggle("Bật DInput", s.dinput, s.exclusive) { s.dinput = it; if (s.exclusive && it && s.xinput) s.xinput = false; s.saveInput() }
                SettingsDivider()
                SettingToggle("Tắt XInput", s.disableXInput) { s.disableXInput = it; s.extra("disableXinput", if (it) "1" else null) }
                SettingsDivider()
                SettingToggle("Chuột tương đối", s.relativeMouse) { s.relativeMouse = it; s.extra("enableRelativeMouse", if (it) "1" else null) }
                SettingsDivider()
                SettingToggle("Tắt chuột", s.disableMouse) { s.disableMouse = it; s.extra("disableMouse", if (it) "1" else null) }
                SettingsDivider()
                SettingToggle("Giả lập màn hình cảm ứng", s.simulatedTouch) { s.simulatedTouch = it; s.extra("simTouchScreen", if (it) "1" else "0") }
            }
        }

        "Môi trường chạy" -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            EnvironmentVariablesEditor(s.envVars, onChanged = { s.setEnvironment(it) })
        }

        else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsCard {
                val startupEntries = listOf(
                    "Bình thường (tải mọi dịch vụ)",
                    "Thiết yếu (chỉ tải dịch vụ cần thiết)",
                    "Tối giản (dừng dịch vụ khi khởi động)"
                )
                SettingChoice("Chế độ khởi động", startupEntries[s.startup], startupEntries) {
                    s.startup = startupEntries.indexOf(it).coerceAtLeast(0); s.extra("startupSelection", s.startup.toString())
                }
            }
            SettingsCard {
                SettingToggle("Đồng bộ cấu trúc CPU", s.syncCpu) { s.syncCpu = it; s.saveCpu() }
                SettingsDivider()
                CpuSelectorRow("Chọn nhân CPU", s.cpu) { index, checked -> s.cpu[index] = checked; s.saveCpu() }
            }
            SettingsCard {
                SettingChoice("Hiệu ứng làm nét", s.sharpnessEffect, listOf("None", "CAS", "DLS")) {
                    s.sharpnessEffect = it; s.extra("sharpnessEffect", it)
                }
                SettingsDivider()
                SharpnessSliderV2("Mức làm nét", s.sharpnessLevel) {
                    s.sharpnessLevel = it; s.extra("sharpnessLevel", it)
                }
                SettingsDivider()
                SharpnessSliderV2("Khử nhiễu khi làm nét", s.sharpnessDenoise) {
                    s.sharpnessDenoise = it; s.extra("sharpnessDenoise", it)
                }
            }
            ExecArgumentsEditorV2(s.execArgs) {
                s.execArgs = it; s.extra("execArgs", it.ifBlank { null })
            }
            SettingsCard {
                shortcutComponentRowsV2.forEachIndexed { index, (key, label) ->
                    val entries = listOf("Có sẵn (Wine)", "Gốc (Windows)")
                    val selected = entries[(s.components[key] ?: 0).coerceIn(0, 1)]
                    SettingChoice(label, selected, entries) {
                        s.components[key] = entries.indexOf(it).coerceAtLeast(0); s.saveComponents()
                    }
                    if (index != shortcutComponentRowsV2.lastIndex) SettingsDivider()
                }
            }
        }
    }
}

@Composable
private fun SharpnessSliderV2(label: String, value: String, onChanged: (String) -> Unit) {
    val numeric = value.toFloatOrNull()?.coerceIn(0f, 100f) ?: 100f
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(numeric.roundToInt().toString(), fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = numeric,
            onValueChange = { onChanged(it.roundToInt().coerceIn(0, 100).toString()) },
            valueRange = 0f..100f
        )
    }
}

@Composable
private fun ExecArgumentsEditorV2(value: String, onChanged: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    SettingsCard {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onChanged,
                label = { Text("Tham số chạy") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )
            Box {
                TextButton(onClick = { open = true }) { Text("⋮", style = MaterialTheme.typography.headlineSmall) }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    execArgumentPresetsV2.forEach { arg ->
                        DropdownMenuItem(
                            text = { Text(arg) },
                            onClick = {
                                if (!value.split(' ').contains(arg)) onChanged(listOf(value.trim(), arg).filter(String::isNotBlank).joinToString(" "))
                                open = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortcutVulkanExtensionsV2(context: Context, driver: String, blacklisted: String, onChanged: (String) -> Unit) {
    val extensions = remember(driver) { runCatching { GPUInformation.enumerateExtensions(driver, context).toList().sorted() }.getOrDefault(emptyList()) }
    if (extensions.isEmpty()) {
        SettingText("Tiện ích Vulkan đã tắt", blacklisted, 2) { onChanged(it.replace(" ", "")) }
        return
    }
    val disabled = blacklisted.split(',').map(String::trim).filter(String::isNotBlank).toSet()
    val enabledCount = extensions.count { it !in disabled }
    var open by remember { mutableStateOf(false) }
    Surface(onClick = { open = true }, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp)) {
            Text("Tiện ích Vulkan", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Đã bật $enabledCount/${extensions.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        }
    }
    if (open) {
        val selected = remember(blacklisted, open, extensions) { mutableStateListOf<String>().apply { addAll(extensions.filter { it !in disabled }) } }
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = { onChanged(extensions.filterNot { it in selected }.joinToString(",")); open = false }) { Text("Xong") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Hủy") } },
            title = { Text("Tiện ích Vulkan") },
            text = {
                LazyColumn(Modifier.heightIn(max = 460.dp)) {
                    items(extensions) { extension ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = extension in selected,
                                onCheckedChange = { checked -> if (checked) { if (extension !in selected) selected.add(extension) } else selected.remove(extension) }
                            )
                            Text(extension, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
                        }
                    }
                }
            }
        )
    }
}

private fun loadShortcutGpuNamesV2(context: Context): List<String> {
    val result = linkedSetOf("Device")
    runCatching {
        val data = FileUtils.readString(context, "gpu_cards.json")
        val array = JSONArray(data)
        for (index in 0 until array.length()) {
            val name = array.optJSONObject(index)?.optString("name").orEmpty()
            if (name.isNotBlank()) result.add(name)
        }
    }
    return result.toList()
}

private fun loadShortcutSoundFontsV2(context: Context): List<String> {
    val result = linkedSetOf("Tắt", MidiManager.DEFAULT_SF2_FILE)
    MidiManager.getSoundFontDir(context).listFiles()?.filter { it.isFile }?.sortedBy { it.name.lowercase() }?.forEach { result.add(it.name) }
    return result.toList()
}

private fun parseShortcutComponentsV2(raw: String): Map<String, Int> {
    val result = mutableMapOf<String, Int>()
    raw.split(',').forEach { token ->
        val split = token.indexOf('=')
        if (split > 0) result[token.substring(0, split)] = token.substring(split + 1).toIntOrNull()?.coerceIn(0, 1) ?: 0
    }
    shortcutComponentRowsV2.forEach { (key, _) -> result.putIfAbsent(key, 0) }
    return result
}

private fun renameShortcutV2(shortcut: Shortcut, requested: String) {
    val clean = requested.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
    if (clean.isBlank() || clean == shortcut.name) return

    val parent = shortcut.file.parentFile
    val oldName = shortcut.name
    val ext = shortcut.file.extension.takeIf { it.isNotBlank() } ?: "desktop"
    val target = File(parent, "$clean.$ext")
    if (target.exists() || !shortcut.file.renameTo(target)) return

    val oldLink = File(parent, "$oldName.lnk")
    if (oldLink.isFile) {
        val newLink = File(parent, "$clean.lnk")
        if (!newLink.exists()) oldLink.renameTo(newLink)
    }
}


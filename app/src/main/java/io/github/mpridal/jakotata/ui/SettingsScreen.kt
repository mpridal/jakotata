package io.github.mpridal.jakotata.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.mpridal.jakotata.BuildConfig
import io.github.mpridal.jakotata.R
import io.github.mpridal.jakotata.core.profile.ProfileStore
import io.github.mpridal.jakotata.core.settings.Settings
import io.github.mpridal.jakotata.core.voice.VoiceProfileRecorder
import io.github.mpridal.jakotata.ui.camera.CameraState
import io.github.mpridal.jakotata.ui.components.GlassBox
import io.github.mpridal.jakotata.ui.components.JakoIcons
import io.github.mpridal.jakotata.ui.theme.JakoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** Nastavení (zadání kap. 6.6); rodičovská zóna je za příkladem z násobilky (kap. 6.7). */
@Composable
fun SettingsScreen(state: CameraState, settings: Settings, onCheckUpdate: () -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var parentOpen by rememberSaveable { mutableStateOf(false) }
    fun update(t: (Settings) -> Settings) { scope.launch { state.settings.update(t) } }
    val c = JakoTheme.colors

    GlassBox(Modifier.fillMaxWidth()) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundIcon(JakoIcons.Back, stringResource(R.string.action_close), onClick = { if (parentOpen) parentOpen = false else onClose() })
                Spacer(Modifier.width(12.dp))
                Text(stringResource(if (parentOpen) R.string.parent_zone else R.string.action_settings), style = MaterialTheme.typography.headlineMedium, color = c.onGlass)
            }
            if (parentOpen) {
                ParentZone(state, settings, ::update)
                return@Column
            }
            OptionRow(
                stringResource(R.string.settings_language),
                listOf(stringResource(R.string.option_system), "Čeština", "English", "Español"),
                listOf("", "cs", "en", "es").indexOf(settings.language).coerceAtLeast(0),
            ) { i -> update { it.copy(language = listOf("", "cs", "en", "es")[i]) } }
            OptionRow(
                stringResource(R.string.settings_theme),
                listOf(stringResource(R.string.option_system), stringResource(R.string.theme_light), stringResource(R.string.theme_dark)),
                settings.theme,
            ) { i -> update { it.copy(theme = i) } }
            OptionRow(stringResource(R.string.settings_quality), listOf("720p", "1080p"), if (settings.videoQuality >= 1080) 1 else 0) { i ->
                update { it.copy(videoQuality = if (i == 1) 1080 else 720) }
            }
            val lengths = listOf(30, 60, 120, 300)
            OptionRow(stringResource(R.string.settings_max_length), lengths.map { if (it < 60) "$it s" else "${it / 60} min" }, lengths.indexOf(settings.maxRecordingSec).coerceAtLeast(2)) { i ->
                update { it.copy(maxRecordingSec = lengths[i]) }
            }
            OptionRow(stringResource(R.string.settings_camera), listOf(stringResource(R.string.camera_front), stringResource(R.string.camera_back)), if (settings.frontCamera) 0 else 1) { i ->
                update { it.copy(frontCamera = i == 0) }
            }
            OptionRow(stringResource(R.string.settings_sound), listOf(stringResource(R.string.sound_parrot), stringResource(R.string.sound_silent)), if (settings.soundMode == 0) 1 else 0) { i ->
                update { it.copy(soundMode = if (i == 1) 0 else 2) }
            }
            OptionRow(stringResource(R.string.settings_faces), listOf("1", "2"), settings.maxFaces - 1) { i -> update { it.copy(maxFaces = i + 1) } }
            SwitchRow(stringResource(R.string.settings_auto_update), settings.autoUpdate) { on -> update { it.copy(autoUpdate = on) } }
            PillButton(stringResource(R.string.update_check_now), primary = false, enabled = true, onClick = onCheckUpdate)
            Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium, color = c.onGlassMuted)
            PillButton(stringResource(R.string.parent_zone), primary = true, enabled = true) { parentOpen = true }
            Text(stringResource(R.string.about_licenses), style = MaterialTheme.typography.bodyMedium, color = c.onGlassMuted)
        }
    }
}

/** Rodičovská zóna: vstup příkladem z násobilky (jako v Deskovkách). */
@Composable
private fun ParentZone(state: CameraState, settings: Settings, update: ((Settings) -> Settings) -> Unit) {
    val c = JakoTheme.colors
    val a = rememberSaveable { Random.nextInt(6, 10) }
    val b = rememberSaveable { Random.nextInt(11, 19) }
    var answer by rememberSaveable { mutableStateOf("") }
    var unlocked by rememberSaveable { mutableStateOf(false) }
    if (!unlocked) {
        Text(stringResource(R.string.parent_gate, a, b), style = MaterialTheme.typography.titleLarge, color = c.onGlass)
        OutlinedTextField(
            value = answer, onValueChange = { v -> answer = v.filter(Char::isDigit).take(4); if (answer.toIntOrNull() == a * b) unlocked = true },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    SwitchRow(stringResource(R.string.parent_watermark), settings.watermark) { on -> update { it.copy(watermark = on) } }
    SwitchRow(stringResource(R.string.parent_sharing), settings.sharingAllowed) { on -> update { it.copy(sharingAllowed = on) } }
    ProfilesSection(state)
    TrainingSection(state)
}

@Composable
private fun ProfilesSection(state: CameraState) {
    val c = JakoTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmTrash by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    Text(stringResource(R.string.parent_profiles), style = MaterialTheme.typography.titleMedium, color = c.onGlass)
    val ids = state.profileIds
    ids.forEach { id ->
        val active = id == state.activeProfileId
        val name = remember(id) { ProfileStore.name(context, id) }.ifBlank { stringResource(R.string.profile_default_name) }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp))
                .background(if (active) c.onGlass else c.onGlass.copy(alpha = 0.08f))
                .selectable(active, role = Role.RadioButton) { scope.launch { state.selectProfile(id) } }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { Text(name, style = MaterialTheme.typography.labelLarge, color = if (active) c.root else c.onGlass) }
    }
    if (ids.size < ProfileStore.MAX_PROFILES) {
        OutlinedTextField(
            value = newName, onValueChange = { newName = it.take(20) }, singleLine = true,
            label = { Text(stringResource(R.string.parent_new_profile_name)) }, modifier = Modifier.fillMaxWidth(),
        )
        PillButton(stringResource(R.string.parent_add_profile), primary = false, enabled = true) {
            state.pendingName = newName.trim()
            newName = ""
            scope.launch { state.selectProfile(ProfileStore.newId()) }  // prázdný profil → otevře se vyfocení
        }
    }
    if (state.profile != null) {
        if (!confirmTrash) PillButton(stringResource(R.string.parent_trash_profile), primary = false, enabled = true) { confirmTrash = true }
        else {
            Text(stringResource(R.string.parent_trash_confirm), style = MaterialTheme.typography.bodyLarge, color = c.onGlass)
            PillButton(stringResource(R.string.action_trash), primary = true, enabled = true) {
                confirmTrash = false
                scope.launch { state.trashActiveProfile() }
            }
            PillButton(stringResource(R.string.action_cancel), primary = false, enabled = true) { confirmTrash = false }
        }
    }
}

/** Tréninkové nahrávky po kapitolách pro neuronový klon hlasu ve fázi 2 (zadání kap. 6.2 bod 5). */
@Composable
private fun TrainingSection(state: CameraState) {
    val c = JakoTheme.colors
    val scope = rememberCoroutineScope()
    var seconds by remember { mutableFloatStateOf(0f) }
    var refresh by remember { mutableIntStateOf(0) }
    var recording by remember { mutableStateOf(false) }
    var elapsed by remember { mutableIntStateOf(0) }
    var exportMsg by remember { mutableStateOf<Int?>(null) }
    val recorder = remember { VoiceProfileRecorder(maxSeconds = 300) }
    LaunchedEffect(refresh) { seconds = state.trainingSeconds() }
    LaunchedEffect(recording) {
        while (recording) { delay(500); elapsed = recorder.seconds.toInt(); if (!recorder.isRecording) recording = false }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch { exportMsg = if (state.exportTraining(uri)) R.string.training_exported else R.string.training_export_failed }
    }
    Text(stringResource(R.string.training_title), style = MaterialTheme.typography.titleMedium, color = c.onGlass)
    Text(stringResource(R.string.training_progress, (seconds / 60f)), style = MaterialTheme.typography.bodyLarge, color = c.onGlassMuted)
    // Ukazatel k cíli 30 min.
    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.onGlass.copy(alpha = 0.12f))) {
        Box(Modifier.fillMaxWidth((seconds / 1800f).coerceIn(0f, 1f)).height(8.dp).background(c.accentStart))
    }
    if (recording) {
        Text("%d:%02d / 5:00".format(elapsed / 60, elapsed % 60), style = MaterialTheme.typography.titleLarge, color = c.onGlass)
        PillButton(stringResource(R.string.voice_stop), primary = true, enabled = true) {
            recording = false
            scope.launch {
                val pcm = withContext(Dispatchers.IO) { recorder.stop() }
                if (pcm.size > VoiceProfileRecorder.SAMPLE_RATE * 5) state.saveTraining(pcm)
                state.voice.start()
                refresh++
            }
        }
    } else {
        Text(stringResource(R.string.training_hint), style = MaterialTheme.typography.bodyMedium, color = c.onGlassMuted)
        PillButton(stringResource(R.string.training_record), primary = false, enabled = true) {
            state.voice.stopAndWait()
            recording = recorder.start()
            if (!recording) state.voice.start()
        }
        PillButton(stringResource(R.string.training_export), primary = false, enabled = seconds > 0f) {
            exporter.launch("jakotata-trenink.zip")
        }
    }
    exportMsg?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = c.onGlass) }
}

@Composable
private fun OptionRow(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = JakoTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = c.onGlassMuted)
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEachIndexed { i, label ->
                val sel = i == selected
                Box(
                    Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(50))
                        .background(if (sel) c.onGlass else c.onGlass.copy(alpha = 0.08f))
                        .selectable(sel, role = Role.RadioButton) { onSelect(i) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(label, style = MaterialTheme.typography.labelLarge, color = if (sel) c.root else c.onGlass) }
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = JakoTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = c.onGlass, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = c.accentStart, uncheckedTrackColor = Color.Transparent))
    }
}

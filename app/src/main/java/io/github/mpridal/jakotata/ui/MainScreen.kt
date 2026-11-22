package io.github.mpridal.jakotata.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import io.github.mpridal.jakotata.BuildConfig
import io.github.mpridal.jakotata.R
import io.github.mpridal.jakotata.core.PerfStats
import io.github.mpridal.jakotata.core.profile.FaceProfileBuilder
import io.github.mpridal.jakotata.ui.camera.CameraPreview
import io.github.mpridal.jakotata.ui.camera.CameraState
import io.github.mpridal.jakotata.ui.camera.rememberCameraState
import io.github.mpridal.jakotata.ui.components.GlassBox
import io.github.mpridal.jakotata.ui.components.LocalHazeState
import io.github.mpridal.jakotata.ui.theme.JakoDimens
import io.github.mpridal.jakotata.ui.theme.JakoTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalConfiguration
import io.github.mpridal.jakotata.core.record.MediaItem
import io.github.mpridal.jakotata.ui.components.JakoIcons
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.produceState
import io.github.mpridal.jakotata.core.render.ImageMode
import io.github.mpridal.jakotata.core.settings.AppSettings
import io.github.mpridal.jakotata.core.AdaptiveQuality
import io.github.mpridal.jakotata.core.update.AppVersion
import io.github.mpridal.jakotata.core.update.UpdateCheck
import io.github.mpridal.jakotata.core.update.UpdateService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.mpridal.jakotata.core.voice.ParrotState
import io.github.mpridal.jakotata.core.voice.SoundMode
import kotlinx.coroutines.launch

/**
 * Hlavní obrazovka (milník 3): živý obraz s maskou táty, profil z čelní fotky, „Kolik táty“.
 * Kompletní skleněné ovládání podle návrhu UI přibude v milníku 6.
 */
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context) }
    val loaded by appSettings.flow.collectAsStateWithLifecycle(initialValue = null)
    val settings = loaded ?: run {
        Box(Modifier.fillMaxSize().background(JakoTheme.colors.root))
        return
    }
    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    var hasCamera by remember { mutableStateOf(granted(Manifest.permission.CAMERA)) }
    var hasMic by remember { mutableStateOf(granted(Manifest.permission.RECORD_AUDIO)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasCamera = granted(Manifest.permission.CAMERA)
        hasMic = granted(Manifest.permission.RECORD_AUDIO)
    }
    val permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    LaunchedEffect(Unit) { if (!hasCamera || !hasMic) launcher.launch(permissions) }

    val state = rememberCameraState(settings.maxFaces, settings.activeProfile)
    LaunchedEffect(state) { state.loadProfile() }

    var front by rememberSaveable { mutableStateOf(settings.frontCamera) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(settings.watermark) { state.setWatermarkEnabled(settings.watermark) }
    LaunchedEffect(settings.videoQuality) { state.videoQuality = settings.videoQuality }
    LaunchedEffect(state) {
        state.voice.setMode(if (settings.soundMode == 0) SoundMode.SILENT else SoundMode.PARROT)
    }
    var showMesh by rememberSaveable { mutableStateOf(false) }
    var maskOn by rememberSaveable { mutableStateOf(true) }
    var voiceOn by rememberSaveable { mutableStateOf(true) }
    var amount by rememberSaveable { mutableFloatStateOf(0.8f) }
    var dadEyes by rememberSaveable { mutableStateOf(false) }
    var imageMode by rememberSaveable { mutableStateOf(ImageMode.MASK) }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var voiceSheetOpen by rememberSaveable { mutableStateOf(false) }
    var galleryOpen by rememberSaveable { mutableStateOf(false) }
    var updateRequested by remember { mutableStateOf(false) }
    val updater = remember { UpdateService(context, AppVersion.parse(BuildConfig.VERSION_NAME) ?: AppVersion(0, 0, 0)) }
    var updateResult by remember { mutableStateOf<UpdateCheck?>(null) }
    // Kontrola na pozadí (max. 1× za 6 h, chyby tiše); ručně z nastavení vždy s výsledkem.
    LaunchedEffect(Unit) {
        val r = updater.check(force = false, autoEnabled = settings.autoUpdate)
        if (r is UpdateCheck.Available) updateResult = r
    }
    LaunchedEffect(updateRequested) {
        if (updateRequested) {
            updateResult = updater.check(force = true, autoEnabled = true)
            updateRequested = false
        }
    }
    var countdown by remember { mutableIntStateOf(0) }
    var recording by remember { mutableStateOf(false) }
    var elapsed by remember { mutableIntStateOf(0) }
    var lastItem by remember { mutableStateOf<MediaItem?>(null) }
    val scope = rememberCoroutineScope()
    val portrait = LocalConfiguration.current.orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE

    fun stopRecording() {
        if (!recording) return
        recording = false
        scope.launch {
            val uri = state.stopRecording()
            if (uri != null) lastItem = MediaItem(uri, isVideo = true, dateAddedSec = System.currentTimeMillis() / 1000, durationMs = elapsed * 1000L)
        }
    }

    // Odpočet 3-2-1, pak start; časovač a automatické zastavení po max. délce (zadání kap. 6.4).
    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000)
            if (countdown == 1) {
                // Start mimo tento efekt: změna `countdown` efekt zruší, start ale musí doběhnout
                // a výsledek se musí zapsat (jinak by nahrávání běželo a UI o něm nevědělo).
                elapsed = 0
                scope.launch { recording = state.startRecording(portrait) }
                countdown = 0
            } else countdown -= 1
        }
    }
    LaunchedEffect(recording) {
        while (recording) {
            delay(1000)
            elapsed += 1
            if (elapsed >= settings.maxRecordingSec) stopRecording()
        }
    }
    val hazeState = remember { HazeState() }
    // Adaptivní kvalita: vzorek fps každých 0,5 s (zadání kap. 5).
    val quality = remember { AdaptiveQuality() }
    var qualityLevel by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            PerfStats.sample()
            if (quality.update(PerfStats.snapshot.value.renderFps, 0.5f)) {
                qualityLevel = quality.level
                state.renderer.shadingEnabled = quality.shadingTransfer
            }
        }
    }
    val c = JakoTheme.colors

    // Převod hlasu běží, když je obrazovka vidět a je povolený mikrofon.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, hasMic) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (hasMic) state.voice.start()
                Lifecycle.Event.ON_PAUSE -> state.voice.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (hasMic && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) state.voice.start()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); state.voice.stop() }
    }
    LaunchedEffect(voiceOn) { state.voice.setVoiceEnabled(voiceOn) }
    LaunchedEffect(amount) { state.voice.setAmount(amount) }

    Box(Modifier.fillMaxSize().background(c.root)) {
        Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
            if (hasCamera) {
                CameraPreview(state, front, showMesh, maskOn, amount, dadEyes, imageMode, Modifier.fillMaxSize())
            }
        }
        CompositionLocalProvider(
            LocalHazeState provides hazeState,
            io.github.mpridal.jakotata.ui.theme.LocalReduceTransparency provides (qualityLevel >= 2),
        ) {
            val galleryThumb by produceState<android.graphics.Bitmap?>(null, lastItem, galleryOpen) {
                value = withContext(Dispatchers.IO) { state.library.list().firstOrNull()?.let { state.library.thumbnail(it, 160) } }
            }
            val controls = CameraControls(
                mode = imageMode, onMode = { imageMode = it },
                faceOn = maskOn, onFace = { maskOn = it },
                voiceOn = voiceOn, onVoice = { voiceOn = it },
                amount = amount, onAmount = { amount = it },
                recording = recording || countdown > 0,
                onRecord = {
                    when {
                        recording -> stopRecording()
                        countdown > 0 -> countdown = 0
                        else -> { lastItem = null; galleryOpen = false; countdown = 3 }
                    }
                },
                onPhoto = {
                    scope.launch {
                        val uri = state.takePhoto(portrait)
                        if (uri != null) lastItem = MediaItem(uri, isVideo = false, dateAddedSec = System.currentTimeMillis() / 1000, durationMs = 0)
                    }
                },
                onGallery = { lastItem = null; galleryOpen = !galleryOpen },
                galleryThumb = galleryThumb,
                onSwitchCamera = { front = !front },
            )
            val status: @Composable () -> Unit = {
                if (imageMode == ImageMode.PUPPET && state.profile != null) PuppetHint(state)
                if (recording) RecordingPill(elapsed, settings.maxRecordingSec)
                else if (hasMic && voiceOn) ParrotPill(state)
            }
            val center: @Composable () -> Unit = {
                when {
                    !hasCamera || !hasMic -> Scrollable { PermissionCard { launcher.launch(permissions) } }
                    countdown > 0 -> Scrollable { CountdownOverlay(countdown) }
                    updateResult != null && !recording -> Scrollable { UpdateCard(updateResult!!, updater) { updateResult = null } }
                    settingsOpen -> SettingsScreen(state, settings, onCheckUpdate = { settingsOpen = false; updateRequested = true }) { settingsOpen = false }
                    galleryOpen -> GalleryScreen(state.library, settings.sharingAllowed) { galleryOpen = false }
                    lastItem != null && !recording -> Scrollable { ResultCard(lastItem!!, state.library, settings.sharingAllowed) { lastItem = null } }
                    voiceSheetOpen -> Scrollable { VoiceSheet(state) { voiceSheetOpen = false } }
                    sheetOpen || state.profile == null -> Scrollable {
                        ProfileSheet(
                            state, canClose = state.profile != null,
                            onRecordVoice = { sheetOpen = false; voiceSheetOpen = true },
                        ) { sheetOpen = false }
                    }
                    else -> Unit
                }
            }
            val topBar: @Composable () -> Unit = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    ProfileChip(state) { sheetOpen = true }
                    Spacer(Modifier.weight(1f))
                    if (hasMic) SoundModeChip(state)
                    GlassBox(shape = androidx.compose.foundation.shape.CircleShape) {
                        RoundIcon(JakoIcons.Sliders, stringResource(R.string.action_settings), enabled = !recording) {
                            galleryOpen = false; lastItem = null; settingsOpen = !settingsOpen
                        }
                    }
                }
            }
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(JakoDimens.gutter)) {
                if (maxWidth > maxHeight) {
                    // Na šířku (tablet): panel vlevo, tlačítka vpravo (návrh UI: Viktoriin tablet).
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        topBar()
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            SidePanel(controls, Modifier.align(Alignment.Bottom))
                            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                status()
                                if (BuildConfig.DEBUG) DebugOverlay(state, showMesh) { showMesh = !showMesh }
                                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { center() }
                            }
                            ActionRail(controls, Modifier.align(Alignment.CenterVertically))
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        topBar()
                        status()
                        if (BuildConfig.DEBUG) DebugOverlay(state, showMesh) { showMesh = !showMesh }
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { center() }
                        PhonePanel(controls)
                    }
                }
            }
        }
    }
}

/** Panel, který se na malém displeji může posunout (nastavení a galerie rolují samy). */
@Composable
private fun Scrollable(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) { content() }
}

/** Ikona režimu zvuku (zadání kap. 6.3): klepnutím Papoušek → (Sluchátka) → Ticho. */
@Composable
private fun SoundModeChip(state: CameraState) {
    val mode by state.voice.mode.collectAsStateWithLifecycle()
    val (label, icon) = when (mode) {
        SoundMode.PARROT -> R.string.sound_parrot to JakoIcons.Parrot
        SoundMode.HEADPHONES -> R.string.sound_headphones to JakoIcons.Headphones
        SoundMode.SILENT -> R.string.sound_silent to JakoIcons.Silent
    }
    GlassBox(
        Modifier.heightIn(min = JakoDimens.pill).clickable(role = Role.Button) { state.voice.cycleMode() },
        shape = RoundedCornerShape(50),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(icon, null, tint = JakoTheme.colors.onGlass)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = JakoTheme.colors.onGlass)
        }
    }
}

/** „Udělej vážný obličej“ během kalibrace Loutky (zadání kap. 2.4). */
@Composable
private fun PuppetHint(state: CameraState) {
    var calibrating by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) { calibrating = state.renderer.puppetCalibrating; delay(100) }
    }
    if (!calibrating) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        GlassBox(shape = RoundedCornerShape(50)) {
            Text(
                stringResource(R.string.puppet_neutral), modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                style = MaterialTheme.typography.labelLarge, color = JakoTheme.colors.onGlass,
            )
        }
    }
}

/** Stav Papouška: poslouchám / nahrávám / táta říká (zadání kap. 6.3, návrh UI). */
@Composable
private fun ParrotPill(state: CameraState) {
    val mode by state.voice.mode.collectAsStateWithLifecycle()
    if (mode != SoundMode.PARROT) return
    var parrot by remember { mutableStateOf(ParrotState.LISTENING) }
    var level by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) { parrot = state.voice.parrotState(); level = state.voice.inputLevel(); delay(80) }
    }
    val text = when (parrot) {
        ParrotState.LISTENING -> R.string.parrot_listening
        ParrotState.RECORDING -> R.string.parrot_recording
        ParrotState.PROCESSING, ParrotState.PLAYING -> R.string.parrot_playing
    }
    val c = JakoTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        GlassBox(shape = RoundedCornerShape(50)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                val bars = ((20f * kotlin.math.log10(level.coerceAtLeast(1e-5f)) + 60f) / 12f).toInt().coerceIn(0, 5)
                repeat(5) { i ->
                    Box(
                        Modifier.padding(end = 3.dp).width(4.dp).height((8 + i * 3).dp).clip(RoundedCornerShape(2.dp))
                            .background(if (i < bars || parrot == ParrotState.PLAYING) c.accentStart else c.onGlass.copy(alpha = 0.25f)),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(stringResource(text), style = MaterialTheme.typography.labelLarge, color = c.onGlass)
            }
        }
    }
}

@Composable
private fun ProfileChip(state: CameraState, onClick: () -> Unit) {
    val c = JakoTheme.colors
    val p = state.profile
    GlassBox(
        Modifier.heightIn(min = JakoDimens.pill).clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(50),
    ) {
        Row(Modifier.padding(start = 6.dp, end = 18.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (p != null) {
                Image(
                    p.texture.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(40.dp).clip(CircleShape),
                )
            } else {
                Box(Modifier.size(40.dp).background(c.accent, CircleShape))
            }
            Spacer(Modifier.width(10.dp))
            Text(state.profile?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.profile_default_name), style = MaterialTheme.typography.labelLarge, color = c.onGlass)
            Spacer(Modifier.width(6.dp))
            androidx.compose.material3.Icon(JakoIcons.Chevron, null, tint = c.onGlass, modifier = Modifier.size(18.dp))
        }
    }
}

/** Vyfocení táty (zadání kap. 6.2 – zjednodušeně: souhlas + čelní fotka z kamery nebo galerie). */
@Composable
private fun ProfileSheet(state: CameraState, canClose: Boolean, onRecordVoice: () -> Unit, onClose: () -> Unit) {
    val c = JakoTheme.colors
    val scope = rememberCoroutineScope()
    var consent by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Int?>(null) }
    val name = state.pendingName.ifBlank { stringResource(R.string.profile_default_name) }

    fun handle(result: FaceProfileBuilder.Result?) {
        message = when (result) {
            is FaceProfileBuilder.Result.Ok -> { scope.launch { state.saveProfile(result.profile); onClose() }; R.string.profile_saved }
            FaceProfileBuilder.Result.NotFrontal -> R.string.profile_err_not_frontal
            FaceProfileBuilder.Result.EyesClosed -> R.string.profile_err_eyes
            FaceProfileBuilder.Result.MouthOpen -> R.string.profile_err_mouth
            else -> R.string.profile_err_no_face
        }
        busy = false
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { busy = true; scope.launch { handle(state.profileFromUri(uri, name)) } }
    }

    GlassBox(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.profile_sheet_title), style = MaterialTheme.typography.headlineMedium, color = c.onGlass)
            Text(stringResource(R.string.profile_capture_hint), style = MaterialTheme.typography.bodyLarge, color = c.onGlassMuted)
            Row(
                Modifier.fillMaxWidth().heightIn(min = JakoDimens.touchTarget)
                    .toggleable(value = consent, role = Role.Checkbox, onValueChange = { consent = it }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = consent, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = c.accentStart))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.profile_consent), style = MaterialTheme.typography.bodyLarge, color = c.onGlass)
            }
            val enabled = consent && !busy
            PillButton(stringResource(R.string.profile_capture_camera), primary = true, enabled = enabled) {
                busy = true; message = null
                scope.launch { handle(state.profileFromCamera(name)) }
            }
            PillButton(stringResource(R.string.profile_pick_gallery), primary = false, enabled = enabled) {
                message = null
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            val status = when {
                busy -> R.string.profile_processing
                !consent -> R.string.profile_consent_required
                else -> message
            }
            if (status != null) Text(stringResource(status), style = MaterialTheme.typography.bodyMedium, color = c.onGlass)
            if (canClose) {
                val voiceLabel = state.voiceProfile?.let { stringResource(R.string.voice_result, it.medianF0.toInt()) }
                PillButton(stringResource(R.string.profile_record_voice) + (voiceLabel?.let { " · $it" } ?: ""), primary = false, enabled = true, onClick = onRecordVoice)
                PillButton(stringResource(R.string.action_close), primary = false, enabled = true, onClick = onClose)
            }
        }
    }
}

@Composable
internal fun PillButton(text: String, primary: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = JakoTheme.colors
    val bg = if (primary) c.accentStart else c.onGlass.copy(alpha = 0.12f)
    Box(
        Modifier.fillMaxWidth().heightIn(min = JakoDimens.touchTarget)
            .clip(RoundedCornerShape(50))
            .background(if (enabled) bg else bg.copy(alpha = bg.alpha * 0.4f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (primary) Color.White else c.onGlass, textAlign = TextAlign.Center)
    }
}

@Composable
private fun DebugOverlay(state: CameraState, showMesh: Boolean, onToggleMesh: () -> Unit) {
    val scope = rememberCoroutineScope()
    var voiceInfo by remember { mutableStateOf("") }
    var roundTrip by remember { mutableStateOf("–") }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            voiceInfo = "kid %.0f Hz · live %.0f ms".format(state.voice.kidF0(), state.voice.liveLatencyMs())
        }
    }
    val s by PerfStats.snapshot.collectAsStateWithLifecycle()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassBox(Modifier.clickable(onClick = onToggleMesh), shape = RoundedCornerShape(18.dp)) {
            Text(
                "%.0f fps · face %.0f/s %.1f ms %s\nframe age %.0f ms · faces %d · mesh %s\n%s · rt %s".format(
                    s.renderFps, s.detectFps, s.detectMs, s.delegate, s.frameAgeMs, s.faces, if (showMesh) "on" else "off",
                    voiceInfo, roundTrip,
                ),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                color = JakoTheme.colors.onGlass,
            )
        }
        // Test round-trip latence (zadání kap. 3.3): cvaknutí z reproduktoru → mikrofon.
        GlassBox(
            Modifier.clickable {
                roundTrip = "…"
                scope.launch { roundTrip = withContext(Dispatchers.Default) { state.voice.measureRoundTripMs() }.let { if (it < 0) "?" else "$it ms" } }
            },
            shape = RoundedCornerShape(18.dp),
        ) {
            Text(stringResource(R.string.latency_test), modifier = Modifier.padding(12.dp), fontSize = 11.sp, color = JakoTheme.colors.onGlass)
        }
    }
}

@Composable
private fun PermissionCard(onRequest: () -> Unit) {
    val c = JakoTheme.colors
    GlassBox(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.camera_permission_title), style = MaterialTheme.typography.headlineMedium, color = c.onGlass, textAlign = TextAlign.Center)
            Text(stringResource(R.string.camera_permission_body), style = MaterialTheme.typography.bodyLarge, color = c.onGlassMuted, textAlign = TextAlign.Center)
            PillButton(stringResource(R.string.camera_permission_allow), primary = true, enabled = true, onClick = onRequest)
        }
    }
}

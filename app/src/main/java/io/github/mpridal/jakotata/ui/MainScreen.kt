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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Hlavní obrazovka (milník 3): živý obraz s maskou táty, profil z čelní fotky, „Kolik táty“.
 * Kompletní skleněné ovládání podle návrhu UI přibude v milníku 6.
 */
@Composable
fun MainScreen() {
    val context = LocalContext.current
    var hasCamera by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamera = it }
    LaunchedEffect(Unit) { if (!hasCamera) launcher.launch(Manifest.permission.CAMERA) }

    val state = rememberCameraState()
    LaunchedEffect(state) { state.loadProfile() }

    var front by rememberSaveable { mutableStateOf(true) }
    var showMesh by rememberSaveable { mutableStateOf(false) }
    var maskOn by rememberSaveable { mutableStateOf(true) }
    var amount by rememberSaveable { mutableFloatStateOf(0.8f) }
    var dadEyes by rememberSaveable { mutableStateOf(false) }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    val hazeState = remember { HazeState() }
    val c = JakoTheme.colors

    Box(Modifier.fillMaxSize().background(c.root)) {
        Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
            if (hasCamera) {
                CameraPreview(state, front, showMesh, maskOn, amount, dadEyes, Modifier.fillMaxSize())
            }
        }
        CompositionLocalProvider(LocalHazeState provides hazeState) {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().padding(JakoDimens.gutter),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    ProfileChip(state) { sheetOpen = true }
                    if (BuildConfig.DEBUG) DebugOverlay(showMesh) { showMesh = !showMesh }
                }
                when {
                    !hasCamera -> PermissionCard { launcher.launch(Manifest.permission.CAMERA) }
                    sheetOpen || state.profile == null -> ProfileSheet(state, canClose = state.profile != null) { sheetOpen = false }
                    else -> Spacer(Modifier)
                }
                ControlPanel(
                    maskOn = maskOn, onMaskOn = { maskOn = it },
                    amount = amount, onAmount = { amount = it },
                    dadEyes = dadEyes, onDadEyes = { dadEyes = it },
                    onSwitchCamera = { front = !front },
                )
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
            Text(stringResource(R.string.profile_default_name), style = MaterialTheme.typography.labelLarge, color = c.onGlass)
        }
    }
}

/** Vyfocení táty (zadání kap. 6.2 – zjednodušeně: souhlas + čelní fotka z kamery nebo galerie). */
@Composable
private fun ProfileSheet(state: CameraState, canClose: Boolean, onClose: () -> Unit) {
    val c = JakoTheme.colors
    val scope = rememberCoroutineScope()
    var consent by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Int?>(null) }
    val name = stringResource(R.string.profile_default_name)

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
            if (canClose) PillButton(stringResource(R.string.action_close), primary = false, enabled = true, onClick = onClose)
        }
    }
}

@Composable
private fun PillButton(text: String, primary: Boolean, enabled: Boolean, onClick: () -> Unit) {
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
private fun ControlPanel(
    maskOn: Boolean, onMaskOn: (Boolean) -> Unit,
    amount: Float, onAmount: (Float) -> Unit,
    dadEyes: Boolean, onDadEyes: (Boolean) -> Unit,
    onSwitchCamera: () -> Unit,
) {
    val c = JakoTheme.colors
    val recordLabel = stringResource(R.string.action_record)
    val switchLabel = stringResource(R.string.action_switch_camera)
    GlassBox(Modifier.fillMaxWidth(), shape = RoundedCornerShape(JakoDimens.panelRadius)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleChip(stringResource(R.string.toggle_face), maskOn, onMaskOn)
                ToggleChip(stringResource(R.string.dad_eyes), dadEyes, onDadEyes)
                Spacer(Modifier.weight(1f))
                Text("${(amount * 100).toInt()} %", style = MaterialTheme.typography.titleMedium, color = c.onGlass)
            }
            val sliderLabel = stringResource(R.string.how_much_dad)
            Slider(
                value = amount, onValueChange = onAmount, steps = 19,
                modifier = Modifier.semantics { contentDescription = sliderLabel },
                colors = SliderDefaults.colors(thumbColor = c.accentStart, activeTrackColor = c.accentStart),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(JakoDimens.pill))
                Box(Modifier.size(JakoDimens.recordButton).clip(CircleShape).background(c.onGlass.copy(alpha = 0.15f))) {
                    Box(
                        Modifier.align(Alignment.Center).size(JakoDimens.recordButton - 20.dp)
                            .background(c.accent, CircleShape).semantics { contentDescription = recordLabel },
                    )
                }
                Box(
                    Modifier.size(JakoDimens.pill).clip(CircleShape).background(c.onGlass.copy(alpha = 0.12f))
                        .clickable(role = Role.Button, onClick = onSwitchCamera)
                        .semantics { contentDescription = switchLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("⟲", fontSize = 24.sp, color = c.onGlass)
                }
            }
        }
    }
}

@Composable
private fun ToggleChip(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = JakoTheme.colors
    Box(
        Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(50))
            .background(if (checked) c.onGlass else Color.Transparent)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (checked) c.root else c.onGlassMuted)
    }
}

@Composable
private fun DebugOverlay(showMesh: Boolean, onToggleMesh: () -> Unit) {
    LaunchedEffect(Unit) {
        while (true) { delay(500); PerfStats.sample() }
    }
    val s by PerfStats.snapshot.collectAsStateWithLifecycle()
    GlassBox(Modifier.clickable(onClick = onToggleMesh), shape = RoundedCornerShape(18.dp)) {
        Text(
            "%.0f fps · face %.0f/s %.1f ms %s\nframe age %.0f ms · faces %d · mesh %s".format(
                s.renderFps, s.detectFps, s.detectMs, s.delegate, s.frameAgeMs, s.faces, if (showMesh) "on" else "off",
            ),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            color = JakoTheme.colors.onGlass,
        )
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

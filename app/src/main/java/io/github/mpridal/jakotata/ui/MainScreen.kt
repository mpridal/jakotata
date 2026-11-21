package io.github.mpridal.jakotata.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import io.github.mpridal.jakotata.ui.camera.CameraPreview
import io.github.mpridal.jakotata.ui.components.GlassBox
import io.github.mpridal.jakotata.ui.components.LocalHazeState
import io.github.mpridal.jakotata.ui.theme.JakoDimens
import io.github.mpridal.jakotata.ui.theme.JakoTheme
import kotlinx.coroutines.delay

/**
 * Hlavní obrazovka (milník 2): živý obraz kamery s debug sítí bodů a měřením fps.
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

    var front by rememberSaveable { mutableStateOf(true) }
    var showMesh by rememberSaveable { mutableStateOf(BuildConfig.DEBUG) }
    val hazeState = remember { HazeState() }
    val c = JakoTheme.colors

    Box(Modifier.fillMaxSize().background(c.root)) {
        Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
            if (hasCamera) CameraPreview(front = front, showMesh = showMesh, modifier = Modifier.fillMaxSize())
        }
        CompositionLocalProvider(LocalHazeState provides hazeState) {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().padding(JakoDimens.gutter),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    GlassPill(stringResource(R.string.profile_default_name))
                    if (BuildConfig.DEBUG) DebugOverlay(showMesh) { showMesh = !showMesh }
                }
                if (!hasCamera) {
                    PermissionCard { launcher.launch(Manifest.permission.CAMERA) }
                }
                BottomBar(onSwitchCamera = { front = !front })
            }
        }
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
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        GlassBox {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.camera_permission_title), style = MaterialTheme.typography.headlineMedium, color = c.onGlass, textAlign = TextAlign.Center)
                Text(stringResource(R.string.camera_permission_body), style = MaterialTheme.typography.bodyLarge, color = c.onGlassMuted, textAlign = TextAlign.Center)
                Box(
                    Modifier
                        .padding(top = 16.dp)
                        .heightIn(min = JakoDimens.touchTarget)
                        .background(c.accent, RoundedCornerShape(50))
                        .clickable(role = Role.Button, onClick = onRequest)
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                ) {
                    Text(stringResource(R.string.camera_permission_allow), style = MaterialTheme.typography.labelLarge, color = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
    }
}

@Composable
private fun BottomBar(onSwitchCamera: () -> Unit) {
    val c = JakoTheme.colors
    val recordLabel = stringResource(R.string.action_record)
    val switchLabel = stringResource(R.string.action_switch_camera)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(JakoDimens.pill))
        GlassBox(Modifier.size(JakoDimens.recordButton), shape = CircleShape) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(JakoDimens.recordButton - 20.dp)
                    .background(c.accent, CircleShape)
                    .semantics { contentDescription = recordLabel },
            )
        }
        GlassBox(
            Modifier.size(JakoDimens.pill).clickable(role = Role.Button, onClick = onSwitchCamera),
            shape = CircleShape,
        ) {
            Text(
                "⟲",
                modifier = Modifier.align(Alignment.Center).semantics { contentDescription = switchLabel },
                fontSize = 24.sp,
                color = c.onGlass,
            )
        }
    }
}

@Composable
private fun GlassPill(text: String) {
    GlassBox(shape = RoundedCornerShape(50)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            style = MaterialTheme.typography.labelLarge,
            color = JakoTheme.colors.onGlass,
        )
    }
}

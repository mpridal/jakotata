package io.github.mpridal.jakotata.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import io.github.mpridal.jakotata.R
import io.github.mpridal.jakotata.core.voice.NativeVoice
import io.github.mpridal.jakotata.ui.components.GlassBox
import io.github.mpridal.jakotata.ui.components.LocalHazeState
import io.github.mpridal.jakotata.ui.theme.JakoDimens
import io.github.mpridal.jakotata.ui.theme.JakoTheme

/**
 * Kostra hlavní obrazovky (milník 1): rozložení skleněných panelů nad pozadím, kde bude
 * živý obraz kamery (milník 2). Ovládání zatím nic nedělá; finální podoba podle návrhu UI.
 */
@Composable
fun MainScreen() {
    val c = JakoTheme.colors
    val hazeState = remember { HazeState() }
    Box(Modifier.fillMaxSize()) {
        // Zástupné „pozadí kamery“ – barevný přechod, aby bylo vidět rozmazání skla.
        Box(
            Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .background(Brush.verticalGradient(listOf(c.accentEnd, c.root, c.accentStart))),
        )
        CompositionLocalProvider(LocalHazeState provides hazeState) {
            Column(
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(JakoDimens.gutter),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                TopBar()
                CenterHint()
                BottomBar()
            }
        }
    }
}

@Composable
private fun TopBar() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        GlassPill(stringResource(R.string.profile_default_name))
        GlassPill(stringResource(R.string.sound_parrot))
    }
}

@Composable
private fun CenterHint() {
    val c = JakoTheme.colors
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        GlassBox {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.show_face), style = MaterialTheme.typography.headlineMedium, color = c.onGlass)
                Text(
                    stringResource(R.string.skeleton_note) + " · voice " + NativeVoice.versionOrNull().orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.onGlassMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun BottomBar() {
    val c = JakoTheme.colors
    val recordLabel = stringResource(R.string.action_record)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassPill(stringResource(R.string.mode_mask))
            GlassPill(stringResource(R.string.mode_puppet))
        }
        Box(Modifier.padding(top = 16.dp), contentAlignment = Alignment.Center) {
            GlassBox(Modifier.size(JakoDimens.recordButton), shape = CircleShape) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(JakoDimens.recordButton - 20.dp)
                        .background(c.accent, CircleShape)
                        .semantics { contentDescription = recordLabel },
                )
            }
        }
    }
}

@Composable
private fun GlassPill(text: String) {
    GlassBox(shape = RoundedCornerShape(50)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            style = MaterialTheme.typography.labelLarge,
            color = JakoTheme.colors.onGlass,
        )
    }
}

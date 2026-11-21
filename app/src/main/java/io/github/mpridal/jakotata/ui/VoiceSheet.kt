package io.github.mpridal.jakotata.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mpridal.jakotata.R
import io.github.mpridal.jakotata.core.voice.VoiceProfile
import io.github.mpridal.jakotata.core.voice.VoiceProfileRecorder
import io.github.mpridal.jakotata.ui.camera.CameraState
import io.github.mpridal.jakotata.ui.components.GlassBox
import io.github.mpridal.jakotata.ui.theme.JakoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.log10
import kotlin.math.roundToInt

/**
 * Nahrání hlasu táty (zadání kap. 3.4 a 6.2 krok 3): telepromptér 45–60 s, měřič úrovně,
 * kontrola kvality a karta výsledku. Během nahrávání je převod hlasu zastavený.
 */
@Composable
fun VoiceSheet(state: CameraState, onClose: () -> Unit) {
    val c = JakoTheme.colors
    val scope = rememberCoroutineScope()
    val recorder = remember { VoiceProfileRecorder() }
    var recording by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var seconds by remember { mutableFloatStateOf(0f) }
    var level by remember { mutableFloatStateOf(0f) }
    var result by remember { mutableStateOf<VoiceProfile?>(null) }
    var problems by remember { mutableStateOf<List<VoiceProfile.Problem>>(emptyList()) }

    LaunchedEffect(recording) {
        while (recording) {
            seconds = recorder.seconds
            level = recorder.level
            if (!recorder.isRecording) recording = false
            delay(50)
        }
    }

    fun finish() {
        recording = false
        busy = true
        scope.launch {
            val pcm = withContext(Dispatchers.IO) { recorder.stop() }
            val v = withContext(Dispatchers.Default) { VoiceProfileRecorder.analyze(pcm) }
            problems = v?.problems() ?: listOf(VoiceProfile.Problem.TOO_LITTLE_SPEECH)
            if (v != null && problems.isEmpty()) {
                state.saveVoice(v, pcm)
                result = v
            }
            state.voice.start()
            busy = false
        }
    }

    GlassBox(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.voice_record_title), style = MaterialTheme.typography.headlineMedium, color = c.onGlass)
            val done = result
            if (done != null) {
                Text(stringResource(R.string.voice_result, done.medianF0.roundToInt()), style = MaterialTheme.typography.headlineMedium, color = c.onGlass)
                Text(stringResource(R.string.voice_saved), style = MaterialTheme.typography.bodyLarge, color = c.onGlassMuted)
            } else {
                Text(stringResource(R.string.voice_record_hint), style = MaterialTheme.typography.bodyLarge, color = c.onGlassMuted)
                // Telepromptér.
                Box(
                    Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(18.dp))
                        .background(c.onGlass.copy(alpha = 0.08f)).padding(14.dp),
                ) {
                    Text(
                        stringResource(R.string.voice_prompt),
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.titleMedium,
                        color = c.onGlass,
                    )
                }
                // Měřič úrovně a čas.
                val db = 20f * log10(level.coerceAtLeast(1e-5f))
                val fill = ((db + 60f) / 60f).coerceIn(0f, 1f)
                Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(c.onGlass.copy(alpha = 0.12f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(fill).background(if (db > -3f) c.recording else c.accentStart))
                }
                Text("%d / %d s".format(seconds.toInt(), VoiceProfileRecorder.MAX_SECONDS), style = MaterialTheme.typography.labelLarge, color = c.onGlass)
                problems.forEach { p ->
                    val msg = when (p) {
                        VoiceProfile.Problem.TOO_LITTLE_SPEECH -> R.string.voice_err_little
                        VoiceProfile.Problem.CLIPPING -> R.string.voice_err_clip
                        VoiceProfile.Problem.NOISY -> R.string.voice_err_noisy
                    }
                    Text(stringResource(msg), style = MaterialTheme.typography.bodyMedium, color = c.onGlass)
                }
            }
            when {
                busy -> Text(stringResource(R.string.profile_processing), style = MaterialTheme.typography.bodyMedium, color = c.onGlass)
                recording -> PillButton(stringResource(R.string.voice_stop), primary = true, enabled = true) { finish() }
                done == null -> PillButton(stringResource(R.string.voice_start), primary = true, enabled = true) {
                    problems = emptyList()
                    state.voice.stopAndWait()
                    recording = recorder.start()
                    if (!recording) state.voice.start()
                }
                else -> PillButton(stringResource(R.string.voice_again), primary = false, enabled = true) { result = null; seconds = 0f }
            }
            if (!recording && !busy) PillButton(stringResource(R.string.action_close), primary = false, enabled = true, onClick = onClose)
        }
    }
}

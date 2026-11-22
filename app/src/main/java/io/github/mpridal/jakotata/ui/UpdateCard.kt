package io.github.mpridal.jakotata.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import io.github.mpridal.jakotata.R
import io.github.mpridal.jakotata.core.update.UpdateCheck
import io.github.mpridal.jakotata.core.update.UpdateService
import io.github.mpridal.jakotata.ui.components.GlassBox
import io.github.mpridal.jakotata.ui.theme.JakoTheme
import kotlinx.coroutines.launch

/** Nabídka aktualizace (zadání kap. 9); povinnou aktualizaci (minimální verze) nejde odložit. */
@Composable
fun UpdateCard(result: UpdateCheck, service: UpdateService, onClose: () -> Unit) {
    val c = JakoTheme.colors
    val scope = rememberCoroutineScope()
    var progress by remember { mutableFloatStateOf(-1f) }
    var failed by remember { mutableStateOf(false) }
    val lang = ConfigurationCompat.getLocales(LocalConfiguration.current)[0]?.language ?: "en"
    GlassBox(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (result) {
                is UpdateCheck.Available -> {
                    Text(stringResource(R.string.update_available, result.release.version.toString()), style = MaterialTheme.typography.headlineMedium, color = c.onGlass)
                    Text(result.release.notesFor(lang), style = MaterialTheme.typography.bodyLarge, color = c.onGlassMuted)
                    if (result.mandatory) Text(stringResource(R.string.update_mandatory), style = MaterialTheme.typography.bodyMedium, color = c.onGlass)
                    if (progress >= 0f) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = c.accentStart)
                    if (failed) Text(stringResource(R.string.update_failed), style = MaterialTheme.typography.bodyMedium, color = c.onGlass)
                    PillButton(stringResource(R.string.update_install), primary = true, enabled = progress < 0f) {
                        failed = false
                        progress = 0f
                        scope.launch {
                            val ok = service.downloadAndInstall(result.release) { p -> progress = p }
                            progress = -1f
                            failed = !ok
                        }
                    }
                    if (!result.mandatory) PillButton(stringResource(R.string.update_later), primary = false, enabled = true, onClick = onClose)
                }
                UpdateCheck.UpToDate -> {
                    Text(stringResource(R.string.update_up_to_date), style = MaterialTheme.typography.titleLarge, color = c.onGlass)
                    PillButton(stringResource(R.string.action_close), primary = false, enabled = true, onClick = onClose)
                }
                else -> {
                    Text(stringResource(R.string.update_check_failed), style = MaterialTheme.typography.titleLarge, color = c.onGlass)
                    PillButton(stringResource(R.string.action_close), primary = false, enabled = true, onClick = onClose)
                }
            }
        }
    }
}

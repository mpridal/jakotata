package io.github.mpridal.jakotata.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import io.github.mpridal.jakotata.R
import io.github.mpridal.jakotata.core.render.ImageMode
import io.github.mpridal.jakotata.ui.components.GlassBox
import io.github.mpridal.jakotata.ui.components.JakoIcons
import io.github.mpridal.jakotata.ui.theme.JakoDimens
import io.github.mpridal.jakotata.ui.theme.JakoTheme

/** Stav a akce ovládání kamery – jeden objekt pro rozložení telefonu i tabletu. */
class CameraControls(
    val mode: ImageMode, val onMode: (ImageMode) -> Unit,
    val faceOn: Boolean, val onFace: (Boolean) -> Unit,
    val voiceOn: Boolean, val onVoice: (Boolean) -> Unit,
    val amount: Float, val onAmount: (Float) -> Unit,
    val recording: Boolean, val onRecord: () -> Unit,
    val onPhoto: () -> Unit,
    val onGallery: () -> Unit,
    val galleryThumb: Bitmap?,
    val onSwitchCamera: () -> Unit,
)

private val modes = listOf(
    ImageMode.MASK to R.string.mode_mask,
    ImageMode.PUPPET to R.string.mode_puppet,
    ImageMode.HALF to R.string.mode_half,
    ImageMode.COMPARE to R.string.mode_compare,
)

/** Spodní skleněný panel telefonu (návrh UI: Kamera – Maska). */
@Composable
fun PhonePanel(c: CameraControls) {
    GlassBox(Modifier.fillMaxWidth(), shape = RoundedCornerShape(JakoDimens.panelRadius)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ModeSegments(c)
            ToggleRow(c)
            AmountSlider(c)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GalleryThumb(c)
                    RoundIcon(JakoIcons.Camera, stringResource(R.string.action_photo), onClick = c.onPhoto)
                }
                RecordButton(c)
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                    RoundIcon(JakoIcons.SwitchCamera, stringResource(R.string.action_switch_camera), enabled = !c.recording, onClick = c.onSwitchCamera)
                }
            }
        }
    }
}

/** Levý panel na šířku (návrh UI: Viktoriin tablet): režimy pod sebou, přepínače, posuvník. */
@Composable
fun SidePanel(c: CameraControls, modifier: Modifier = Modifier) {
    val t = JakoTheme.colors
    GlassBox(modifier.widthIn(max = 320.dp), shape = RoundedCornerShape(JakoDimens.panelRadius)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.image_mode), style = MaterialTheme.typography.labelMedium, color = t.onGlassMuted)
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                modes.forEach { (m, label) ->
                    val selected = c.mode == m
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(18.dp))
                            .background(if (selected) t.onGlass else Color.Transparent)
                            .selectable(selected, role = Role.RadioButton) { c.onMode(m) }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(10.dp).background(if (selected) t.accentStart else t.onGlassMuted.copy(alpha = 0.5f), CircleShape))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = if (selected) t.root else t.onGlass)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(t.glassBorder))
            ToggleRow(c)
            AmountSlider(c)
        }
    }
}

/** Pravý sloupec na šířku: otočit kameru, nahrávat, vyfotit, galerie. */
@Composable
fun ActionRail(c: CameraControls, modifier: Modifier = Modifier) {
    GlassBox(modifier, shape = RoundedCornerShape(JakoDimens.panelRadius)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(vertical = 14.dp, horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RoundIcon(JakoIcons.SwitchCamera, stringResource(R.string.action_switch_camera), enabled = !c.recording, onClick = c.onSwitchCamera)
            RecordButton(c)
            RoundIcon(JakoIcons.Camera, stringResource(R.string.action_photo), onClick = c.onPhoto)
            GalleryThumb(c)
        }
    }
}

@Composable
private fun ModeSegments(c: CameraControls) {
    val t = JakoTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(t.onGlass.copy(alpha = 0.12f)).padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        modes.forEach { (m, label) ->
            val selected = c.mode == m
            Box(
                Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(18.dp))
                    .background(if (selected) t.onGlass else Color.Transparent)
                    .selectable(selected, role = Role.Tab) { c.onMode(m) },
                contentAlignment = Alignment.Center,
            ) {
                // Dva řádky a menší písmo: delší názvy (EN/ES) se vejdou i na telefon 360 dp (zadání kap. 12).
                Text(
                    stringResource(label), style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 14.sp),
                    maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    color = if (selected) t.root else t.onGlass,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(c: CameraControls) {
    val t = JakoTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconToggle(JakoIcons.Face, stringResource(R.string.toggle_face), c.faceOn, c.onFace)
        IconToggle(JakoIcons.Mic, stringResource(R.string.toggle_voice), c.voiceOn, c.onVoice)
        Spacer(Modifier.weight(1f))
        Text(stringResource(R.string.how_much_dad), style = MaterialTheme.typography.labelMedium, color = t.onGlassMuted)
        Text("${(c.amount * 100).toInt()} %", style = MaterialTheme.typography.titleLarge, color = t.onGlass, modifier = Modifier.widthIn(min = 56.dp))
    }
}

@Composable
private fun AmountSlider(c: CameraControls) {
    val t = JakoTheme.colors
    val label = stringResource(R.string.how_much_dad)
    Slider(
        value = c.amount, onValueChange = c.onAmount, steps = 19,
        modifier = Modifier.semantics { contentDescription = label },
        colors = SliderDefaults.colors(thumbColor = t.accentStart, activeTrackColor = t.accentStart, inactiveTrackColor = t.onGlass.copy(alpha = 0.2f)),
    )
}

@Composable
private fun IconToggle(icon: ImageVector, text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val t = JakoTheme.colors
    Row(
        Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(50))
            .background(if (checked) t.onGlass else Color.Transparent)
            .border(1.dp, t.glassBorder, RoundedCornerShape(50))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (checked) t.root else t.onGlassMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (checked) t.root else t.onGlassMuted)
    }
}

/** Tlačítko nahrávání: kruh s akcentem → zaoblený čtverec při nahrávání (zadání kap. 7). */
@Composable
private fun RecordButton(c: CameraControls) {
    val t = JakoTheme.colors
    val label = stringResource(if (c.recording) R.string.action_stop else R.string.action_record)
    val inner by animateDpAsState(if (c.recording) 34.dp else 64.dp, label = "rec")
    val corner by animateIntAsState(if (c.recording) 28 else 50, label = "corner")
    Box(
        Modifier.size(JakoDimens.recordButton).clip(CircleShape).border(4.dp, t.onGlass, CircleShape)
            .clickable(role = Role.Button, onClick = c.onRecord).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(inner).background(if (c.recording) t.recording else t.accentStart, RoundedCornerShape(corner)))
    }
}

@Composable
private fun GalleryThumb(c: CameraControls) {
    val t = JakoTheme.colors
    val label = stringResource(R.string.action_gallery)
    Box(
        Modifier.size(JakoDimens.pill).clip(RoundedCornerShape(16.dp)).border(2.dp, t.onGlass, RoundedCornerShape(16.dp))
            .background(t.onGlass.copy(alpha = 0.12f))
            .clickable(enabled = !c.recording, role = Role.Button, onClick = c.onGallery)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        val thumb = c.galleryThumb
        if (thumb != null) Image(thumb.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Icon(JakoIcons.Gallery, null, tint = t.onGlass)
    }
}

@Composable
fun RoundIcon(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val t = JakoTheme.colors
    Box(
        Modifier.size(JakoDimens.pill).clip(CircleShape).background(t.onGlass.copy(alpha = if (enabled) 0.12f else 0.05f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = t.onGlass.copy(alpha = if (enabled) 1f else 0.4f))
    }
}

package io.github.mpridal.jakotata.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mpridal.jakotata.R
import io.github.mpridal.jakotata.core.record.MediaItem
import io.github.mpridal.jakotata.core.record.MediaLibrary
import io.github.mpridal.jakotata.ui.components.GlassBox
import io.github.mpridal.jakotata.ui.components.JakoIcons
import io.github.mpridal.jakotata.ui.theme.JakoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Odpočet 3-2-1 před nahráváním (zadání kap. 6.4). */
@Composable
fun CountdownOverlay(value: Int) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GlassBox(Modifier.size(160.dp), shape = CircleShape) {
            Text(
                value.toString(), modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.displayLarge, fontSize = 96.sp, color = JakoTheme.colors.onGlass,
            )
        }
    }
}

/** Pulzující tečka a čas nahrávání „0:42 / 2:00“. */
@Composable
fun RecordingPill(elapsedSec: Int, maxSec: Int) {
    val c = JakoTheme.colors
    val pulse by rememberInfiniteTransition(label = "rec").animateFloat(
        initialValue = 1f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "dot",
    )
    val label = stringResource(R.string.recording_timer, elapsedSec, maxSec)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        GlassBox(Modifier.semantics { contentDescription = label }, shape = RoundedCornerShape(50)) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).alpha(pulse).background(c.recording, CircleShape))
                Spacer(Modifier.width(10.dp))
                Text("%d:%02d".format(elapsedSec / 60, elapsedSec % 60), style = MaterialTheme.typography.titleLarge, color = c.onGlass)
                Text("  / %d:%02d".format(maxSec / 60, maxSec % 60), style = MaterialTheme.typography.labelLarge, color = c.onGlassMuted)
            }
        }
    }
}

fun openMedia(context: Context, uri: Uri, isVideo: Boolean) {
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, if (isVideo) "video/mp4" else "image/jpeg")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(intent) }
}

/** Sdílení jen na pokyn uživatele přes systémovou nabídku (zadání kap. 6.4). */
fun shareMedia(context: Context, uri: Uri, isVideo: Boolean) {
    val send = Intent(Intent.ACTION_SEND).setType(if (isVideo) "video/mp4" else "image/jpeg")
        .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(send, null)) }
}

/** Mazání jen do koše: systémový koš (s potvrzením) na API 30+, jinak koš aplikace. */
@Composable
fun rememberTrash(library: MediaLibrary, onDone: () -> Unit): (MediaItem) -> Unit {
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { onDone() }
    return { item ->
        val pi = library.trashRequest(listOf(item))
        if (pi != null) launcher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        else scope.launch { withContext(Dispatchers.IO) { library.moveToAppTrash(item) }; onDone() }
    }
}

/** Karta po nahrání: přehrát, sdílet, do koše (video je už uložené). */
@Composable
fun ResultCard(item: MediaItem, library: MediaLibrary, sharingAllowed: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val c = JakoTheme.colors
    val trash = rememberTrash(library) { onClose() }
    GlassBox(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(if (item.isVideo) R.string.saved_video else R.string.saved_photo),
                style = MaterialTheme.typography.headlineMedium, color = c.onGlass,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RoundAction(JakoIcons.Play, stringResource(R.string.action_play)) { openMedia(context, item.uri, item.isVideo) }
                if (sharingAllowed) RoundAction(JakoIcons.Share, stringResource(R.string.action_share)) { shareMedia(context, item.uri, item.isVideo) }
                RoundAction(JakoIcons.Trash, stringResource(R.string.action_trash)) { trash(item) }
                RoundAction(JakoIcons.Close, stringResource(R.string.action_close), onClose)
            }
        }
    }
}

@Composable
fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val c = JakoTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(c.onGlass.copy(alpha = 0.12f))
                .combinedClickable(onClick = onClick, role = androidx.compose.ui.semantics.Role.Button),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = label, tint = c.onGlass) }
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.onGlassMuted, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Galerie vlastních nahrávek a fotek (zadání kap. 6.5): klepnutí přehraje, podržení nabídne akce. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(library: MediaLibrary, sharingAllowed: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val c = JakoTheme.colors
    var refresh by remember { mutableIntStateOf(0) }
    val items by produceState(initialValue = emptyList<MediaItem>(), refresh) {
        value = withContext(Dispatchers.IO) { library.list() }
    }
    var selected by remember { mutableStateOf<MediaItem?>(null) }
    val trash = rememberTrash(library) { selected = null; refresh++ }

    GlassBox(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.action_gallery), style = MaterialTheme.typography.headlineMedium, color = c.onGlass, modifier = Modifier.weight(1f))
                RoundAction(JakoIcons.Close, stringResource(R.string.action_close), onClose)
            }
            val sel = selected
            if (sel != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RoundAction(JakoIcons.Play, stringResource(R.string.action_play)) { openMedia(context, sel.uri, sel.isVideo) }
                    if (sharingAllowed) RoundAction(JakoIcons.Share, stringResource(R.string.action_share)) { shareMedia(context, sel.uri, sel.isVideo) }
                    RoundAction(JakoIcons.Trash, stringResource(R.string.action_trash)) { trash(sel) }
                }
            }
            if (items.isEmpty()) {
                Text(stringResource(R.string.gallery_empty), style = MaterialTheme.typography.bodyLarge, color = c.onGlassMuted)
            }
            LazyVerticalGrid(columns = GridCells.Adaptive(110.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.uri.toString() }) { item ->
                    val thumb by produceState<Bitmap?>(null, item) { value = withContext(Dispatchers.IO) { library.thumbnail(item) } }
                    Box(
                        Modifier.aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(c.onGlass.copy(alpha = 0.1f))
                            .combinedClickable(
                                onClick = { openMedia(context, item.uri, item.isVideo) },
                                onLongClick = { selected = if (selected == item) null else item },
                            ),
                    ) {
                        thumb?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                        if (item.isVideo) {
                            Icon(JakoIcons.Play, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.align(Alignment.Center).size(32.dp))
                        }
                        if (selected == item) Box(Modifier.fillMaxSize().background(c.accentStart.copy(alpha = 0.35f)))
                    }
                }
            }
        }
    }
}

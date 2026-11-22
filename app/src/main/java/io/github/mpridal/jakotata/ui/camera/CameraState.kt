package io.github.mpridal.jakotata.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.mpridal.jakotata.core.camera.CameraSource
import io.github.mpridal.jakotata.core.face.FaceTracker
import io.github.mpridal.jakotata.core.profile.FaceProfile
import io.github.mpridal.jakotata.core.profile.FaceProfileBuilder
import io.github.mpridal.jakotata.core.profile.ProfileStore
import io.github.mpridal.jakotata.core.render.CameraRenderer
import io.github.mpridal.jakotata.core.record.MediaLibrary
import io.github.mpridal.jakotata.core.record.MediaSaver
import io.github.mpridal.jakotata.core.record.RecordingSession
import io.github.mpridal.jakotata.core.render.MaskTopology
import io.github.mpridal.jakotata.core.voice.VoiceEngine
import io.github.mpridal.jakotata.core.voice.VoiceProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.max

/**
 * Drží `core` objekty kamery, obličeje, renderu a profilu pro obrazovku kamery. UI volá jen tyto
 * metody, ne MediaPipe ani GL přímo (zadání kap. 4.2).
 */
class CameraState(private val context: Context) {
    val tracker = FaceTracker(context)
    val renderer = CameraRenderer(
        faceSource = { tracker.latest.get() },
        meshEdges = tracker.meshEdges,
        maskTopology = MaskTopology(tracker.meshTriangles, tracker.ovalLoop, tracker.leftEyeLoop, tracker.rightEyeLoop),
        kidSkinSource = { tracker.kidSkin.get() },
    )
    val camera = CameraSource(context)
    val voice = VoiceEngine(context)
    private val store = ProfileStore(context)
    val library = MediaLibrary(context)
    private val saver = MediaSaver(context)
    private var session: RecordingSession? = null

    init {
        renderer.setWatermark(watermarkBitmap(context))
    }

    /** Rozměry videa/fotky podle orientace obrazovky: 720p (zadání kap. 5). */
    fun outputSize(portrait: Boolean): Pair<Int, Int> = if (portrait) 720 to 1280 else 1280 to 720

    /** Začne nahrávat video (obraz s maskou + převedený hlas). */
    suspend fun startRecording(portrait: Boolean): Boolean = withContext(Dispatchers.Default) {
        if (session != null) return@withContext true  // nikdy dvě nahrávání najednou
        val (w, h) = outputSize(portrait)
        val s = RecordingSession(saver, renderer, voice, w, h)
        if (s.start()) { session = s; true } else false
    }

    /** Zastaví a uloží video do galerie. */
    suspend fun stopRecording(): Uri? = withContext(Dispatchers.Default) {
        val s = session ?: return@withContext null
        session = null
        s.stop()
    }

    /** Fotka s maskou a vodoznakem do galerie. */
    suspend fun takePhoto(portrait: Boolean): Uri? {
        val (w, h) = outputSize(portrait)
        val bmp = suspendCancellableCoroutine<Bitmap?> { cont -> renderer.capturePhoto(w, h) { cont.resume(it) } } ?: return null
        return withContext(Dispatchers.IO) { saver.savePhoto(bmp) }
    }

    var profile: FaceProfile? by mutableStateOf(null)
        private set
    var voiceProfile: VoiceProfile? by mutableStateOf(null)
        private set

    suspend fun loadProfile() {
        val (p, v) = withContext(Dispatchers.IO) { store.load() to store.loadVoice() }
        profile = p
        renderer.setProfile(p)
        voiceProfile = v
        voice.setProfile(v)
    }

    suspend fun saveVoice(v: VoiceProfile, pcm: FloatArray) {
        withContext(Dispatchers.IO) { store.saveVoice(v, pcm) }
        voiceProfile = v
        voice.setProfile(v)
    }

    /** Aktuální snímek kamery → profil (neuloží ho). */
    suspend fun profileFromCamera(name: String): FaceProfileBuilder.Result? {
        val frame = suspendCancellableCoroutine<Bitmap?> { cont -> renderer.captureFrame { cont.resume(it) } } ?: return null
        return build(frame, name)
    }

    suspend fun profileFromUri(uri: Uri, name: String): FaceProfileBuilder.Result? {
        val bitmap = withContext(Dispatchers.IO) { decode(uri) } ?: return null
        return build(bitmap, name)
    }

    suspend fun saveProfile(p: FaceProfile) {
        withContext(Dispatchers.IO) { store.save(p) }
        profile = p
        renderer.setProfile(p)
    }

    private suspend fun build(bitmap: Bitmap, name: String) = withContext(Dispatchers.Default) {
        FaceProfileBuilder(context).use { it.build(bitmap, name) }
    }

    private fun decode(uri: Uri): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = max(info.size.width, info.size.height)
                if (longest > MAX_PHOTO) {
                    val s = MAX_PHOTO.toFloat() / longest
                    decoder.setTargetSize((info.size.width * s).toInt(), (info.size.height * s).toInt())
                }
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_PHOTO) sample *= 2
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }
    } catch (e: Exception) {
        null
    }

    fun release() {
        session?.stop()
        session = null
        voice.stop()
        camera.release()
        renderer.release()
        tracker.close()
    }

    private companion object {
        const val MAX_PHOTO = 1600

        /** Vodoznak „JakoTáta“ (zadání kap. 8): bílý text na tmavé poloprůhledné pilulce. */
        fun watermarkBitmap(context: Context): Bitmap {
            val w = 480; val h = 120
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            val scrim = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0x52000000 }
            canvas.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), h / 2.5f, h / 2.5f, scrim)
            val text = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xE6FFFFFF.toInt()
                textSize = h * 0.55f
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = androidx.core.content.res.ResourcesCompat.getFont(context, io.github.mpridal.jakotata.R.font.baloo2)
                    ?.let { android.graphics.Typeface.create(it, android.graphics.Typeface.BOLD) }
            }
            val y = h / 2f - (text.descent() + text.ascent()) / 2f
            canvas.drawText("JakoTáta", w / 2f, y, text)
            return bmp
        }
    }
}

@Composable
fun rememberCameraState(): CameraState {
    val context = LocalContext.current
    val state = remember { CameraState(context) }
    DisposableEffect(state) { onDispose { state.release() } }
    return state
}

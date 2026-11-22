package io.github.mpridal.jakotata.core.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import io.github.mpridal.jakotata.core.face.SkinStats
import io.github.mpridal.jakotata.core.voice.VoiceProfile
import io.github.mpridal.jakotata.core.voice.VoiceProfileRecorder
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Profily v soukromém úložišti aplikace (zadání kap. 4 a 8): složka s fotkou obličeje a JSON.
 * Mimo zálohu Google (dataExtractionRules). Nic se nemaže natvrdo – starý profil jde do koše
 * `files/trash/`, odkud ho lze obnovit.
 */
class ProfileStore(context: Context, val id: String = DEFAULT_ID) {
    private val root = File(context.filesDir, "profiles")
    private val trash = File(context.filesDir, "trash")

    // Složka na profil (táta, máma, děda… max. 5, zadání kap. 1).
    private val dir = File(root, id)

    fun load(): FaceProfile? = try {
        val json = File(dir, FACE_JSON)
        val img = File(dir, FACE_IMAGE)
        if (!json.exists() || !img.exists()) null else {
            val o = JSONObject(json.readText())
            val bitmap = BitmapFactory.decodeFile(img.path) ?: return null
            FaceProfile(
                name = o.optString("name"),
                texture = bitmap,
                coords = o.getJSONArray("coords").toFloatArray(),
                skin = SkinStats.fromArray(o.getJSONArray("skin").toFloatArray()),
                createdAtMs = o.optLong("createdAtMs"),
            )
        }
    } catch (e: Exception) {
        Log.w(TAG, "Profile load failed", e)
        null
    }

    /** Uloží obličej; předchozí obličej přesune do koše. */
    fun save(profile: FaceProfile) {
        trashFiles(FACE_IMAGE, FACE_JSON)
        dir.mkdirs()
        File(dir, FACE_IMAGE).outputStream().use { profile.texture.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        val o = JSONObject()
            .put("version", 1)
            .put("name", profile.name)
            .put("createdAtMs", profile.createdAtMs)
            .put("coords", JSONArray().apply { profile.coords.forEach { put(it.toDouble()) } })
            .put("skin", JSONArray().apply { profile.skin.toArray().forEach { put(it.toDouble()) } })
        File(dir, FACE_JSON).writeText(o.toString())
    }

    fun loadVoice(): VoiceProfile? = try {
        File(dir, VOICE_JSON).takeIf { it.exists() }?.let { VoiceProfile.fromJson(JSONObject(it.readText())) }
    } catch (e: Exception) {
        Log.w(TAG, "Voice load failed", e)
        null
    }

    /** Uloží hlas (JSON + původní WAV); předchozí hlas přesune do koše. */
    fun saveVoice(voice: VoiceProfile, pcm: FloatArray) {
        trashFiles(VOICE_JSON, VOICE_WAV)
        dir.mkdirs()
        VoiceProfileRecorder.writeWav(File(dir, VOICE_WAV), pcm)
        File(dir, VOICE_JSON).writeText(voice.toJson().toString())
    }

    /** Přesune soubory profilu do koše `files/trash/<čas>-default/` (obnovitelné, nic se nemaže). */
    private fun trashFiles(vararg names: String) {
        val existing = names.map { File(dir, it) }.filter { it.exists() }
        if (existing.isEmpty()) return
        val target = File(trash, "${System.currentTimeMillis()}-$id").apply { mkdirs() }
        for (f in existing) if (!f.renameTo(File(target, f.name))) Log.w(TAG, "Move to trash failed: $f")
    }

    // ---- Tréninkové nahrávky pro fázi 2 (zadání kap. 3.4 a 6.7) ----

    private val trainingDir get() = File(dir, "training")

    fun trainingFiles(): List<File> = trainingDir.listFiles { f -> f.extension == "wav" }?.sortedBy { it.name }.orEmpty()

    /** Celková délka tréninkových nahrávek [s] (16bit mono 48 kHz). */
    fun trainingSeconds(): Float = trainingFiles().sumOf { (it.length() - 44).coerceAtLeast(0) } / 2f / 48000f

    fun saveTraining(pcm: FloatArray) {
        trainingDir.mkdirs()
        val next = (trainingFiles().size + 1).toString().padStart(3, '0')
        VoiceProfileRecorder.writeWav(File(trainingDir, "chapter_$next.wav"), pcm)
    }

    /** ZIP s WAV soubory (tréninkové + původní nahrávka hlasu) do zvoleného výstupu. */
    fun exportTrainingZip(out: java.io.OutputStream) {
        java.util.zip.ZipOutputStream(out).use { zip ->
            val files = trainingFiles() + listOfNotNull(File(dir, VOICE_WAV).takeIf { it.exists() })
            for (f in files) {
                zip.putNextEntry(java.util.zip.ZipEntry(f.name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** Celý profil do koše (obnovitelné). */
    fun moveProfileToTrash() {
        if (!dir.exists()) return
        trash.mkdirs()
        if (!dir.renameTo(File(trash, "${System.currentTimeMillis()}-$id"))) Log.w(TAG, "Move to trash failed: $dir")
    }

    private fun JSONArray.toFloatArray() = FloatArray(length()) { getDouble(it).toFloat() }

    companion object {
        private const val TAG = "ProfileStore"
        const val DEFAULT_ID = "default"
        const val MAX_PROFILES = 5

        /** Id existujících profilů (složky s obličejem). */
        fun ids(context: Context): List<String> =
            File(context.filesDir, "profiles").listFiles { f -> f.isDirectory && File(f, "face.json").exists() }
                ?.sortedBy { it.lastModified() }?.map { it.name }.orEmpty()

        fun newId(): String = "p" + System.currentTimeMillis()

        /** Jméno profilu z face.json (prázdné, když chybí). */
        fun name(context: Context, id: String): String = runCatching {
            JSONObject(File(File(context.filesDir, "profiles"), "$id/face.json").readText()).optString("name")
        }.getOrDefault("")
        private const val FACE_IMAGE = "face.jpg"
        private const val FACE_JSON = "face.json"
        private const val VOICE_JSON = "voice.json"
        private const val VOICE_WAV = "voice.wav"
    }
}

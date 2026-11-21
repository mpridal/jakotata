package io.github.mpridal.jakotata.core.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import io.github.mpridal.jakotata.core.face.SkinStats
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Profily v soukromém úložišti aplikace (zadání kap. 4 a 8): složka s fotkou obličeje a JSON.
 * Mimo zálohu Google (dataExtractionRules). Nic se nemaže natvrdo – starý profil jde do koše
 * `files/trash/`, odkud ho lze obnovit.
 */
class ProfileStore(context: Context) {
    private val root = File(context.filesDir, "profiles")
    private val trash = File(context.filesDir, "trash")

    // Fáze 1 umí jeden profil; datový model počítá se složkou na profil (max. 5).
    private val dir = File(root, DEFAULT_ID)

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

    /** Uloží profil; předchozí obličej přesune do koše. */
    fun save(profile: FaceProfile) {
        moveToTrash()
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

    /** Přesune aktuální profil do koše (obnovitelné). */
    fun moveToTrash() {
        if (!dir.exists() || dir.listFiles().isNullOrEmpty()) return
        trash.mkdirs()
        val target = File(trash, "${System.currentTimeMillis()}-$DEFAULT_ID")
        if (!dir.renameTo(target)) Log.w(TAG, "Move to trash failed: $dir")
    }

    private fun JSONArray.toFloatArray() = FloatArray(length()) { getDouble(it).toFloat() }

    private companion object {
        const val TAG = "ProfileStore"
        const val DEFAULT_ID = "default"
        const val FACE_IMAGE = "face.jpg"
        const val FACE_JSON = "face.json"
    }
}

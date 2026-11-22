package io.github.mpridal.jakotata.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Nastavení aplikace (zadání kap. 6.6 a 6.7). */
data class Settings(
    /** "" = podle systému, jinak "cs" / "en" / "es". */
    val language: String = "",
    /** 0 systém, 1 světlé, 2 tmavé. */
    val theme: Int = 0,
    /** Výška videa: 720 nebo 1080. */
    val videoQuality: Int = 720,
    val maxRecordingSec: Int = 120,
    val frontCamera: Boolean = true,
    /** SoundMode.native: 0 ticho, 1 sluchátka, 2 papoušek. */
    val soundMode: Int = 2,
    val maxFaces: Int = 1,
    val autoUpdate: Boolean = true,
    // Rodičovská zóna.
    val watermark: Boolean = true,
    val sharingAllowed: Boolean = true,
    val activeProfile: String = "default",
)

class AppSettings(private val context: Context) {
    val flow: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            language = p[LANGUAGE] ?: "",
            theme = p[THEME] ?: 0,
            videoQuality = p[QUALITY] ?: 720,
            maxRecordingSec = p[MAX_REC] ?: 120,
            frontCamera = p[FRONT] ?: true,
            soundMode = p[SOUND] ?: 2,
            maxFaces = p[FACES] ?: 1,
            autoUpdate = p[AUTO_UPDATE] ?: true,
            watermark = p[WATERMARK] ?: true,
            sharingAllowed = p[SHARING] ?: true,
            activeProfile = p[PROFILE] ?: "default",
        )
    }

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { p ->
            val cur = Settings(
                p[LANGUAGE] ?: "", p[THEME] ?: 0, p[QUALITY] ?: 720, p[MAX_REC] ?: 120, p[FRONT] ?: true,
                p[SOUND] ?: 2, p[FACES] ?: 1, p[AUTO_UPDATE] ?: true, p[WATERMARK] ?: true, p[SHARING] ?: true,
                p[PROFILE] ?: "default",
            )
            val n = transform(cur)
            p[LANGUAGE] = n.language; p[THEME] = n.theme; p[QUALITY] = n.videoQuality; p[MAX_REC] = n.maxRecordingSec
            p[FRONT] = n.frontCamera; p[SOUND] = n.soundMode; p[FACES] = n.maxFaces; p[AUTO_UPDATE] = n.autoUpdate
            p[WATERMARK] = n.watermark; p[SHARING] = n.sharingAllowed; p[PROFILE] = n.activeProfile
        }
    }

    private companion object {
        val LANGUAGE = stringPreferencesKey("language")
        val THEME = intPreferencesKey("theme")
        val QUALITY = intPreferencesKey("video_quality")
        val MAX_REC = intPreferencesKey("max_recording_sec")
        val FRONT = booleanPreferencesKey("front_camera")
        val SOUND = intPreferencesKey("sound_mode")
        val FACES = intPreferencesKey("max_faces")
        val AUTO_UPDATE = booleanPreferencesKey("auto_update")
        val WATERMARK = booleanPreferencesKey("watermark")
        val SHARING = booleanPreferencesKey("sharing_allowed")
        val PROFILE = stringPreferencesKey("active_profile")
    }
}

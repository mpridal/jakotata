package io.github.mpridal.jakotata.core.record

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import java.io.File

/** Nahrávka nebo fotka aplikace v galerii zařízení. */
data class MediaItem(val uri: Uri, val isVideo: Boolean, val dateAddedSec: Long, val durationMs: Long)

/**
 * Vlastní nahrávky a fotky z `Movies/JakoTata` a `Pictures/JakoTata` (zadání kap. 6.5).
 * Mazání jen do koše: systémový koš na API 30+ (s potvrzením systému), jinak vlastní koš v aplikaci.
 */
class MediaLibrary(private val context: Context) {

    fun list(): List<MediaItem> = (query(isVideo = true) + query(isVideo = false)).sortedByDescending { it.dateAddedSec }

    private fun query(isVideo: Boolean): List<MediaItem> {
        val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DATE_ADDED)
            if (isVideo) add(MediaStore.Video.VideoColumns.DURATION)
        }.toTypedArray()
        val (selection, args) = if (Build.VERSION.SDK_INT >= 29) {
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?" to arrayOf("%/${MediaSaver.FOLDER}/%")
        } else {
            @Suppress("DEPRECATION")
            "${MediaStore.MediaColumns.DATA} LIKE ?" to arrayOf("%/${MediaSaver.FOLDER}/%")
        }
        val out = ArrayList<MediaItem>()
        context.contentResolver.query(collection, projection, selection, args, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val durCol = if (isVideo) c.getColumnIndex(MediaStore.Video.VideoColumns.DURATION) else -1
            while (c.moveToNext()) {
                out += MediaItem(
                    ContentUris.withAppendedId(collection, c.getLong(idCol)), isVideo,
                    c.getLong(dateCol), if (durCol >= 0) c.getLong(durCol) else 0L,
                )
            }
        }
        return out
    }

    fun thumbnail(item: MediaItem, size: Int = 320): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= 29) {
            context.contentResolver.loadThumbnail(item.uri, Size(size, size), null)
        } else {
            @Suppress("DEPRECATION")
            if (item.isVideo) MediaStore.Video.Thumbnails.getThumbnail(context.contentResolver, ContentUris.parseId(item.uri), MediaStore.Video.Thumbnails.MINI_KIND, null)
            else MediaStore.Images.Thumbnails.getThumbnail(context.contentResolver, ContentUris.parseId(item.uri), MediaStore.Images.Thumbnails.MINI_KIND, null)
        }
    } catch (e: Exception) {
        null
    }

    /** API 30+: žádost o přesun do systémového koše (systém zobrazí potvrzení). */
    fun trashRequest(items: List<MediaItem>): PendingIntent? =
        if (Build.VERSION.SDK_INT >= 30) MediaStore.createTrashRequest(context.contentResolver, items.map { it.uri }, true) else null

    /** API < 30: vlastní koš v aplikaci – kopie do `files/media_trash/`, teprve pak odebrání z galerie. */
    fun moveToAppTrash(item: MediaItem): Boolean = try {
        val dir = File(context.filesDir, "media_trash").apply { mkdirs() }
        val ext = if (item.isVideo) "mp4" else "jpg"
        val target = File(dir, "${System.currentTimeMillis()}-${ContentUris.parseId(item.uri)}.$ext")
        context.contentResolver.openInputStream(item.uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
        target.length() > 0 && context.contentResolver.delete(item.uri, null, null) > 0
    } catch (e: Exception) {
        false
    }
}

package io.github.mpridal.jakotata.core.record

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ukládání nahrávek a fotek do galerie zařízení (zadání kap. 4 a 6.4): `Movies/JakoTata`
 * a `Pictures/JakoTata`. Popis označuje soubor jako upravený aplikací (kap. 8).
 */
class MediaSaver(private val context: Context) {

    class Target(val uri: Uri, val pfd: ParcelFileDescriptor, internal val file: File?)

    fun newVideo(): Target? = create(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI, Environment.DIRECTORY_MOVIES, "mp4", "video/mp4",
    )

    /** Dokončí video – zviditelní ho v galerii. */
    fun finish(target: Target) {
        runCatching { target.pfd.close() }
        if (target.file != null) {
            MediaScannerConnection.scanFile(context, arrayOf(target.file.path), null, null)
        } else if (Build.VERSION.SDK_INT >= 29) {
            context.contentResolver.update(target.uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        }
    }

    /** Nepovedené nahrávání: rozpracovaný záznam do koše (systémový na API 30+), nic natvrdo. */
    fun abandon(target: Target) {
        runCatching { target.pfd.close() }
        if (Build.VERSION.SDK_INT >= 30) {
            context.contentResolver.update(target.uri, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
                put(MediaStore.MediaColumns.IS_TRASHED, 1)
            }, null, null)
        }
    }

    fun savePhoto(bitmap: Bitmap): Uri? {
        val t = create(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, Environment.DIRECTORY_PICTURES, "jpg", "image/jpeg") ?: return null
        ParcelFileDescriptor.AutoCloseOutputStream(t.pfd).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        if (t.file != null) MediaScannerConnection.scanFile(context, arrayOf(t.file.path), null, null)
        else if (Build.VERSION.SDK_INT >= 29) {
            context.contentResolver.update(t.uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        }
        return t.uri
    }

    private fun create(collection: Uri, dir: String, ext: String, mime: String): Target? {
        val name = "JakoTata_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".$ext"
        return if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$dir/$FOLDER")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
                if (mime.startsWith("video")) put(MediaStore.Video.VideoColumns.DESCRIPTION, DESCRIPTION)
                else put(MediaStore.Images.ImageColumns.DESCRIPTION, DESCRIPTION)
            }
            val uri = context.contentResolver.insert(collection, values) ?: return null
            val pfd = context.contentResolver.openFileDescriptor(uri, "rw") ?: return null
            Target(uri, pfd, null)
        } else {
            @Suppress("DEPRECATION")
            val folder = File(Environment.getExternalStoragePublicDirectory(dir), FOLDER).apply { mkdirs() }
            val file = File(folder, name)
            Target(Uri.fromFile(file), ParcelFileDescriptor.open(file,
                ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_TRUNCATE), file)
        }
    }

    companion object {
        const val FOLDER = "JakoTata"
        const val DESCRIPTION = "Upraveno aplikací JakoTáta (obličej a hlas změněny)"
    }
}

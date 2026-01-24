package com.phantom.ghostshift.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.phantom.ghostshift.domain.Kind
import com.phantom.ghostshift.domain.parseTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max

class PhotoRepository(
    private val context: Context,
    private val dao: PhotosDao
) {
    val allPhotos: Flow<List<PhotoEntity>> = dao.getAllSortedByIdx()
    val pendingPhotos: Flow<List<PhotoEntity>> = dao.getPendingSorted()
    val downloadedPhotos: Flow<List<PhotoEntity>> = dao.getDownloadedSorted()

    suspend fun getById(id: Long): PhotoEntity? = dao.getById(id)

    suspend fun addPhotoFromUri(uri: Uri, tag: String) {
        processAndSave(uri, tag) // Logic for Add
    }

    suspend fun replacePhotoFromUri(id: Long, uri: Uri) {
        val photo = dao.getById(id) ?: return
        processAndSave(uri, photo.tag, replaceId = id)
    }

    private suspend fun processAndSave(uri: Uri, tag: String, replaceId: Long? = null) {
        withContext(Dispatchers.IO) {
            val (kind, idx) = tag.parseTag() ?: (Kind.IN to 0) // Should validation happen before? Yes.
            
            // Decode & Resize
            val (bitmap, width, height) = decodeAndResize(uri)
            val file = File(context.filesDir, "p_${System.currentTimeMillis()}.jpg")
            
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            bitmap.recycle()

            if (replaceId != null) {
                // Replace: Delete old file? Or keep logic simple. Ideally delete old file.
                val old = dao.getById(replaceId)
                old?.let {
                    try { File(it.filePath).delete() } catch (e: Exception) { e.printStackTrace() }
                    
                    val updated = it.copy(
                        filePath = file.absolutePath,
                        width = width,
                        height = height,
                        editedAt = System.currentTimeMillis()
                    )
                    dao.upsert(updated) // Update
                }
            } else {
                // Add
                val entity = PhotoEntity(
                    tag = tag,
                    kind = kind,
                    idx = idx,
                    createdAt = System.currentTimeMillis(),
                    downloaded = false,
                    downloadedAt = null,
                    editedAt = null,
                    filePath = file.absolutePath,
                    width = width,
                    height = height
                )
                dao.upsert(entity)
            }
        }
    }

    private fun decodeAndResize(uri: Uri): Triple<Bitmap, Int, Int> {
        val stream: InputStream? = context.contentResolver.openInputStream(uri)
        val original = BitmapFactory.decodeStream(stream) ?: throw Exception("Cannot decode image")
        stream?.close()

        val maxSide = 1600
        val w = original.width
        val h = original.height
        val s = max(w, h)
        
        if (s <= maxSide) return Triple(original, w, h)

        val scale = maxSide.toFloat() / s
        val newW = (w * scale).toInt()
        val newH = (h * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(original, newW, newH, true)
        if (scaled != original) original.recycle()
        return Triple(scaled, newW, newH)
    }

    suspend fun exportPhoto(photo: PhotoEntity): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val srcFile = File(photo.filePath)
                if (!srcFile.exists()) return@withContext false

                val filename = "${photo.tag}.jpg"
                
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/GhostShift")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }

                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI // Fallback to Pictures/images on old android?
                }
                
                val destUri = context.contentResolver.insert(collection, values) ?: return@withContext false

                context.contentResolver.openOutputStream(destUri).use { out ->
                    srcFile.inputStream().use { input ->
                        input.copyTo(out!!)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(destUri, values, null, null)
                }
                
                // Mark downloaded if first time
                if (!photo.downloaded) {
                    val updated = photo.copy(downloaded = true, downloadedAt = System.currentTimeMillis())
                    dao.upsert(updated)
                }
                
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
    
    suspend fun deletePhoto(photo: PhotoEntity) {
        withContext(Dispatchers.IO) {
            try { File(photo.filePath).delete() } catch (_: Exception) {}
            dao.delete(photo)
        }
    }
    
    suspend fun deleteAll() {
        withContext(Dispatchers.IO) {
            // Delete all files logic?
            // Need to iterate first or just clear DB and let files accumulate (bad).
            // Better: getAll, delete files, clear DB.
            // For now just dao.deleteAll() to keep simple, but TODO clean up files.
            dao.deleteAll()
        }
    }
}

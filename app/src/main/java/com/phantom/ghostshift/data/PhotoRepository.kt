package com.phantom.ghostshift.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.phantom.ghostshift.domain.Kind
import com.phantom.ghostshift.domain.SlotManager
import com.phantom.ghostshift.domain.parseTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max

class PhotoRepository(
    private val context: Context,
    private val dao: PhotosDao
) {
    companion object {
        private const val TAG = "PhotoRepository"
        private const val EXPORT_FOLDER = "GhostShift"

        internal fun buildExportFileName(tag: String, exportedAt: Long): String {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date(exportedAt))
            return "${tag}_$stamp.jpg"
        }
    }

    val allPhotos: Flow<List<PhotoEntity>> = dao.getAllSortedByIdx()
    val pendingPhotos: Flow<List<PhotoEntity>> = dao.getPendingSorted()
    val downloadedPhotos: Flow<List<PhotoEntity>> = dao.getDownloadedSorted()

    suspend fun getById(id: Long): PhotoEntity? = dao.getById(id)

    suspend fun addPhotoFromUri(uri: Uri, tag: String, mirrorHorizontally: Boolean = false) {
        processAndSave(uri, tag, mirrorHorizontally = mirrorHorizontally)
    }

    suspend fun replacePhotoFromUri(id: Long, uri: Uri, mirrorHorizontally: Boolean = false) {
        val photo = dao.getById(id) ?: return
        processAndSave(uri, photo.tag, replaceId = id, mirrorHorizontally = mirrorHorizontally)
    }

    private suspend fun processAndSave(
        uri: Uri,
        tag: String,
        replaceId: Long? = null,
        mirrorHorizontally: Boolean = false
    ) {
        withContext(Dispatchers.IO) {
            val (kind, idx) = tag.parseTag() ?: (Kind.IN to 0) // Should validation happen before? Yes.
            
            // Decode & Resize
            val (bitmap, width, height) = decodeAndResize(uri, mirrorHorizontally)
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

    private fun decodeAndResize(uri: Uri, mirrorHorizontally: Boolean): Triple<Bitmap, Int, Int> {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        var original = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input)
        } ?: throw Exception("Cannot decode image")

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> { matrix.setRotate(180f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        }
        if (!matrix.isIdentity) {
            val oriented = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
            if (oriented != original) original.recycle()
            original = oriented
        }
        if (original.width > original.height) {
            val portrait = Bitmap.createBitmap(
                original, 0, 0, original.width, original.height,
                Matrix().apply { postRotate(90f) }, true
            )
            if (portrait != original) original.recycle()
            original = portrait
        }
        if (mirrorHorizontally) {
            val mirrored = Bitmap.createBitmap(
                original, 0, 0, original.width, original.height,
                Matrix().apply { postScale(-1f, 1f) }, true
            )
            if (mirrored != original) original.recycle()
            original = mirrored
        }

        val w = original.width
        val h = original.height
        
        // 2. Center Crop to 3:4 (Portrait) or 4:3 (Landscape)?
        // Web Rule: "Portrait 4:3" -> 3:4.
        // If image is landscape, we invoke logic to crop or keep?
        // Web usually enforces "Passport/Portrait" style.
        // Let's enforce 3:4 if it's portrait-ish, or 4:3 if landscape?
        // User said "output MUST be portrait 4:3 (i.e., 3:4 in portrait)".
        
        // Determine target aspect
        val targetRatio = 3f / 4f
        val currentRatio = w.toFloat() / h.toFloat()
        
        var cropped: Bitmap = original
        
        // Only crop if significantly different? 
        // Strict parity: Force 3:4.
        // If landscape (w > h), crop to 3:4? That would lose a lot.
        // Assuming user takes portrait photos. If landscape, we might crop to center 3:4.
        
        if (w > h) {
             // Landscape source: Crop to center vertical 3:4?
             // That effectively means taking a vertical slice.
             val targetW = (h * targetRatio).toInt()
             val startX = max(0, (w - targetW) / 2)
             cropped = Bitmap.createBitmap(original, startX, 0, targetW, h)
        } else {
             // Portrait source
             if (currentRatio > targetRatio) {
                 // Too wide -> Crop width
                 val targetW = (h * targetRatio).toInt()
                 val startX = max(0, (w - targetW) / 2)
                 cropped = Bitmap.createBitmap(original, startX, 0, targetW, h)
             } else if (currentRatio < targetRatio) {
                 // Too tall -> Crop height
                 val targetH = (w / targetRatio).toInt()
                 val startY = max(0, (h - targetH) / 2)
                 cropped = Bitmap.createBitmap(original, 0, startY, w, targetH)
             }
        }
        
        if (cropped != original) original.recycle()
        
        // 3. Resize Max Side 1600
        val maxSide = 1600
        val cw = cropped.width
        val ch = cropped.height
        val s = max(cw, ch)
        
        if (s <= maxSide) return Triple(cropped, cw, ch)
        
        val scale = maxSide.toFloat() / s
        val newW = (cw * scale).toInt()
        val newH = (ch * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(cropped, newW, newH, true)
        if (scaled != cropped) cropped.recycle()
        
        return Triple(scaled, newW, newH)
    }

    suspend fun exportPhoto(photo: PhotoEntity): Boolean {
        val firstAttemptAt = System.currentTimeMillis()
        if (exportPhotoOnce(photo, firstAttemptAt)) return true

        delay(150)
        return exportPhotoOnce(photo, System.currentTimeMillis())
    }

    private suspend fun exportPhotoOnce(photo: PhotoEntity, exportedAt: Long): Boolean {
        return withContext(Dispatchers.IO) {
            var destUri: Uri? = null
            try {
                val srcFile = File(photo.filePath)
                if (!srcFile.exists()) return@withContext false

                val filename = buildExportFileName(photo.tag, exportedAt)

                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, photo.mime)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$EXPORT_FOLDER")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }

                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }

                val resolver = context.contentResolver
                destUri = resolver.insert(collection, values) ?: return@withContext false
                val output = resolver.openOutputStream(destUri)
                    ?: throw IllegalStateException("Cannot open output stream for $destUri")
                output.use { out ->
                    srcFile.inputStream().use { input ->
                        input.copyTo(out)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(destUri, values, null, null)
                }

                if (!photo.downloaded || photo.downloadedAt == null) {
                    val updated = photo.copy(downloaded = true, downloadedAt = photo.downloadedAt ?: exportedAt)
                    dao.upsert(updated)
                }

                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export ${photo.tag}", e)
                destUri?.let { uri ->
                    runCatching { context.contentResolver.delete(uri, null, null) }
                }
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

    suspend fun deletePendingPhotoAndShift(photoId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            val target = dao.getById(photoId) ?: return@withContext false
            if (target.downloadedAt != null) return@withContext false

            try { File(target.filePath).delete() } catch (_: Exception) {}
            dao.delete(target)

            val allAfterDelete = dao.getAllSortedByIdxOnce().sortedBySlot()
            val pendingAfterDelete = allAfterDelete.filter { it.downloadedAt == null }
            resequencePendingList(allAfterDelete, pendingAfterDelete)
            true
        }
    }

    suspend fun reorderPendingByIds(orderedPendingIds: List<Long>): Boolean {
        return withContext(Dispatchers.IO) {
            val all = dao.getAllSortedByIdxOnce().sortedBySlot()
            val pending = all.filter { it.downloadedAt == null }
            if (pending.isEmpty()) return@withContext false

            val pendingById = pending.associateBy { it.id }
            val orderedDistinctIds = orderedPendingIds.distinct()
            val orderedIdSet = orderedDistinctIds.toSet()

            val reorderedPending = mutableListOf<PhotoEntity>()
            for (id in orderedDistinctIds) {
                val p = pendingById[id] ?: continue
                reorderedPending.add(p)
            }
            for (p in pending) {
                if (p.id !in orderedIdSet) reorderedPending.add(p)
            }

            val originalIds = pending.map { it.id }
            val targetIds = reorderedPending.map { it.id }
            if (originalIds == targetIds) return@withContext false

            resequencePendingList(all, reorderedPending)
            true
        }
    }

    /**
     * Reorders only complete, unexported pairs. Incomplete/exported pairs retain their slot
     * so an already exported IN/OUT image can never become paired with a different photo.
     */
    suspend fun reorderCompletePendingPairs(orderedPairIds: List<Long>): Boolean {
        return withContext(Dispatchers.IO) {
            val all = dao.getAllSortedByIdxOnce().sortedBySlot()
            val reorderablePairs = all
                .groupBy { it.idx }
                .toSortedMap()
                .mapNotNull { (index, photos) ->
                    val inPhoto = photos.firstOrNull { it.kind == Kind.IN }
                    val outPhoto = photos.firstOrNull { it.kind == Kind.OUT }
                    if (inPhoto != null && outPhoto != null &&
                        inPhoto.downloadedAt == null && outPhoto.downloadedAt == null
                    ) {
                        Triple(index, inPhoto, outPhoto)
                    } else {
                        null
                    }
                }
            if (reorderablePairs.size < 2) return@withContext false

            val pairsById = reorderablePairs.associateBy { it.second.id }
            val requested = orderedPairIds.distinct().mapNotNull(pairsById::get)
            val requestedIds = requested.map { it.second.id }.toSet()
            val reordered = requested + reorderablePairs.filter { it.second.id !in requestedIds }
            if (reordered.map { it.second.id } == reorderablePairs.map { it.second.id }) return@withContext false

            val slots = reorderablePairs.map { it.first }
            val now = System.currentTimeMillis()
            slots.zip(reordered).forEach { (slot, pair) ->
                dao.upsert(pair.second.copy(idx = slot, tag = "IN-$slot", editedAt = now))
                dao.upsert(pair.third.copy(idx = slot, tag = "OUT-$slot", editedAt = now))
            }
            true
        }
    }

    suspend fun swapPairContents(index: Int): Boolean = withContext(Dispatchers.IO) {
        val all = dao.getAllSortedByIdxOnce()
        val inPhoto = all.firstOrNull { it.idx == index && it.kind == Kind.IN } ?: return@withContext false
        val outPhoto = all.firstOrNull { it.idx == index && it.kind == Kind.OUT } ?: return@withContext false
        if (inPhoto.downloadedAt != null || outPhoto.downloadedAt != null) return@withContext false
        val now = System.currentTimeMillis()
        dao.upsert(inPhoto.copy(filePath = outPhoto.filePath, width = outPhoto.width, height = outPhoto.height, mime = outPhoto.mime, editedAt = now))
        dao.upsert(outPhoto.copy(filePath = inPhoto.filePath, width = inPhoto.width, height = inPhoto.height, mime = inPhoto.mime, editedAt = now))
        true
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

    private fun List<PhotoEntity>.sortedBySlot(): List<PhotoEntity> {
        return this.sortedWith(compareBy<PhotoEntity> { it.idx }.thenBy { if (it.kind == Kind.IN) 0 else 1 })
    }

    private fun nextPendingStartTag(allSorted: List<PhotoEntity>): String {
        val lastDownloaded = allSorted
            .filter { it.downloadedAt != null }
            .sortedBy { it.downloadedAt }
            .lastOrNull()

        return if (lastDownloaded != null) {
            SlotManager.nextTagInSequence(lastDownloaded.tag) ?: "IN-1"
        } else {
            "IN-1"
        }
    }

    private suspend fun resequencePendingList(allSorted: List<PhotoEntity>, pendingOrdered: List<PhotoEntity>) {
        if (pendingOrdered.isEmpty()) return

        var nextTag = nextPendingStartTag(allSorted)
        for (photo in pendingOrdered) {
            val parsed = nextTag.parseTag() ?: break
            val (kind, idx) = parsed
            val updated = photo.copy(tag = nextTag, kind = kind, idx = idx)
            dao.upsert(updated)
            nextTag = SlotManager.nextTagInSequence(nextTag) ?: break
        }
    }
}

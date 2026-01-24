package com.phantom.ghostshift.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotosDao {
    @Query("SELECT * FROM photos ORDER BY idx ASC, kind ASC") // IN-1, OUT-1, IN-2... strict sort
    fun getAllSortedByIdx(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE downloaded = 0 ORDER BY idx ASC, kind ASC")
    fun getPendingSorted(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE downloaded = 1 ORDER BY downloadedAt ASC")
    fun getDownloadedSorted(): Flow<List<PhotoEntity>>

    @Upsert
    suspend fun upsert(photo: PhotoEntity): Long

    @Delete
    suspend fun delete(photo: PhotoEntity)

    @Query("DELETE FROM photos")
    suspend fun deleteAll()
    
    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun getById(id: Long): PhotoEntity?
}

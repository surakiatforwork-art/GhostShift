package com.phantom.ghostshift.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.phantom.ghostshift.domain.Kind

@Entity(tableName = "photos")
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tag: String,
    val kind: Kind,
    val idx: Int,
    val createdAt: Long,
    val downloaded: Boolean,
    val downloadedAt: Long?,
    val editedAt: Long?,
    val filePath: String,
    val width: Int,
    val height: Int,
    val mime: String = "image/jpeg"
)

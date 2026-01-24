package com.phantom.ghostshift.data

import android.content.Context

interface AppContainer {
    val photoRepository: PhotoRepository
    val userPreferences: UserPreferences
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    override val photoRepository: PhotoRepository by lazy {
        PhotoRepository(context, AppDatabase.getDatabase(context).photosDao())
    }

    override val userPreferences: UserPreferences by lazy {
        UserPreferences(context.dataStore)
    }
}

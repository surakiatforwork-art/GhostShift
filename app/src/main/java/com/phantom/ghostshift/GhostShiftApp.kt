package com.phantom.ghostshift

import android.app.Application
import com.phantom.ghostshift.data.AppContainer
import com.phantom.ghostshift.data.DefaultAppContainer

class GhostShiftApp : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}

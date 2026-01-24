package com.phantom.ghostshift

import android.app.Application

class GhostShiftApp : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}

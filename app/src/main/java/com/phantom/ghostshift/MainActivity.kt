package com.phantom.ghostshift

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import com.phantom.ghostshift.ui.SharedPhotoInput
import com.phantom.ghostshift.ui.theme.GhostShiftTheme

class MainActivity : ComponentActivity() {
    private var onSharedPhotosReceived: ((List<SharedPhotoInput>) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appContainer = (application as GhostShiftApp).container
        
        setContent {
            GhostShiftTheme {
                // Manual DI for ViewModel
                val factory = com.phantom.ghostshift.ui.MainViewModelFactory(
                    repo = appContainer.photoRepository,
                    prefs = appContainer.userPreferences,
                    alarmScheduler = com.phantom.ghostshift.system.AlarmScheduler(this)
                )
                val viewModel: com.phantom.ghostshift.ui.MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = factory)

                LaunchedEffect(viewModel) {
                    onSharedPhotosReceived = viewModel::addSharedPhotos
                    extractSharedPhotos(intent).takeIf { it.isNotEmpty() }?.let(viewModel::addSharedPhotos)
                }
                com.phantom.ghostshift.ui.MainScreen(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractSharedPhotos(intent).takeIf { it.isNotEmpty() }?.let { inputs ->
            onSharedPhotosReceived?.invoke(inputs)
        }
    }

    private fun extractSharedPhotos(intent: Intent?): List<SharedPhotoInput> {
        val shareIntent = intent ?: return emptyList()
        if (shareIntent.action !in setOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)) return emptyList()

        val uris = if (shareIntent.action == Intent.ACTION_SEND_MULTIPLE) {
            shareIntent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        } else {
            listOfNotNull(shareIntent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
        }
        val remarks = shareIntent.getStringArrayListExtra(EXTRA_REMARKS).orEmpty()
        return uris.mapIndexed { index, uri -> SharedPhotoInput(uri, remarks.getOrNull(index)) }
    }

    private companion object {
        const val EXTRA_REMARKS = "com.phantom.ghostshift.EXTRA_REMARKS"
    }
}

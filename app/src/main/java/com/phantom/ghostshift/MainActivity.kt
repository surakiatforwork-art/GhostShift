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
    private var pendingSharedPhotos: List<SharedPhotoInput>? = null
    private var initialShareHandled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialShareHandled = savedInstanceState?.getBoolean(STATE_INITIAL_SHARE_HANDLED) ?: false
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
                    pendingSharedPhotos?.let { inputs ->
                        pendingSharedPhotos = null
                        viewModel.addSharedPhotos(inputs)
                    }
                    if (!initialShareHandled) {
                        receiveSharedIntent(intent)
                        initialShareHandled = true
                    }
                }
                com.phantom.ghostshift.ui.MainScreen(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isVisible = true
        com.phantom.ghostshift.system.AlertOverlayController.dismiss(applicationContext)
    }

    override fun onPause() {
        isVisible = false
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveSharedIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_INITIAL_SHARE_HANDLED, initialShareHandled)
        super.onSaveInstanceState(outState)
    }

    /**
     * A shared intent remains attached to the activity unless it is cleared. Without doing so,
     * Android can replay the same images when this activity is recreated.
     */
    private fun receiveSharedIntent(intent: Intent?) {
        val inputs = extractSharedPhotos(intent)
        if (inputs.isEmpty()) return

        val handler = onSharedPhotosReceived
        if (handler == null) {
            pendingSharedPhotos = inputs
        } else {
            handler(inputs)
        }
        setIntent(Intent())
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

    companion object {
        const val EXTRA_REMARKS = "com.phantom.ghostshift.EXTRA_REMARKS"
        const val STATE_INITIAL_SHARE_HANDLED = "initial_share_handled"
        @Volatile var isVisible: Boolean = false
    }
}

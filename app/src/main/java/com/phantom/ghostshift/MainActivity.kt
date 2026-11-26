package com.phantom.ghostshift

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.isSystemInDarkTheme
import kotlinx.coroutines.launch
import com.phantom.ghostshift.ui.SharedPhotoInput
import com.phantom.ghostshift.ui.theme.GhostShiftTheme

class MainActivity : ComponentActivity() {
    private var onSharedPhotosReceived: ((List<SharedPhotoInput>) -> Unit)? = null
    private var pendingSharedPhotos: List<SharedPhotoInput>? = null
    private var initialShareHandled = false
    // Registered only by the visible CameraScreen. Consume the matching UP
    // even if the photo has already closed the camera on DOWN.
    var cameraShutterAction: (() -> Unit)? = null
    private val cameraVolumeKeys = mutableSetOf<Int>()

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val key = event.keyCode
        if (key == android.view.KeyEvent.KEYCODE_VOLUME_UP ||
            key == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
            when (event.action) {
                android.view.KeyEvent.ACTION_DOWN -> {
                    val action = cameraShutterAction
                    if (action != null || key in cameraVolumeKeys) {
                        if (event.repeatCount == 0 && cameraVolumeKeys.add(key)) action?.invoke()
                        return true
                    }
                }
                android.view.KeyEvent.ACTION_UP -> {
                    if (cameraVolumeKeys.remove(key)) return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialShareHandled = savedInstanceState?.getBoolean(STATE_INITIAL_SHARE_HANDLED) ?: false
        val appContainer = (application as GhostShiftApp).container
        
        setContent {
            val themeMode by appContainer.userPreferences.themeMode.collectAsState(initial = "system")
            val scope = rememberCoroutineScope()
            val dark = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            GhostShiftTheme(darkTheme = dark) {
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
                com.phantom.ghostshift.ui.UpdateHost {
                    com.phantom.ghostshift.ui.MainScreen(viewModel, themeMode = themeMode, onThemeModeChanged = { mode -> scope.launch { appContainer.userPreferences.setThemeMode(mode) } })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requestHighRefreshRate()
        isVisible = true
        com.phantom.ghostshift.system.AlertOverlayController.dismiss(applicationContext)
    }

    // This is a window-level preference, not a system setting. Match the current
    // resolution and request up to 120 Hz; Android can still honor battery,
    // thermal and user display restrictions. Older 60 Hz phones keep their mode.
    @Suppress("DEPRECATION")
    private fun requestHighRefreshRate() {
        val display = if (android.os.Build.VERSION.SDK_INT >= 30) this.display
            else windowManager.defaultDisplay
        val current = display?.mode ?: return
        val candidate = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth &&
                it.physicalHeight == current.physicalHeight && it.refreshRate <= 120.5f }
            .maxByOrNull { it.refreshRate } ?: return
        if (candidate.refreshRate <= 60.5f) return
        val attributes = window.attributes
        if (attributes.preferredDisplayModeId != candidate.modeId) {
            attributes.preferredDisplayModeId = candidate.modeId
            window.attributes = attributes
        }
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

package com.phantom.ghostshift

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import com.phantom.ghostshift.ui.theme.GhostShiftTheme

class MainActivity : ComponentActivity() {
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
                
                com.phantom.ghostshift.ui.MainScreen(viewModel)
            }
        }
    }
}

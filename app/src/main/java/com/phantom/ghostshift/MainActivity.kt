package com.phantom.ghostshift

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import com.phantom.ghostshift.ui.theme.GhostShiftTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GhostShiftTheme {
                Text(text = "GhostShift (Loading...)")
            }
        }
    }
}

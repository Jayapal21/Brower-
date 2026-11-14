package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.ObscuraApp
import com.example.ui.ObscuraViewModel
import com.example.ui.theme.ObscuraTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ObscuraViewModel by viewModels()

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val widthSizeClass = calculateWindowSizeClass(this).widthSizeClass
            val settings by viewModel.settings.collectAsStateWithLifecycle()

            ObscuraTheme(darkTheme = settings.darkTheme) {
                ObscuraApp(
                    viewModel = viewModel,
                    widthSizeClass = widthSizeClass,
                )
            }
        }
    }
}

package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.motion.AdaptiveMotionEngine
import com.example.ui.ObscuraApp
import com.example.ui.ObscuraViewModel
import com.example.ui.theme.ObscuraTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ObscuraViewModel by viewModels()
    private lateinit var motionEngine: AdaptiveMotionEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        motionEngine = AdaptiveMotionEngine(applicationContext)
        enableEdgeToEdge()
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val motionPolicy by motionEngine.policy.collectAsStateWithLifecycle()
            val motionTelemetry by motionEngine.telemetry.collectAsStateWithLifecycle()

            LaunchedEffect(settings.motionMode) {
                motionEngine.setModePreference(settings.motionMode)
            }

            LaunchedEffect(settings.springStiffness, settings.springDamping) {
                motionEngine.setSpringTuning(settings.springStiffness, settings.springDamping)
            }

            LaunchedEffect(settings.darkTheme) {
                if (settings.darkTheme) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                        navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                    )
                } else {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                        ),
                        navigationBarStyle = SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                        ),
                    )
                }
            }

            ObscuraTheme(
                darkTheme = settings.darkTheme,
                motionPolicy = motionPolicy,
            ) {
                ObscuraApp(
                    viewModel = viewModel,
                    motionTelemetry = motionTelemetry,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        motionEngine.attach(this)
    }

    override fun onStop() {
        motionEngine.detach()
        super.onStop()
    }

    override fun onDestroy() {
        motionEngine.detach()
        super.onDestroy()
    }
}

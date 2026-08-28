package com.example.englishcantoneselearning

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.englishcantoneselearning.ui.LearningApp
import com.example.englishcantoneselearning.ui.ReaderViewModel
import com.example.englishcantoneselearning.ui.material.MaterialViewModel
import com.example.englishcantoneselearning.ui.theme.EnglishCantoneseLearningTheme

class MainActivity : ComponentActivity() {
    private val container: AppContainer
        get() = (application as LearningApplication).container

    private val readerViewModel: ReaderViewModel by viewModels {
        ReaderViewModel.Factory(container)
    }

    private val materialViewModel: MaterialViewModel by viewModels {
        MaterialViewModel.Factory(container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        setContent {
            EnglishCantoneseLearningTheme {
                LearningApp(
                    readerViewModel = readerViewModel,
                    materialViewModel = materialViewModel,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        readerViewModel.refreshTtsAvailability()
        materialViewModel.refreshTtsAvailability()
    }

    override fun onStop() {
        if (!isChangingConfigurations) {
            readerViewModel.onAppBackgrounded()
            materialViewModel.onAppBackgrounded()
        }
        super.onStop()
    }
}

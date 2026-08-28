package com.example.englishcantoneselearning

import android.app.Application

class LearningApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onTerminate() {
        container.close()
        super.onTerminate()
    }
}

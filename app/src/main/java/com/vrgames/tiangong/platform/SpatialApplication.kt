package com.vrgames.tiangong.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.vrgames.tiangong.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}

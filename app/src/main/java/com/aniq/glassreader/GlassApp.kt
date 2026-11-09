package com.aniq.glassreader

import android.app.Application
import com.aniq.glassreader.data.Store

class GlassApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        com.aniq.glassreader.ui.PageSound.init(this)
    }
}

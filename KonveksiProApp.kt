package com.dooyappwork.konveksipro

import android.app.Application
import com.dooyappwork.konveksipro.core.utils.AppContainer

/** Application class - pemegang AppContainer (dependency sederhana, tanpa DI framework). */
class KonveksiProApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    companion object {
        lateinit var instance: KonveksiProApp
            private set
    }
}

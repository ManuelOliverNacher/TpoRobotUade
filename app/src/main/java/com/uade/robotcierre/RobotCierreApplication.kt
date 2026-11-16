package com.uade.robotcierre

import android.app.Application

/** Application: crea el [AppContainer] (inyección de dependencias manual, sin Hilt). */
class RobotCierreApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

package dev.jesiel.launcher

import android.app.Application
import dev.jesiel.launcher.data.LockScreen

class LauncherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Tela de bloqueio do Launcher: o receptor de "tela apagou" vive enquanto o app vive.
        LockScreen.sync(this)
    }
}

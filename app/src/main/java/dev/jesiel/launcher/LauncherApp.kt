package dev.jesiel.launcher

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import dev.jesiel.launcher.data.LockScreen

class LauncherApp : Application(), ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()

    /**
     * Um só estado para o app inteiro: tela inicial, Ajustes, Estúdio (que abrem como app à
     * parte) e tela de bloqueio veem as mesmas preferências na hora.
     */
    val launcherViewModel: LauncherViewModel by lazy {
        ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(this))[LauncherViewModel::class.java]
    }

    override fun onCreate() {
        super.onCreate()
        // Tela de bloqueio do Launcher: o receptor de "tela apagou" vive enquanto o app vive.
        LockScreen.sync(this)
    }
}

/** O estado compartilhado do launcher (ver [LauncherApp.launcherViewModel]). */
val android.app.Activity.launcherViewModel: LauncherViewModel get() = (application as LauncherApp).launcherViewModel

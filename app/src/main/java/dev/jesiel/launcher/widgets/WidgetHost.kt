package dev.jesiel.launcher.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.UserManager

/** Encapsula o [AppWidgetHost] que hospeda os widgets Android na tela inicial. */
class WidgetHost(context: Context) {

    private val appContext = context.applicationContext
    val manager: AppWidgetManager = AppWidgetManager.getInstance(appContext)
    val packageManager = appContext.packageManager!!
    val host = AppWidgetHost(appContext, HOST_ID)

    fun providers(): List<AppWidgetProviderInfo> {
        val users = appContext.getSystemService(UserManager::class.java).userProfiles
        return users.flatMap { manager.getInstalledProvidersForProfile(it) }
    }

    fun allocateId(): Int = host.allocateAppWidgetId()

    fun bindIfAllowed(id: Int, provider: AppWidgetProviderInfo): Boolean =
        manager.bindAppWidgetIdIfAllowed(id, provider.profile, provider.provider, null)

    fun info(id: Int): AppWidgetProviderInfo? = manager.getAppWidgetInfo(id)

    fun createView(context: Context, id: Int): AppWidgetHostView? {
        val info = info(id) ?: return null
        return host.createView(context, id, info)
    }

    fun delete(id: Int) = host.deleteAppWidgetId(id)

    fun startListening() = runCatching { host.startListening() }

    fun stopListening() = runCatching { host.stopListening() }

    companion object {
        const val HOST_ID = 1024
    }
}

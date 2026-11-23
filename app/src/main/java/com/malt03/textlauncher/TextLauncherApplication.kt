package com.malt03.textlauncher

import android.app.Application
import android.content.ComponentName
import android.content.pm.LauncherApps
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.notification.NotificationListener
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.SettingsCache
import com.android.launcher3.util.SettingsCache.NOTIFICATION_BADGING_URI
import com.malt03.textlauncher.data.AppRepository
import com.malt03.textlauncher.data.HomeModel
import com.malt03.textlauncher.data.LauncherDatabase
import com.malt03.textlauncher.data.ModelLauncherCallbacks
import com.malt03.textlauncher.data.SearchStore
import com.malt03.textlauncher.data.ShortcutRepository
import com.malt03.textlauncher.icons.AppIconLoader
import com.malt03.textlauncher.icons.IconSettings
import com.malt03.textlauncher.ui.home.HomeGridInitializer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class TextLauncherApplication : Application() {
    /** Single thread for the search database writes, so they are applied in order. */
    val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    lateinit var database: LauncherDatabase
        private set
    lateinit var searchStore: SearchStore
        private set
    lateinit var appRepository: AppRepository
        private set
    lateinit var homeModel: HomeModel
        private set
    lateinit var shortcutRepository: ShortcutRepository
        private set
    lateinit var iconSettings: IconSettings
        private set
    lateinit var iconLoader: AppIconLoader
        private set

    override fun onCreate() {
        super.onCreate()
        database = LauncherDatabase(this)
        searchStore = SearchStore(database, ioExecutor)
        ioExecutor.execute { searchStore.loadBlocking() }
        iconSettings = IconSettings(this)
        iconLoader = AppIconLoader(this, iconSettings)
        appRepository = AppRepository(this, iconLoader)
        shortcutRepository = ShortcutRepository(this)
        // LauncherModel: one LauncherApps callback for the app list and the shortcuts.
        getSystemService(LauncherApps::class.java).registerCallback(
            ModelLauncherCallbacks(appRepository, shortcutRepository), android.os.Handler(mainLooper),
        )
        homeModel = HomeModel(this, database, shortcutRepository, iconLoader.iconCache)
        // InvariantDeviceProfile: the grid from the device, until the home screen sets its own.
        InvariantDeviceProfile.setGridInitializer(HomeGridInitializer)

        // ModelInitializer: when notification dots are turned back on, the listener (which unbinds
        // itself when they are turned off) is bound again.
        SettingsCache.INSTANCE.get(this).getListenableRef(NOTIFICATION_BADGING_URI).forEach(MAIN_EXECUTOR) {
                dotsEnabled ->
            if (dotsEnabled) {
                NotificationListener.requestRebind(ComponentName(this, NotificationListener::class.java))
            }
        }
    }
}

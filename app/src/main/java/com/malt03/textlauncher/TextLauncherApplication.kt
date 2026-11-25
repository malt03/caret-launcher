package com.malt03.textlauncher

import android.app.Application
import android.content.ComponentName
import android.content.pm.LauncherApps
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherModel
import com.android.launcher3.model.AllAppsList
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.ItemInstallQueue
import com.android.launcher3.model.LoaderTask
import com.android.launcher3.model.ModelDbController
import com.android.launcher3.model.WorkspaceItemSerializer
import com.android.launcher3.model.WorkspaceItemSpaceFinder
import com.android.launcher3.notification.NotificationListener
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.SettingsCache
import com.android.launcher3.util.SettingsCache.NOTIFICATION_BADGING_URI
import com.malt03.textlauncher.data.AppRepository
import com.malt03.textlauncher.data.LauncherAppFilter
import com.malt03.textlauncher.data.LauncherDatabase
import com.malt03.textlauncher.data.SearchStore
import com.malt03.textlauncher.data.ShareTargetStore
import com.malt03.textlauncher.icons.AppIconLoader
import com.malt03.textlauncher.icons.IconSettings
import com.malt03.textlauncher.ui.home.HomeGridInitializer
import com.malt03.textlauncher.ui.home.HomeLoaderHooks
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
    /** The apps the typed text is shared with (docs/spec.md 2.7). */
    lateinit var shareTargets: ShareTargetStore
        private set
    /** Launcher3's LauncherModel: the home screen's items and the all apps list. */
    lateinit var model: LauncherModel
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
        shareTargets = ShareTargetStore(this)
        iconSettings = IconSettings(this)
        iconLoader = AppIconLoader(this, iconSettings)
        appRepository = AppRepository()
        // LauncherModel (Dagger in Launcher3): the all apps list leaves the launcher itself out,
        // and the favorites table is in the app's database.
        val iconCache = iconLoader.iconCache
        val allAppsList = AllAppsList(iconCache, LauncherAppFilter(this))
        val bgDataModel = BgDataModel()
        val hooks = HomeLoaderHooks(this)
        model = LauncherModel(
            this, iconCache, allAppsList, bgDataModel,
            { name, binder ->
                LoaderTask(
                    this, LauncherAppState.getIDP(this), model, iconCache, allAppsList, bgDataModel,
                    name, binder, hooks,
                )
            },
            ModelDbController(this, database),
        )
        LauncherAppState.getInstance(this).setModel(model)
        // ItemInstallQueue: the items other apps pin (AddItemActivity), added once the home screen
        // is loaded and resumed.
        val idp = LauncherAppState.getIDP(this)
        ItemInstallQueue.INSTANCE.set(
            ItemInstallQueue(
                this,
                { WorkspaceItemSpaceFinder(bgDataModel, idp, model) },
                WorkspaceItemSerializer(this, iconCache, idp),
                { model },
            ),
        )
        // ModelInitializer: the LauncherApps callback, and the icons made again when the icon
        // settings change (refreshAndReloadLauncher).
        getSystemService(LauncherApps::class.java).registerCallback(model.newModelCallbacks())
        iconLoader.addChangeListener { model.forceReload("icon settings") }
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

package com.malt03.textlauncher

import android.app.Application
import com.google.android.material.color.DynamicColors
import com.malt03.textlauncher.data.AppRepository
import com.malt03.textlauncher.data.HomeModel
import com.malt03.textlauncher.data.LauncherDatabase
import com.malt03.textlauncher.data.SearchStore
import com.malt03.textlauncher.icons.AppIconLoader
import com.malt03.textlauncher.icons.IconSettings
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class TextLauncherApplication : Application() {
    /** Single thread for all database writes, so they are applied in order. */
    val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    lateinit var database: LauncherDatabase
        private set
    lateinit var searchStore: SearchStore
        private set
    lateinit var appRepository: AppRepository
        private set
    lateinit var homeModel: HomeModel
        private set
    lateinit var iconSettings: IconSettings
        private set
    lateinit var iconLoader: AppIconLoader
        private set

    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        database = LauncherDatabase(this)
        searchStore = SearchStore(database, ioExecutor)
        ioExecutor.execute { searchStore.loadBlocking() }
        appRepository = AppRepository(this)
        homeModel = HomeModel(database, ioExecutor)
        iconSettings = IconSettings(this)
        iconLoader = AppIconLoader(this, iconSettings)
    }
}

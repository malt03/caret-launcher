/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * Modified for TextLauncher: a stand-in that keeps only what TextLauncher uses.
 * See docs/launcher3-porting.md and third_party/launcher3/UPSTREAM.md.
 */

package com.android.launcher3.dagger

import android.content.Context
import android.net.Uri
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.concurrent.annotations.Background
import com.android.launcher3.concurrent.annotations.BackgroundContext
import com.android.launcher3.concurrent.annotations.LightweightBackground
import com.android.launcher3.concurrent.annotations.LightweightBackgroundPriority
import com.android.launcher3.icons.IconCache
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.Executors
import com.android.launcher3.util.LooperExecutor
import com.android.launcher3.util.SettingsCache.NOTIFICATION_BADGING_URI
import com.android.launcher3.util.ScreenOnTracker
import com.android.launcher3.widgetpicker.LauncherWidgetPickerModule
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import java.util.concurrent.Executor
import javax.inject.Named
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.asCoroutineDispatcher

/**
 * Stand-in for Launcher3's LauncherAppModule (android-17.0.0_r1). TextLauncher: of
 * BootUnsafeModules only LauncherWidgetPickerModule, of BootSafeModules only [SettingsModule]; the
 * rest is [TextLauncherAppModule].
 */
@Module(
    includes = [LauncherWidgetPickerModule::class, SettingsModule::class, TextLauncherAppModule::class]
)
class LauncherAppModule

/**
 * Stand-in for Launcher3's SettingsModule (android-17.0.0_r1): the settings that are on when they
 * have no value. TextLauncher: only notification dots (no touchpad).
 */
@Module
object SettingsModule {
    @Provides
    @IntoSet
    @Named("SETTINGS_ENABLED_BY_DEFAULT")
    fun provideNotificationBadgingDefaults(): Uri = NOTIFICATION_BADGING_URI
}

/**
 * TextLauncher: what the widget picker needs from modules that are not ported. The executors are
 * those of LauncherExecutorsModule (@Background is Executors.ORDERED_BG_EXECUTOR, and its
 * dispatcher is the @BackgroundContext of ExecutorsModule). The rest are the objects that are
 * made without Dagger in TextLauncher: the grid and the icon cache, which the home screen sets
 * (LauncherAppState), and the users and the screen state, one per process.
 */
@Module
object TextLauncherAppModule {
    @Provides
    @LauncherAppSingleton
    @Background
    fun provideBackgroundExecutor(): Executor = Executors.ORDERED_BG_EXECUTOR

    @Provides
    @LauncherAppSingleton
    @BackgroundContext
    fun provideBackgroundContext(): CoroutineContext =
        Executors.ORDERED_BG_EXECUTOR.asCoroutineDispatcher()

    /** LauncherExecutorsModule: @LightweightBackground(UI) is Executors.UI_HELPER_EXECUTOR. */
    @Provides
    @LauncherAppSingleton
    @LightweightBackground(LightweightBackgroundPriority.UI)
    fun provideUiHelperLooperExecutor(): LooperExecutor = Executors.UI_HELPER_EXECUTOR

    @Provides
    fun provideIDP(@ApplicationContext context: Context): InvariantDeviceProfile =
        LauncherAppState.getIDP(context)

    @Provides
    fun provideIconCache(@ApplicationContext context: Context): IconCache =
        LauncherAppState.getInstance(context).iconCache

    @Provides
    fun provideUserCache(@ApplicationContext context: Context): UserCache =
        UserCache.INSTANCE.get(context)

    @Provides
    fun provideScreenOnTracker(@ApplicationContext context: Context): ScreenOnTracker =
        ScreenOnTracker.INSTANCE.get(context)
}

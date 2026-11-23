# packages/apps/Launcher3 から取り込んだコード

方針: [docs/launcher3-porting.md](../../docs/launcher3-porting.md)

`scripts/update-upstream.sh` は、次のブロックを読んで更新する。パスは、このディレクトリと上流のリポジトリで同じ。ただし上流の `res/` は、ここでは `res-textlauncher/` に置く（`res_dir`）。`derived=` の行は、上流のファイルから使わない部分を削り、変更に `TextLauncher:` の印を付けて作った互換クラスで、同じく3方向マージで更新する（`PopupContainerWithArrow` は上流から削っただけ。`PopupContainer` は削ったうえで、キーボードの出入りで落とし先の層の高さが変わってもメニューの画面上の位置を保つ。`AppWidgetResizeFrame` は `IntRange` と `showForWidget` の Compose 版への経路だけを残した（`Flags.fixWidgetSinglePtrResize` はリリース設定で有効）。`ResizeManager` は2パネルの隣の `CellLayout` の処理・保留中のウィジェット・記録を削り、ウィジェットのスパン・大きさの通知・モデルの保存を `Launcher` の互換クラス経由でアプリから受ける。`AppWidgetResizeFrameCompose` はアクセシビリティのデリゲートを削り、ホームのスクロールとキーボードの出入りに枠を追従させ、開閉を `Launcher` に知らせる（編集モード）。`ItemClickHandler` はアプリペア・ウィジェット・インストール中のアイコン・プライベートスペースの部分を削った。`IconCache` は Dagger・LoaderTask の一括読み込み・ホームのファイル・インストール中のセッション・アーカイブされたアプリ・インスタントアプリの部分を削った（ウィジェットピッカーの分類の名前とアイコンは残す）。ウィジェットピッカーの `WidgetPickerComposeWrapper(Impl)` はピン留めの要求とピッカーからのドラッグ（`WidgetPickerDragItemListener`）を削り、`WidgetPickerAddItemListener` は `Launcher` が Context でないので `asContext()` を使う。`LauncherComponentProvider` は `LauncherApplication` とセーフモードを、`LauncherAppComponent` は自前の `LauncherAppModule` を使う。`Preconditions` はタスクバーの確認を、`PendingAddWidgetInfo` は追加の手順のハンドラ・既定の大きさ・記録を、`PinShortcutRequestActivityInfo` はホームへの追加と設定画面を、`WidgetManagerHelper` は `WIDGETS_ENABLED`・カスタムウィジェット・復元の部分を削った。全アプリ一覧の `AlphabeticalAppsList`・`BaseAllAppsAdapter`・`AllAppsRecyclerView` は仕事用プロファイル・プライベートスペース・記録・文字の一覧の部分を削った。`WindowManagerProxy` は各向きのウィンドウの大きさの見積もり（`estimateWindowBounds`）だけを残し、Dagger・タスクバー・インセットの補正を削った。内蔵ディスプレイは `DisplayManager` から数え、大画面のカットアウトは向きによらず上に取る。`RotationHelper` は `DisplayController` とデバイスプロファイルの通知の代わりに、Activity から構成の変化を受け取る）。

```upstream
repo=https://android.googlesource.com/platform/packages/apps/Launcher3
tag=android-17.0.0_r1
res_dir=res-textlauncher
path=src/com/android/launcher3/anim/KeyboardInsetAnimationCallback.java
path=src/com/android/launcher3/shapes/ShapesProvider.kt
path=src/com/android/launcher3/shapes/IconShapeModel.kt
path=src/com/android/launcher3/celllayout/ReorderAlgorithm.java
path=src/com/android/launcher3/celllayout/ItemConfiguration.kt
path=src/com/android/launcher3/celllayout/ViewCluster.kt
path=src/com/android/launcher3/celllayout/ReorderParameters.kt
path=src/com/android/launcher3/celllayout/CellLayoutLayoutParams.java
path=src/com/android/launcher3/util/GridOccupancy.java
path=src/com/android/launcher3/util/RotationUtils.java
path=src/com/android/launcher3/util/WindowBounds.java
path=src/com/android/launcher3/util/window/CachedDisplayInfo.java
path=shared/src/com/android/launcher3/testing/shared/ResourceUtils.java
path=src/com/android/launcher3/util/CellAndSpan.java
path=src/com/android/launcher3/folder/ClippedFolderIconLayoutRule.java
path=src/com/android/launcher3/celllayout/ReorderPreviewAnimation.kt
path=src/com/android/launcher3/Reorderable.java
path=src/com/android/launcher3/util/MultiTranslateDelegate.java
path=src/com/android/launcher3/util/MultiPropertyFactory.java
path=src/com/android/launcher3/util/Thunk.java
path=src/com/android/launcher3/anim/SpringAnimationBuilder.java
path=src/com/android/launcher3/anim/AnimationSuccessListener.java
path=src/com/android/launcher3/folder/FolderAnimationSpringBuilderManager.kt
path=src/com/android/launcher3/folder/FolderSpringAnimatorSet.kt
path=src/com/android/launcher3/folder/FolderAnimationData.kt
path=src/com/android/launcher3/folder/ClipRevealData.kt
path=src/com/android/launcher3/folder/IconAnimationData.kt
path=src/com/android/launcher3/folder/FolderOpenCloseAnimationListener.kt
path=src/com/android/launcher3/folder/FolderScrimAnimationListener.kt
path=src/com/android/launcher3/folder/FolderAnimationCreator.kt
path=src/com/android/launcher3/folder/PreviewBackground.java
path=src/com/android/launcher3/folder/FolderGridOrganizer.java
path=src/com/android/launcher3/graphics/ShapeDelegate.kt
path=src/com/android/launcher3/graphics/PathWrapper.kt
path=src/com/android/launcher3/celllayout/DelegatedCellDrawing.kt
path=src/com/android/launcher3/views/ClipPathView.java
path=src/com/android/launcher3/util/MultiScalePropertyFactory.java
path=src/com/android/launcher3/dragndrop/DragController.java
path=src/com/android/launcher3/dragndrop/DragView.java
path=src/com/android/launcher3/dragndrop/DragDriver.java
path=src/com/android/launcher3/dragndrop/DragOptions.java
path=src/com/android/launcher3/dragndrop/DraggableView.java
path=src/com/android/launcher3/DropTarget.java
path=src/com/android/launcher3/DragSource.java
path=src/com/android/launcher3/util/TouchController.java
path=src/com/android/launcher3/util/RunnableList.java
path=src/com/android/launcher3/util/TouchUtil.java
path=src/com/android/launcher3/CheckLongPressHelper.java
path=src/com/android/launcher3/util/ViewEx.kt
path=src/com/android/launcher3/graphics/DragPreviewProvider.java
path=src/com/android/launcher3/Alarm.java
path=src/com/android/launcher3/OnAlarmListener.java
path=src/com/android/launcher3/InterruptibleInOutAnimator.java
path=src/com/android/launcher3/folder/PreviewItemManager.java
path=src/com/android/launcher3/folder/FolderPreviewItemAnim.java
path=src/com/android/launcher3/folder/PreviewItemDrawingParams.java
path=src/com/android/launcher3/AbstractFloatingView.java
path=src/com/android/launcher3/AbstractFloatingViewHelper.kt
path=src/com/android/launcher3/InsettableFrameLayout.java
path=src/com/android/launcher3/Insettable.java
path=src/com/android/launcher3/anim/AnimatedFloat.java
path=src/com/android/launcher3/popup/ArrowPopup.java
path=src/com/android/launcher3/popup/RoundedArrowDrawable.java
path=src/com/android/launcher3/popup/Popup.kt
path=src/com/android/launcher3/popup/IconViewController.kt
path=src/com/android/launcher3/popup/PopupPopulator.java
path=src/com/android/launcher3/shortcuts/ShortcutRequest.java
path=src/com/android/launcher3/shortcuts/DeepShortcutTextView.java
path=src/com/android/launcher3/shortcuts/ShortcutDragPreviewProvider.java
path=src/com/android/launcher3/keyboard/ItemFocusIndicatorHelper.java
path=src/com/android/launcher3/keyboard/FocusIndicatorHelper.java
path=src/com/android/launcher3/keyboard/FocusedItemDecorator.java
path=src/com/android/launcher3/keyboard/ViewGroupFocusHelper.java
path=src/com/android/launcher3/views/BubbleTextHolder.java
path=src/com/android/launcher3/views/FloatingIconViewCompanion.java
path=src/com/android/launcher3/shortcuts/DeepShortcutView.java
path=src/com/android/launcher3/shortcuts/ShortcutKey.java
path=src/com/android/launcher3/util/ItemInfoMatcher.java
path=src/com/android/launcher3/util/IntSet.java
path=src/com/android/launcher3/util/IntArray.java
path=src/com/android/launcher3/icons/CacheableShortcutInfo.kt
path=src/com/android/launcher3/util/CancellableTask.kt
path=src/com/android/launcher3/FastScrollRecyclerView.java
path=src/com/android/launcher3/MotionEventsUtils.java
path=src/com/android/launcher3/touch/BaseSwipeDetector.java
path=src/com/android/launcher3/touch/SingleAxisSwipeDetector.java
path=src/com/android/launcher3/touch/OverScroll.java
path=src/com/android/launcher3/views/RecyclerViewFastScroller.java
path=src/com/android/launcher3/graphics/FastScrollThumbDrawable.java
path=src/com/android/launcher3/allapps/AllAppsFastScrollHelper.java
path=src/com/android/launcher3/allapps/LetterListTextView.java
path=src/com/android/launcher3/allapps/AllAppsGridAdapter.java
path=src/com/android/launcher3/allapps/AppInfoComparator.java
path=src/com/android/launcher3/allapps/search/SearchAdapterProvider.java
path=src/com/android/launcher3/allapps/search/DefaultSearchAdapterProvider.java
path=src/com/android/launcher3/compat/AlphabeticIndexCompat.java
path=src/com/android/launcher3/util/LabelComparator.java
path=src/com/android/launcher3/util/ScrollableLayoutManager.java
derived=src/com/android/launcher3/touch/ItemClickHandler.java compat/launcher3-internal/com/android/launcher3/touch/ItemClickHandler.java
derived=src/com/android/launcher3/popup/PopupContainer.kt compat/launcher3-internal/com/android/launcher3/popup/PopupContainer.kt
derived=src/com/android/launcher3/popup/PopupContainerWithArrow.kt compat/launcher3-internal/com/android/launcher3/popup/PopupContainerWithArrow.kt
derived=src/com/android/launcher3/icons/IconCache.kt compat/launcher3-internal/com/android/launcher3/icons/IconCache.kt
derived=src/com/android/launcher3/allapps/AlphabeticalAppsList.java compat/launcher3-internal/com/android/launcher3/allapps/AlphabeticalAppsList.java
derived=src/com/android/launcher3/allapps/BaseAllAppsAdapter.java compat/launcher3-internal/com/android/launcher3/allapps/BaseAllAppsAdapter.java
derived=src/com/android/launcher3/allapps/AllAppsRecyclerView.java compat/launcher3-internal/com/android/launcher3/allapps/AllAppsRecyclerView.java
derived=src/com/android/launcher3/util/window/WindowManagerProxy.java compat/launcher3-internal/com/android/launcher3/util/window/WindowManagerProxy.java
derived=src/com/android/launcher3/states/RotationHelper.java compat/launcher3-internal/com/android/launcher3/states/RotationHelper.java
derived=src/com/android/launcher3/AppWidgetResizeFrame.kt compat/launcher3-internal/com/android/launcher3/AppWidgetResizeFrame.kt
derived=src/com/android/launcher3/widget/resize/ResizeManager.kt compat/launcher3-internal/com/android/launcher3/widget/resize/ResizeManager.kt
derived=src/com/android/launcher3/widget/resize/AppWidgetResizeFrameCompose.kt compat/launcher3-internal/com/android/launcher3/widget/resize/AppWidgetResizeFrameCompose.kt
derived=src/com/android/launcher3/widgetpicker/WidgetPickerComposeWrapper.kt compat/launcher3-internal/com/android/launcher3/widgetpicker/WidgetPickerComposeWrapper.kt
derived=src/com/android/launcher3/widgetpicker/WidgetPickerComposeWrapperImpl.kt compat/launcher3-internal/com/android/launcher3/widgetpicker/WidgetPickerComposeWrapperImpl.kt
derived=src/com/android/launcher3/widgetpicker/listeners/WidgetPickerAddItemListener.kt compat/launcher3-internal/com/android/launcher3/widgetpicker/listeners/WidgetPickerAddItemListener.kt
derived=src/com/android/launcher3/dagger/LauncherComponentProvider.kt compat/launcher3-internal/com/android/launcher3/dagger/LauncherComponentProvider.kt
derived=src_no_quickstep/com/android/launcher3/dagger/LauncherAppComponent.java compat/launcher3-internal/com/android/launcher3/dagger/LauncherAppComponent.java
derived=src/com/android/launcher3/util/Preconditions.java compat/launcher3-internal/com/android/launcher3/util/Preconditions.java
derived=src/com/android/launcher3/widget/PendingAddWidgetInfo.java compat/launcher3-internal/com/android/launcher3/widget/PendingAddWidgetInfo.java
derived=src/com/android/launcher3/dragndrop/PinShortcutRequestActivityInfo.java compat/launcher3-internal/com/android/launcher3/dragndrop/PinShortcutRequestActivityInfo.java
derived=src/com/android/launcher3/widget/WidgetManagerHelper.java compat/launcher3-internal/com/android/launcher3/widget/WidgetManagerHelper.java
path=src/com/android/launcher3/widget/resize/ResizeFrame.kt
path=src/com/android/launcher3/widget/resize/ResizeFrameViewModel.kt
path=src/com/android/launcher3/util/compose/TestModifiers.kt
path=src/com/android/launcher3/PopupTouchHandler.kt
path=res/layout/app_widget_resize_frame_compose.xml
path=res/drawable/ic_setting.xml
path=modules/widgetpicker/AndroidManifest.xml
path=modules/widgetpicker/res/values-af/strings.xml
path=modules/widgetpicker/res/values-am/strings.xml
path=modules/widgetpicker/res/values-ar/strings.xml
path=modules/widgetpicker/res/values-as/strings.xml
path=modules/widgetpicker/res/values-az/strings.xml
path=modules/widgetpicker/res/values-b+sr+Latn/strings.xml
path=modules/widgetpicker/res/values-be/strings.xml
path=modules/widgetpicker/res/values-bg/strings.xml
path=modules/widgetpicker/res/values-bn/strings.xml
path=modules/widgetpicker/res/values-bs/strings.xml
path=modules/widgetpicker/res/values-ca/strings.xml
path=modules/widgetpicker/res/values-cs/strings.xml
path=modules/widgetpicker/res/values-da/strings.xml
path=modules/widgetpicker/res/values-de/strings.xml
path=modules/widgetpicker/res/values-el/strings.xml
path=modules/widgetpicker/res/values-en-rAU/strings.xml
path=modules/widgetpicker/res/values-en-rCA/strings.xml
path=modules/widgetpicker/res/values-en-rGB/strings.xml
path=modules/widgetpicker/res/values-en-rIN/strings.xml
path=modules/widgetpicker/res/values-es-rUS/strings.xml
path=modules/widgetpicker/res/values-es/strings.xml
path=modules/widgetpicker/res/values-et/strings.xml
path=modules/widgetpicker/res/values-eu/strings.xml
path=modules/widgetpicker/res/values-fa/strings.xml
path=modules/widgetpicker/res/values-fi/strings.xml
path=modules/widgetpicker/res/values-fr-rCA/strings.xml
path=modules/widgetpicker/res/values-fr/strings.xml
path=modules/widgetpicker/res/values-gl/strings.xml
path=modules/widgetpicker/res/values-gu/strings.xml
path=modules/widgetpicker/res/values-hi/strings.xml
path=modules/widgetpicker/res/values-hr/strings.xml
path=modules/widgetpicker/res/values-hu/strings.xml
path=modules/widgetpicker/res/values-hy/strings.xml
path=modules/widgetpicker/res/values-in/strings.xml
path=modules/widgetpicker/res/values-is/strings.xml
path=modules/widgetpicker/res/values-it/strings.xml
path=modules/widgetpicker/res/values-iw/strings.xml
path=modules/widgetpicker/res/values-ja/strings.xml
path=modules/widgetpicker/res/values-ka/strings.xml
path=modules/widgetpicker/res/values-kk/strings.xml
path=modules/widgetpicker/res/values-km/strings.xml
path=modules/widgetpicker/res/values-kn/strings.xml
path=modules/widgetpicker/res/values-ko/strings.xml
path=modules/widgetpicker/res/values-ky/strings.xml
path=modules/widgetpicker/res/values-lo/strings.xml
path=modules/widgetpicker/res/values-lt/strings.xml
path=modules/widgetpicker/res/values-lv/strings.xml
path=modules/widgetpicker/res/values-mk/strings.xml
path=modules/widgetpicker/res/values-ml/strings.xml
path=modules/widgetpicker/res/values-mn/strings.xml
path=modules/widgetpicker/res/values-mr/strings.xml
path=modules/widgetpicker/res/values-ms/strings.xml
path=modules/widgetpicker/res/values-my/strings.xml
path=modules/widgetpicker/res/values-nb/strings.xml
path=modules/widgetpicker/res/values-ne/strings.xml
path=modules/widgetpicker/res/values-nl/strings.xml
path=modules/widgetpicker/res/values-or/strings.xml
path=modules/widgetpicker/res/values-pa/strings.xml
path=modules/widgetpicker/res/values-pl/strings.xml
path=modules/widgetpicker/res/values-pt-rPT/strings.xml
path=modules/widgetpicker/res/values-pt/strings.xml
path=modules/widgetpicker/res/values-ro/strings.xml
path=modules/widgetpicker/res/values-ru/strings.xml
path=modules/widgetpicker/res/values-si/strings.xml
path=modules/widgetpicker/res/values-sk/strings.xml
path=modules/widgetpicker/res/values-sl/strings.xml
path=modules/widgetpicker/res/values-sq/strings.xml
path=modules/widgetpicker/res/values-sr/strings.xml
path=modules/widgetpicker/res/values-sv/strings.xml
path=modules/widgetpicker/res/values-sw/strings.xml
path=modules/widgetpicker/res/values-ta/strings.xml
path=modules/widgetpicker/res/values-te/strings.xml
path=modules/widgetpicker/res/values-th/strings.xml
path=modules/widgetpicker/res/values-tl/strings.xml
path=modules/widgetpicker/res/values-tr/strings.xml
path=modules/widgetpicker/res/values-uk/strings.xml
path=modules/widgetpicker/res/values-ur/strings.xml
path=modules/widgetpicker/res/values-uz/strings.xml
path=modules/widgetpicker/res/values-vi/strings.xml
path=modules/widgetpicker/res/values-w1100dp/dimens.xml
path=modules/widgetpicker/res/values-w700dp/dimens.xml
path=modules/widgetpicker/res/values-zh-rCN/strings.xml
path=modules/widgetpicker/res/values-zh-rHK/strings.xml
path=modules/widgetpicker/res/values-zh-rTW/strings.xml
path=modules/widgetpicker/res/values-zu/strings.xml
path=modules/widgetpicker/res/values/dimens.xml
path=modules/widgetpicker/res/values/strings.xml
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/Annotations.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/WidgetPickerComponent.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/WidgetPickerModule.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/data/repository/WidgetAppIconsRepository.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/data/repository/WidgetUsersRepository.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/data/repository/WidgetsRepository.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/domain/interactor/WidgetAppIconsInteractor.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/domain/interactor/WidgetsInteractor.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/domain/model/SearchResult.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/domain/usecase/FilterWidgetsForHostUseCase.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/domain/usecase/GroupWidgetAppsByProfileUseCase.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/shared/model/PickableWidget.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/shared/model/WidgetApp.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/shared/model/WidgetAppIcon.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/shared/model/WidgetAppId.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/shared/model/WidgetHostInfo.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/shared/model/WidgetId.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/shared/model/WidgetPreview.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/shared/model/WidgetUserProfiles.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/ViewModel.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/WidgetPickerCuiReporter.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/WidgetPickerEventListeners.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/appcatalog/SingleAppWidgetsCatalog.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/appcatalog/SingleAppWidgetsCatalogViewModel.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/components/Animations.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/components/BorderOnFocus.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/components/DragAndDrop.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/components/ExpandCollapseIndicator.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/components/LeadingIconToolbarTab.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/components/ScrollableFloatingToolbar.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/components/SheetDismissModifiers.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/components/SheetDismissState.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/components/SheetHeader.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/components/SheetNestedScrollConnection.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/components/SinglePaneLayout.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/components/Strings.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/components/TestTag.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/components/TwoPaneLayout.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/components/WidgetAppIcon.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/components/WidgetAppsList.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/components/WidgetAppsListHeader.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/components/WidgetDetails.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/components/WidgetPickerHostStateProvider.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/components/WidgetPreview.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/components/WidgetPreviewHostView.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/components/WidgetsGrid.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/components/WidgetsSearchBar.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/components/accessibility/AccessibilityState.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/components/bottomsheet/TitledBottomSheet.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/components/floatingsheet/TitledFloatingSheet.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/fullcatalog/FullWidgetsCatalog.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/fullcatalog/FullWidgetsCatalogViewModel.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/fullcatalog/screens/landing/LandingScreen.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/fullcatalog/screens/landing/LandingScreenSinglePane.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/fullcatalog/screens/landing/LandingScreenTwoPane.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/fullcatalog/screens/landing/LandingScreenViewModel.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/fullcatalog/screens/search/SearchScreen.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/fullcatalog/screens/search/SearchScreenViewModel.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/model/DisplayableWidgetApp.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/model/WidgetSizeGroup.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/pin/PinAppWidgetCatalog.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/pin/PinAppWidgetCatalogViewModel.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/theme/WidgetPickerColors.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/theme/WidgetPickerTextStyles.kt
path=modules/widgetpicker/src/com/android/launcher3/widgetpicker/ui/theme/WidgetPickerTheme.kt
path=res/color/drop_target_text.xml
path=res/color/popup_shade_first.xml
path=res/color/popup_shade_second.xml
path=res/color/popup_shade_third.xml
path=res/drawable/bg_letter_list_text.xml
path=res/drawable/ic_apps.xml
path=res/drawable/ic_palette.xml
path=res/drawable/ic_widget.xml
path=res/drawable-v28/bg_celllayout.xml
path=res/color/cell_layout_bg_color.xml
path=res/drawable/deep_shortcuts_text_placeholder.xml
path=res/drawable/drop_target_background.xml
path=res/drawable/drop_target_frame.xml
path=res/drawable/drop_target_frame_hover.xml
path=res/drawable/gm_edit_24.xml
path=res/drawable/ic_add_circle_filled.xml
path=res/drawable/ic_deepshortcut_placeholder.xml
path=res/drawable/ic_remove_no_shadow.xml
path=res/drawable/ic_uninstall_no_shadow.xml
path=res/drawable/info_24px.xml
path=res/drawable/middle_item_primary.xml
path=res/drawable/popup_background.xml
path=res/drawable/round_rect_folder.xml
path=res/drawable/single_item_primary.xml
path=res/layout/deep_shortcut.xml
path=res/layout/deep_shortcut_container.xml
path=res/layout/system_shortcut.xml
path=res/layout/system_shortcut_content.xml
path=res/layout/system_shortcut_icon_only.xml
path=res/layout/system_shortcut_icon_only_end.xml
path=res/layout/system_shortcut_icon_only_start.xml
path=res/layout/system_shortcut_icons_container.xml
path=res/layout/system_shortcut_rows_container.xml
path=res/layout/system_shortcut_spacer.xml
path=src/com/android/launcher3/widgetpicker/NoOpWidgetPickerModule.kt
path=src/com/android/launcher3/widgetpicker/WidgetPickerConfig.kt
path=src/com/android/launcher3/widgetpicker/LauncherWidgetPickerModule.kt
path=src/com/android/launcher3/widgetpicker/WidgetPickerActivity.kt
path=src/com/android/launcher3/widget/picker/OWNERS
path=src/com/android/launcher3/widgetpicker/datasource/FeaturedWidgetsDataSource.kt
path=src/com/android/launcher3/widgetpicker/datasource/InMemoryWidgetsSearchAlgorithm.kt
path=src/com/android/launcher3/widgetpicker/datasource/WidgetsSearchAlgorithm.kt
path=src/com/android/launcher3/widgetpicker/datasource/ConfigResourceFeaturedWidgetsDataSource.kt
path=src/com/android/launcher3/widgetpicker/repository/WidgetsRepositoryImpl.kt
path=src/com/android/launcher3/widgetpicker/repository/WidgetUsersRepositoryImpl.kt
path=src/com/android/launcher3/widgetpicker/repository/WidgetAppIconsRepositoryImpl.kt
path=src/com/android/launcher3/widgetpicker/theme/LauncherWidgetPickerTypography.kt
path=src/com/android/launcher3/widgetpicker/theme/LauncherWidgetPickerTheme.kt
path=src/com/android/launcher3/widgetpicker/theme/LauncherWidgetPickerColors.kt
path=src/com/android/launcher3/widgetpicker/logging/LauncherWidgetPickerCuiReporter.kt
path=src/com/android/launcher3/widget/picker/util/WidgetPreviewContainerSizes.kt
path=src/com/android/launcher3/widget/picker/util/WidgetPreviewContainerSize.kt
path=dagger/src/com/android/launcher3/dagger/ApplicationContext.java
path=dagger/src/com/android/launcher3/dagger/LauncherAppSingleton.java
path=modules/concurrent/src/com/android/launcher3/concurrent/annotations/BackgroundContext.kt
path=modules/concurrent/src/com/android/launcher3/concurrent/annotations/Background.kt
path=src/com/android/launcher3/util/LifecycleHelper.kt
path=src/com/android/launcher3/util/compose/TextStyleFromResource.kt
path=src/com/android/launcher3/model/WidgetItem.java
path=src/com/android/launcher3/pm/ShortcutConfigActivityInfo.java
path=src/com/android/launcher3/widget/util/WidgetSizes.java
path=src/com/android/launcher3/widget/WidgetSections.java
path=src/com/android/launcher3/PendingAddItemInfo.java
path=src/com/android/launcher3/widget/PendingAddShortcutInfo.java
path=src/com/android/launcher3/AppFilter.java
path=src/com/android/launcher3/model/WidgetsModel.java
path=src/com/android/launcher3/widget/DatabaseWidgetPreviewLoader.java
path=src/com/android/launcher3/util/ActivityLifecycleCallbacksAdapter.java
path=src/com/android/launcher3/util/ContextTracker.java
path=src/com/android/launcher3/util/XmlElement.kt
path=src/com/android/launcher3/widget/LauncherAppWidgetProviderInfo.java
path=res/layout/widget_picker_activity.xml
path=src/com/android/launcher3/model/StringCache.kt
path=res/xml/widget_sections.xml
path=res/drawable/ic_conversations_widget_category.xml
path=res/drawable/ic_note_taking_widget_category.xml
```

## 取り込んでいないもの

| 上流のパス | 理由 |
|---|---|
| `build.gradle.kts`、`AndroidManifest.xml` | 自前のもの（`build.gradle.kts`、`TextLauncherManifest.xml`）を置く |
| `res/` | 丸ごとは取り込まない。そのままコピーした `color/`・`drawable/`・`layout/` のファイルは上のブロックに載せ、`res-textlauncher/` に置く。コピーしたコードが使う値は抜き出して `res-textlauncher/` に置く（`values/strings.xml`、`values-ja/strings.xml` の `*_shape_title`、各言語の `values-*/strings.xml` の `app_info_drop_target_label`、`action_deep_shortcut`、`action_add_to_workspace`、空き領域の長押しメニューとウィジェットの追加の `styles_wallpaper_button_text`・`widget_button_text`・`all_apps_button_label`・`out_of_space`・`msg_disabled_by_admin`、ウィジェットの長押しメニューとリサイズ枠の `remove_system_shortcut_label`・`widget_settings`・`action_increase_width`・`action_decrease_width`・`resize_handle_*`・`tap_to_show_resize_buttons`・`widget_frame_name`、`values/launcher3.xml` と `values-night/launcher3.xml` にまとめたフォルダ、ドラッグ、落とし先の枠、削除・アンインストールのボタン、長押しメニュー、キーボードのフォーカス枠、全アプリ一覧のスクロールバーと開閉のジェスチャー、ウィンドウの大きさの見積もり、ウィジェットのリサイズ枠用の attr・色・寸法・時間・スタイル、`values/widget_picker.xml` にまとめたウィジェットピッカー（`WidgetPickerActivityTheme`、色・寸法・整数・真偽値・`default_featured_widget_apps`・`filtered_components`・`WidgetSections` の styleable）、各言語の `values-*/strings.xml` の `StringCache` の文字列・`activity_not_found`・`widget_category_conversations`・`widget_category_note_taking`・`safemode_widget_error`）。抜き出した値は更新スクリプトの対象外なので、更新時は手で突き合わせる |

## 手を入れたファイル

なし。

## 互換層で受け止めている依存

`compat/launcher3-internal/`（`:launcher3` モジュールに含めてビルドする）にある。`iconloaderlib` も使う `Flags` と `UserType` だけは、別モジュールの `compat/launcher3/` にある（分け方は docs/launcher3-porting.md の「置き場所と記録」）。

| クラス | 使っているファイル |
|---|---|
| `com.android.launcher3.Utilities`（`mapRange`） | `anim/KeyboardInsetAnimationCallback.java` |
| `com.android.launcher3.CellLayout`（画面には置かない。アルゴリズム、並び替えの反映、落とし先の枠、委譲描画、空きセル探し（`findCellForSpan`）のメンバーを `CellLayout.java` から移した。子 View は `ShortcutAndWidgetContainer` の互換クラスを継承したホームのグリッドが持つ） | `celllayout/ReorderAlgorithm.java`、`celllayout/ViewCluster.kt`、`celllayout/ReorderPreviewAnimation.kt`、`folder/PreviewBackground.java` |
| `com.android.launcher3.model.data.ItemInfo`、`ItemInfoWithIcon`、`WorkspaceItemInfo`、`FolderInfo`、`AppPairInfo` | `util/GridOccupancy.java`、ドラッグ、`folder/PreviewItemManager.java`。`ItemInfoWithIcon.newIcon` は元の処理を写した。上流の BgDataModel と同じく、ホームのモデルはこれらの ItemInfo そのもので（アプリ側の `data/HomeItemInfo.kt` と `HomeModel`）、View の tag にも ItemInfo が入る。無効の状態（`FLAG_DISABLED_*`、`isDisabled`、`isDisabledVersionLower`、`disabledMessage`）とショートカットからの更新（`updateFromDeepShortcutInfo`）、コピーコンストラクタと `clone` は上流から移した。アプリのショートカットは上流と同じく `WorkspaceItemInfo` のまま持ち、`ShortcutKey.fromItemInfo` で見分ける |
| `com.android.launcher3.LauncherAnimUtils`（`VIEW_TRANSLATE_X`、`VIEW_TRANSLATE_Y`） | `util/MultiTranslateDelegate.java` |
| `com.android.launcher3.Workspace`（`REORDER_TIMEOUT`） | `celllayout/ReorderPreviewAnimation.kt` |
| `com.android.launcher3.util.window.RefreshRateTracker`（`getSingleFrameMs`。ディスプレイのリフレッシュレートから計算） | `anim/SpringAnimationBuilder.java` |
| `com.android.launcher3.folder.Folder`、`FolderIcon`、`FolderPagedView`、`LauncherDelegate` | フォルダのアニメーション、プレビュー、ドロップ（`folder/` 以下）、キーボードのフォーカス枠（`keyboard/`）。`Folder` は上流と同じく `AbstractFloatingView`（`TYPE_FOLDER`）で、フォルダのアイコンごとに1つ持ち、開いている間だけドラッグレイヤーに置く。開閉（`animateOpen`、`handleClose`、`animateClosed`、`closeComplete`）、外側のタッチで閉じる処理、ドラッグ元（`startDrag`、`onDragStart`、`onDropCompleted`、`notifyDrop`）、中身の出し入れ（`addFolderContent`、`removeFolderContent`）を `Folder.java` から、アイテムの View の作成と並べ方（`bindItems`、`createNewView`、`arrangeChildren`）とフォーカス枠を `FolderPagedView.java` から移した。名前、ページ、開いたフォルダの上での並べ替え（ドラッグが始まるとフォルダは閉じる）、データベースへの書き込みは持たない。アイテムの View は `Folder.inflateIcon` でアプリが作る。`LauncherDelegate` の `beginDragShared` と `replaceFolderWithFinalItem` はアプリが上書きする。アプリ側の `HomeFolder` と `FolderIconView` がこれらを継承し、フォーカスをタッチでは取らないことは `HomeFolder` が決める。`FolderIcon` のプレビュー描画、`onDrop`、`performCreateAnimation` などは `FolderIcon.java` から移した |
| `com.android.launcher3.BubbleTextView`、`apppairs.AppPairIcon` | フォルダのアニメーション。アプリ側の `AppIconView` が `BubbleTextView` を継承する。アイコンの描画（`setIcon`、`setIconVisible`）、縦の中央寄せ（`onMeasure`、`setCenterVertically`）、並び替えの揺れ（`Reorderable`）は `BubbleTextView.java` から、`FolderIcon` の中央寄せと `Reorderable` は `FolderIcon.java` から移した。ラベルを出さないことはアプリ側で決める（`getCellSpecMaxTextLineCount` を 0 に、`WorkspaceProfile.getIconCenterVertically` を true に）。ラベルがないので文字の透明度は何もしない |
| `com.android.launcher3.Launcher`、`Workspace`、`Hotseat`、`views.ScrimView` | フォルダの背景の暗転と縮小、`Launcher.getLauncher` と `instanceof Launcher`（`folder/`、`shortcuts/DeepShortcutView.java`）。上流と同じく `Launcher` が `ActivityContext` で、アプリ側の `HomeActivityContext` が継承する。`Activity` ではなく、`startActivitySafely`（アプリまたはアプリのショートカットを起動する）、`getDragLayer`、`getWorkspace`、`getHotseat`、`getScrimView`、`getAccessibilityDelegate`、`getStatsLogManager`、`getModel` だけを持つ。`Workspace` はホームのグリッドを、`Hotseat` は検索フィールドを包み、`isFinishedSwitchingState` と `persistRemoveItemsByMatcher`（更新が必要なショートカットのダイアログの「削除」）を持つ |
| `com.android.launcher3.LauncherModel`、`accessibility.LauncherAccessibilityDelegate`、`logging.StatsLogManager` | `shortcuts/DeepShortcutView.java`、`touch/ItemClickHandler.java`。`LauncherModel` は `updateAndBindWorkspaceItem` だけ、`LauncherAccessibilityDelegate` は `addToWorkspace` だけのインターフェースで、アプリ側が実装する。`StatsLogManager` は何も記録しない |
| `com.android.launcher3.views.BaseDragLayer`、`views.ActivityContext`、`DeviceProfile` | フォルダの配置と寸法、ドラッグ、長押しメニュー。`BaseDragLayer` は元と同じく `InsettableFrameLayout` を継承し、タッチコントローラーの振り分け（開いている `AbstractFloatingView` を先に、次にドラッグ）を `BaseDragLayer.java` から移した。ホームの上に重ねた層なので、タッチは親から渡してもらう。ホームの上に重ねた層なので、層の外の View は親を通して座標を変換する。アプリは `BaseDragLayer` を継承した `dragndrop.DragLayer` の互換クラスを置き、こちらに `DragLayer.java` のドロップアニメーション（`animateViewIntoPosition`、`animateView`、`playDropAnimation`、`clearAnimatedView`）を移した。`Launcher` の互換クラスは上流と同じく `getDragLayer()` で `DragLayer` を返す。`DeviceProfile` は、フォルダの寸法（`FolderProfile`）、ホームのアイコンサイズ（`WorkspaceProfile`）、使える高さ（`DeviceProperties`）だけ |
| `com.android.launcher3.graphics.ThemeManager` | アイコンのテーマの有無と形状（アプリのアイコン読み込みが設定する）、フォルダの形。フォルダの形は選んだアイコン形状の `folderRadiusRatio` から作る（`ThemeManager.parseIconState` と同じ） |
| `com.android.launcher3.ShortcutAndWidgetContainer`（`ViewGroup`。アイコンとフォルダの分だけ、セルの寸法、`getChildAt(cellX, cellY)`、`setupLp`、`measureChild`、`layoutChild`、フォーカスした子を画面に入れる処理などを `ShortcutAndWidgetContainer.java` から移した。ホームのグリッドが継承する） | `CellLayout`、`dragndrop/DragLayer`、フォルダのアニメーション（`folder/IconAnimationData.kt`） |
| `util.Themes`、`model.data.FolderInfo` | フォルダのアニメーションと `FolderGridOrganizer` |
| `com.android.launcher3.icons.IconCache`（`derived=`。`BaseIconCache` を継承し、アイコンを SQLite の `app_icons.db` とメモリに持つ）、`LauncherAppState`（`setIconCache` でアプリが作ったものを渡す） | `folder/PreviewItemManager.java`、`popup/PopupPopulator.java`、アプリのアイコン読み込み（`AppIconLoader`。アプリのアイコンは `getTitleAndIcon`、ショートカットは `getShortcutIcon`、起動後の最初の表示のあとに LoaderTask と同じく `IconCacheUpdateHandler` で DB を更新、パッケージの変化で `updateIconsForPkg` / `removeIconsForPkg`、テーマの切り替えで `updateIconParams`） |
| `com.android.launcher3.icons.LauncherIcons`（`BaseIconFactory` のプール。アイコンの密度・大きさ・テーマは InvariantDeviceProfile と ThemeManager の代わりにアプリが渡す） | `IconCache` |
| `util.PackageUserKey`、`model.data.AppInfo`、`model.data.PackageItemInfo`（`IconCache` が引くのに使うメンバーだけ）、`Utilities`（`trim`、`postAsyncCallback`）、`util.Themes`（`getColorAccent`）、`util.ApplicationInfoWrapper`（`ApplicationInfo` から作るコンストラクタ） | `icons/CacheableShortcutInfo.kt`、`IconCache` |
| `com.android.launcher3.allapps.AllAppsStore`（アプリの配列と更新の通知だけ。アプリが `setApps` で渡す）、`model.data.AppInfo`（`sectionName`、`LauncherActivityInfo` からのコンストラクタ（停止中の状態だけ）、`COMPONENT_KEY_COMPARATOR`）、`pm.UserCache`（`getSerialNumberForUser` を `UserManager` に聞く）、`testing.shared.TestProtocol`（`SCROLL_FINISHED_MESSAGE` だけ。`AccessibilityManagerCompat.sendTestProtocolEventToTest` は何もしない）、`DeviceProfile.AllAppsProfile`（列数とセルの高さ）、`ActivityContext`（`getAllAppsItemLongClickListener`、`startActivitySafely` の既定）、`BubbleTextView`（`DISPLAY_ALL_APPS`、`reset`、`applyFromApplicationInfo`、`verifyHighRes`、`reapplyItemInfo` を上流から移した）、`model.data.ItemInfo`（`appTitle`） | 全アプリ一覧（`allapps/`、`FastScrollRecyclerView.java`、`views/RecyclerViewFastScroller.java`） |
| `com.android.launcher3.util.WallpaperColorHints`（Dagger の代わりにアプリで1つ。登録はすぐメインスレッドで）、`util.WallpaperThemeManager`（ライフサイクルのコールバックは自前で書き、作り直しのフラグは保存しない）、`util.Themes`（`getActivityThemeRes`、`getAttrBoolean`）。`AppTheme` の各スタイルは `res-textlauncher` で名前だけ宣言し、中身はアプリの `res/values/themes.xml` が上書きする | 壁紙の色のヒントでテーマを選び、壁紙の上の文字（検索結果）の色と影、システムバーのアイコンの明暗を決める（`LauncherActivity`） |
| `com.android.launcher3.Utilities`（`dpiFromPx`）、`R.dimen.taskbar_size`（上流の既定の 0dp）、`max_width_and_height_of_small_display_cutout` | `util/window/WindowManagerProxy.java`（`derived=`。アプリのグリッドの大きさ `GridSpec` が、`InvariantDeviceProfile` と同じく各向きの見積もりの一番小さい大きさを使う） |
| `com.android.launcher3.util.Executors`（`MODEL_EXECUTOR`・`UI_HELPER_EXECUTOR`・`MAIN_EXECUTOR`・`ORDERED_BG_EXECUTOR`） | `IconCache` のスレッド、長押しメニューのショートカットの問い合わせ（`PopupContainerWithArrow`）、`dragndrop/DragView.java`、ウィジェットピッカー |
| `com.android.launcher3.Flags` | `iconloaderlib`（`third_party/systemui/`）、`shapes/ShapesProvider.kt`、ドラッグ、フォルダ、`keyboard/`（`enableFocusOutline` は bp1a で有効）、全アプリ一覧（`letterFastScroller` はどのリリース設定にもなく、既定の無効） |
| `com.android.launcher3.PagedView`（`isPageInTransition`、`getScrollForPage` だけを持つ抽象クラス。TextLauncher にページ送りの View はないので、継承するものはない） | `keyboard/ViewGroupFocusHelper.java` |
| `com.android.launcher3.LauncherSettings`、`BuildConfig`、`dagger/`、`logging/`、`accessibility.DragViewStateAnnouncer`、`graphics.PreloadIconDelegate`、`graphics.AutomatedIconDelegate`、`apppairs/`、`widget.LauncherAppWidgetHostView` | ドラッグと `folder/PreviewItemManager.java`。TextLauncher で起きない場合（システムのドラッグ、ウィジェット、App Pair、インストール中のアプリなど）は何もしないか、通らない |
| `com.android.launcher3.popup.PopupContainer`、`PopupContainerWithArrow`、`popup.PopupItemDragHandler`、`SystemShortcut`、`popup.PopupDataProvider`、`util.ApplicationInfoWrapper`、`util.ShortcutUtil`、`logging.FileLog`、`anim.PendingAnimation`、`compat.AccessibilityManagerCompat`、`com.android.wm.shell.Flags` | 長押しメニュー（`popup/ArrowPopup.java`、`AbstractFloatingView.java`、`popup/PopupPopulator.java`）。システムショートカットとアプリのショートカットの行を出す経路のメソッドを元のファイルから移した。ショートカット数は Launcher3 のモデルが裏で持つ代わりに、メニューを開くときに同じ条件で問い合わせる。Compose 版のメニュー（`Flags.expandableLongPressMenu` はリリース設定で無効）は持たない。`PopupItemDragHandler` はインターフェースだけを置き、`LauncherPopupItemDragHandler` はアプリ側（`ItemLongClickListener`）に移した。`shortcuts/DeepShortcutView.java` はそのままコピーし、追加ボタンとバッジ付きのコピーは `Launcher` の互換クラス（`getAccessibilityDelegate().addToWorkspace`、`getModel().updateAndBindWorkspaceItem`）を通してアプリが受ける。`BubbleTextView` は元と同じく `TextView` を継承し、`IconViewController` を実装する |
| `com.android.launcher3.util.MSDLPlayerWrapper`（Dagger の代わりに `INSTANCE.get(context)` でアプリに1つのプレイヤーを返す。履歴の dump は持たない） | ドラッグのハプティック（`CellLayout`、`HomeWorkspace`、`DropTargetBar`。`msdllib` は `third_party/systemui/msdllib/`） |
| `com.android.users.UserType` | `iconloaderlib`（`third_party/systemui/`） |
| `com.android.launcher3.dagger.LauncherAppComponent`（`derived=`）、`LauncherBaseAppComponent`（ウィジェットピッカーが使う `getScreenOnTracker`・`getIDP`・`getWidgetPickerComposeWrapper` だけ。フォルダ名の候補は持たない）、`LauncherAppModule`（`LauncherWidgetPickerModule` と、移植していないモジュールの代わりの `TextLauncherAppModule`。`@Background` は `Executors.ORDERED_BG_EXECUTOR`、グリッドとアイコンのキャッシュは `LauncherAppState` から）、`LauncherComponentProvider`（`derived=`） | ウィジェットピッカー（`widgetpicker/`）。Dagger は KSP で生成する |
| `com.android.launcher3.BaseActivity`、`dragndrop.SimpleDragLayer`、`util.SystemUiController`、`util.ScreenOnTracker`、`util.WindowBlurState`、`util.ApiWrapper`（`createFadeOutAnimOptions`）、`InvariantDeviceProfile`（グリッドの列・行とホームのデバイスプロファイル1つ。ホームが設定する）、`DeviceProfile`（`inv`、セルの大きさ、ウィジェットの余白） | `widgetpicker/WidgetPickerActivity.kt` |
| `com.android.launcher3.Launcher`（`ACTIVITY_TRACKER`。`Launcher` は Activity でないので `ContextTracker.ActivityTracker` の代わりに `isStarted` を見る `ContextTracker`）、`widget.PendingAddWidgetInfo`（`derived=`）、`model.data.LauncherAppWidgetInfo`（中身はなく、ホームのウィジェットのアイテム `WidgetItemInfo` が継承する） | ピッカーの「追加」（`WidgetPickerAddItemListener`）。ホームは `LauncherAccessibilityDelegate.addToWorkspace` で受け、`Launcher.addAppWidgetFromDrop` と同じく bind・設定画面・配置をする |
| `com.android.launcher3.pm.UserCache`（プロファイル、種類（`LauncherApps.getLauncherUserInfo`）、休止中か。ユーザーの変化は通知しない）、`util.LooperExecutor`（Guava の代わりに `AbstractExecutorService`。優先度の引き上げはない）、`util.Preconditions`（`derived=`）、`config.FeatureFlags`（`IS_STUDIO_BUILD` は false）、`widget.WidgetManagerHelper`（`derived=`）、`dragndrop.PinShortcutRequestActivityInfo`（`derived=`）、`icons.IconCache`（`getTitleNoCache`）、`icons.LauncherIcons`（`obtain(Context)` はアイコンのキャッシュのプールから）、`Utilities`（`ATLEAST_T`・`ATLEAST_U`・`isBinderSizeError`・`allowBGLaunch`）、`model.data.ItemInfo`（`dumpProperties`） | `widgetpicker/repository/`、`model/WidgetsModel.java`、`widget/DatabaseWidgetPreviewLoader.java`、`model/WidgetItem.java`、`pm/ShortcutConfigActivityInfo.java` |
| `com.android.launcher3.widget.LauncherAppWidgetHostView`（`AppWidgetHostView` を継承し、長押しの判定、既定の余白を付けないこと（`NavigableAppWidgetHostView.setAppWidget`）、ドラッグの種類・範囲だけ。`getScaleToFit` は常に 1）、`ShortcutAndWidgetContainer.measureChild` のウィジェットの余白（`WorkspaceProfile.widgetPadding`）、`model.data.ItemInfo`（`minSpanX`、`minSpanY`）、`Utilities`（`pointInView`）、`com.android.window.flags.Flags`（`showHomeBehindDesktop`） | `graphics/DragPreviewProvider.java`、`CheckLongPressHelper.java`、アプリのウィジェット |

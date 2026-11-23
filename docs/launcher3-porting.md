# Launcher3 からの取り込み方針

## 方針

Android ユーザーにとって、ランチャーの基本の手触りは AOSP の Launcher3 の動きである。そのため、**手触りに関わる部分は、できる限り Launcher3 の実装をコピーして持ってくる**。

- Launcher3 をフォークして改造することはしない。このランチャーは、ホームの構造（縦スクロール、常時キーボード、キーボードの下の全アプリ一覧）が Launcher3 の根幹と違うためである
- 必要な部分だけをソースごとコピーし、Launcher3 の他の部分への依存は、こちら側に用意した小さな代わりのクラスで受け止める
- Launcher3 の更新に追従できるよう、取り込んだコードは元の形をできるだけ保つ

---

## 基準にするバージョン

| リポジトリ | 基準 |
|---|---|
| `platform/packages/apps/Launcher3` | タグ `android-17.0.0_r1` |
| `platform/frameworks/libs/systemui`（`iconloaderlib` など） | タグ `android-17.0.0_r1` |

- main ブランチは使わない。main には未公開の機能がフラグの裏に入っており、リリース版の挙動と一致しないためである
- 新しいリリースのタグが出たら、[更新の手順](#更新の手順) で追従する

---

## 取り込む対象

### Launcher3 から持ってくるもの

パスは Launcher3 の `src/com/android/launcher3/` からの相対パス。範囲は、`android-17.0.0_r1` のソースで依存関係を辿って決めた（Tex-wgyx）。

| 部品 | 取り込み方 | コピーするファイル | 自前で書くもの / 互換層 |
|---|---|---|---|
| アイコン処理 | ライブラリごとコピー | `frameworks/libs/systemui/iconloaderlib/` | — |
| アイコン形状の定義 | コピー | `shapes/ShapesProvider.kt`、`shapes/IconShapeModel.kt` | — |
| アイコンのキャッシュ | 削ってコピー | `icons/CacheableShortcutInfo.kt`、`util/CancellableTask.kt` | `icons/IconCache.kt` から、Dagger・LoaderTask の一括読み込み・ホームのファイル・インストール中のセッション・アーカイブされたアプリ・インスタントアプリの部分を削って互換クラスにする（`derived=`）。キャッシュの本体（`BaseIconCache`、`IconCacheUpdateHandler` など）は iconloaderlib のまま。スレッドは上流と同じく `MODEL_EXECUTOR`。`LauncherIcons` は互換クラスにし、密度・大きさ・テーマはアプリが渡す。アプリのアイコン読み込み（`AppIconLoader`）が、検索結果は `DEFAULT_LOOKUP_FLAG`、ホームは `DESKTOP_ICON_FLAG` で引き、起動後の最初の表示のあとに `LoaderTask.loadAllSurfacesOrdered` と同じく DB を更新し、パッケージの変化で `PackageUpdatedTask`、テーマの切り替えで `ModelInitializer.refreshAndReloadLauncher` と同じことをする。ホームの DB は上流の `favorites` と同じくアプリのショートカットのアイコンを持ち（`ContentWriter.putIcon`、既定のアイコンは持たない）、問い合わせられないときはそれを出す（`IconRequestInfo.parseIconBlob`） |
| 並び替え（押しのけ、空き探し） | アルゴリズムと反映をコピー | `celllayout/ReorderAlgorithm.java`、`celllayout/ItemConfiguration.kt`、`celllayout/ViewCluster.kt`、`celllayout/ReorderParameters.kt`、`celllayout/CellLayoutLayoutParams.java`、`celllayout/ReorderPreviewAnimation.kt`、`util/GridOccupancy.java`、`util/CellAndSpan.java`、`InterruptibleInOutAnimator.java` | `CellLayout` は画面に置かない互換クラスにし、アルゴリズム、結果の反映（`performReorder`、`animateItemsToSolution`、`revertTempState` など）、落とし先の枠（`visualizeDropLocation`）を `CellLayout.java` から移す。子 View の並べ方（`ShortcutAndWidgetContainer`）はホームのグリッドが受け持つ |
| ドラッグ | コピー | `dragndrop/DragController.java`、`dragndrop/DragView.java`、`dragndrop/DragDriver.java`、`dragndrop/DragOptions.java`、`dragndrop/DraggableView.java`、`DropTarget.java`、`DragSource.java`、`graphics/DragPreviewProvider.java`、`util/TouchController.java`、`Alarm.java` など | `LauncherDragController` はアプリ側の `HomeDragController` に、`Workspace` のドラッグ部分（`onDragOver`、`onDrop`、フォルダ作成など）は `HomeWorkspace` に、`ButtonDropTarget`・`DeleteDropTarget`・`SecondaryDropTarget`・`DropTargetBar` は `DropTargetBar` にに、同じ名前とロジックのまま書き写す。`Folder` のドラッグ元と落とし先の部分は `Folder` の互換クラスに移す。`DragLayer` のドロップアニメーションは `DragLayer` の互換クラスに移す |
| 長押しメニューとドラッグへの移行 | コピー | `popup/ArrowPopup.java`、`popup/RoundedArrowDrawable.java`、`popup/Popup.kt`、`popup/IconViewController.kt`、`popup/Poppable.kt`、`AbstractFloatingView.java`、`InsettableFrameLayout.java`、`anim/AnimatedFloat.java`、`popup/PopupPopulator.java`、`shortcuts/ShortcutRequest.java`、`shortcuts/DeepShortcutTextView.java`、`shortcuts/ShortcutDragPreviewProvider.java`、メニューのレイアウトとドロワブル | `PopupContainer`、`PopupContainerWithArrow`、`SystemShortcut`（`AppInfo` を含む）は互換クラスにし、システムショートカットとアプリのショートカットの行を出す経路のメソッドを移す（Compose 版は除く）。`shortcuts/DeepShortcutView.java` はそのままコピーし、追加ボタンとバッジ付きのコピーは `Launcher` の互換クラスを通してアプリが受ける。ショートカットをメニューからドラッグする `LauncherPopupItemDragHandler` はアプリ側に移し、`Workspace.beginDragShared` の移植に渡す。追加ボタンは `LauncherAccessibilityDelegate.addToWorkspace` に合わせて最初の空きセルに置き、`Launcher.bindInflatedItems` と同じバウンスで出す。ホームのショートカットのシステムへの固定は `BgDataModel.updateShortcutPinnedState` を移す。アプリのショートカットのアイコンは `IconCache` の `getShortcutIcon` で作る（バッジも付く）。「検索文字列を編集」はアプリ側で `SystemShortcut` を継承して書く。フォルダとウィジェットの長押しは、`Flags.homeScreenEditImprovements`（リリース設定で有効）に合わせて `PopupControllerForExtraHomeScreenItems` のメニュー（`FolderSystemShortcuts`・`AppWidgetSystemShortcuts` の項目）を `ItemLongClickListener` に移して出し、`FolderIcon` と `LauncherAppWidgetHostView` の互換クラスは上流と同じく `Poppable` を、`FolderIcon` は `IconViewController` も実装する。ドラッグへの移行は `PopupContainer.createPreDragCondition` をそのまま使う |
| ウィジェットのリサイズ枠 | コピー、削ってコピー | `widget/resize/ResizeFrame.kt`、`widget/resize/ResizeFrameViewModel.kt`、`util/compose/TestModifiers.kt`、`PopupTouchHandler.kt` | android-17 のリリース設定では `Flags.fixWidgetSinglePtrResize` が有効で、Launcher3 は Compose 版の枠（`AppWidgetResizeFrameCompose`）を出す。そのため Jetpack Compose を launcher3 モジュールに入れ、Compose 版を移す（View 版の `AppWidgetResizeFrame` は使わない。Tex-yw4e で決定）。`AppWidgetResizeFrame`（`IntRange` と `showForWidget`）、`ResizeManager`、`AppWidgetResizeFrameCompose` は互換クラスにし（`derived=`）、ウィジェットのスパン（`LauncherAppWidgetProviderInfo.initSpans`）、大きさの通知（`WidgetSizeHandler.updateSizeRanges`）、モデルの保存（`CellLayout.commitTempPlacement` の DB 書き込み）は `Launcher` の互換クラスを通してアプリから受ける。`CellLayout` の互換クラスには `hasAreaForResize`・`createAreaForResize` を移す。枠を出す場面は上流と同じく、長押し（`Flags.homeScreenEditImprovements` が有効なので、`PopupControllerForExtraHomeScreenItems` のメニューと同時）、ドロップの後（`Workspace.getWidgetResizeFrameRunnable`）、追加の直後（`Launcher.showWidgetResizeFrame`）。ホームが縦にスクロールし、キーボードで動くので、枠はウィジェットに追従させる |
| フォルダ | コピー | `folder/` の開閉アニメーション一式（`FolderAnimationSpringBuilderManager.kt`、`FolderSpringAnimatorSet.kt`、`FolderAnimationData.kt`、`ClipRevealData.kt`、`IconAnimationData.kt` など）、`folder/PreviewItemManager.java`、`folder/FolderPreviewItemAnim.java`、`folder/PreviewBackground.java`、`folder/ClippedFolderIconLayoutRule.java`、`folder/FolderGridOrganizer.java`、`graphics/ShapeDelegate.kt` | `Folder`、`FolderIcon`、`FolderPagedView`、`BubbleTextView`、`Launcher` などは互換クラスにし、アプリ側の View がそれを継承する。`Folder` は上流と同じく `AbstractFloatingView` で、開閉、外側のタッチ、ドラッグ元、中身の出し入れを `Folder.java` から移す。開いたフォルダは上流と同じく落とし先（`DropTarget`）で、ドラッグ中も開いたまま、中での並べ替え（`onDragOver` → `FolderPagedView.realTimeReorder`）、外に出たら閉じる（`onDragExit` → `completeDragExit`）、失敗したドラッグの戻し（`onDropCompleted`）、外からのドロップ（`beginExternalDrag`、`onDrop`）も上流のまま。`FolderPagedView` はページを1つにして削ってコピーし（`derived=`）、ページは上流と同じく `CellLayout` が子の `ShortcutAndWidgetContainer` にアイコンを持つ。`FolderIcon` のプレビュー描画、ドロップ、作成アニメーション、上に重ねたら開く処理（`onDragEnter` の `mOpenAlarm`）のメソッドは `FolderIcon.java` から互換クラスに移す。データベースへの書き込みの代わりに、中身の変化をアイコンがアプリに伝え、アプリがモデルを保存する |
| 全アプリ一覧と見出しに飛ぶスクロールバー | コピー、削ってコピー | `views/RecyclerViewFastScroller.java`、`graphics/FastScrollThumbDrawable.java`、`FastScrollRecyclerView.java`、`allapps/AllAppsFastScrollHelper.java`、`allapps/LetterListTextView.java`、`allapps/AllAppsGridAdapter.java`、`allapps/AppInfoComparator.java`、`allapps/search/SearchAdapterProvider.java`、`allapps/search/DefaultSearchAdapterProvider.java`、`compat/AlphabeticIndexCompat.java`、`util/LabelComparator.java`、`util/ScrollableLayoutManager.java` | `allapps/AlphabeticalAppsList.java`、`allapps/BaseAllAppsAdapter.java`、`allapps/AllAppsRecyclerView.java` から、仕事用プロファイル・プライベートスペース・記録・文字の一覧（`Flags.letterFastScroller` はリリース設定で無効）の部分を削って互換クラスにする（`derived=`）。`AllAppsStore` は互換クラス。`ActivityAllAppsContainerView` はアプリ側（`AllAppsView`）に、`AdapterHolder.setup` とスクロールバーへのタッチの振り分けを移す。アプリの一覧は `AllAppsList` と同じく `AppInfo` で持ち、見出しは `AlphabeticIndexCompat` で付ける。アイコンは `BubbleTextView.verifyHighRes`（`IconCache.updateIconInBackground`）も上流どおり。セルはアプリが作る（`all_apps_icon.xml` の代わり。検索結果と同じ見た目） |
| 縦スワイプの判定、端での引っ張り | コピー | `touch/BaseSwipeDetector.java`、`touch/SingleAxisSwipeDetector.java`、`touch/OverScroll.java`、`MotionEventsUtils.java` | 全アプリ一覧の開閉（`AllAppsSwipeController`）が使う。引っ掛かりは `OverScroll.dampedScroll` で付ける。`util/TranslateEdgeEffect.java`、`util/EdgeEffectCompat.java` は使う場面がないので取り込んでいない |
| ハプティック | ライブラリごとコピー | `frameworks/libs/systemui/msdllib/` | `util/MSDLPlayerWrapper.java` の代わりを互換層に置く。鳴らす場所と種類は Launcher3 に合わせる（下記） |
| キーボードの出入りへの追従 | コピー | `anim/KeyboardInsetAnimationCallback.java` | — |
| キーボードのフォーカス枠 | コピー | `keyboard/ItemFocusIndicatorHelper.java`、`keyboard/FocusIndicatorHelper.java`、`keyboard/FocusedItemDecorator.java`、`keyboard/ViewGroupFocusHelper.java` | 検索結果への付け方は `ActivityAllAppsContainerView.AdapterHolder.setup` と `BaseAllAppsAdapter.setIconFocusListener` に、ホームは `DragLayer` と `ItemInflater` に（枠はスクロールするグリッドに描く）、フォルダは `FolderPagedView` と `Folder` のフォーカス処理（開いたらフォルダにフォーカス、中だけを探す、閉じたらアイコンに戻す）に、文字を打ったら検索フィールドに戻す処理は `AppsSearchContainerLayout.preDispatchKeyEvent` に倣う。入力が変わったら1位に枠を付ける処理は Pixel Launcher に合わせた自前のもの（docs/spec.md 2.6）。`PagedView` は、先頭のページだけを表示してスクロールしない互換クラス（フォルダの中身が使う） |
| タップ | 削ってコピー | — | `touch/ItemClickHandler.java` から、アプリペア・ウィジェット・インストール中のアイコン・プライベートスペースの部分を削って互換クラスにする（`derived=`）。無効な項目（作成元が無効にしたショートカット、停止中のアプリなど）のタップも上流と同じ。起動そのものは `Launcher.startActivitySafely` をアプリが実装する。検索結果のタップは、学習の記録のためアプリ側で受ける |
| ウィジェットピッカー | コピー、削ってコピー | `modules/widgetpicker/`（別モジュール `:widgetpicker`）、`widgetpicker/`（`WidgetPickerActivity`、リポジトリ、データソース、テーマ、記録）、`model/WidgetsModel.java`・`WidgetItem.java`、`widget/DatabaseWidgetPreviewLoader.java`・`WidgetSections.java`、`util/ContextTracker.java` ほか | android-17 のピッカーは Compose 版だけなので、それを移す（Tex-r705。OS 標準の選択画面を使う Tex-h3ld の決定は取り消し）。上流と同じく Dagger（KSP で生成）でつなぐ。ルートの `LauncherAppComponent` には `LauncherWidgetPickerModule` と、移植していないモジュールの代わりの `TextLauncherAppModule` を入れる。開き方（`ACTION_PICK` を自分のパッケージに）、「追加」の流れ（`Launcher.ACTIVITY_TRACKER` に `WidgetPickerAddItemListener` を登録してホームを前面にし、`LauncherAccessibilityDelegate.addToWorkspace` → `Launcher.addAppWidgetFromDrop`）は上流と同じ。TextLauncher はウィジェットを作る許可（`BIND_APPWIDGET`）を持たないので OS の確認画面を出すことがあり、それが消えないよう追加はホームが resume してから始める。ピッカーからのドラッグ（`WidgetPickerDragItemListener`）、ショートカットの作成、ピン留めの要求はまだ移していない |
| 通知ドット | コピー、削ってコピー | `notification/NotificationListener.java`・`NotificationRepository.kt`・`NotificationKeyData.java`・`NotificationGroup.java`、`dot/DotInfo.java`・`FolderDotInfo.java`、`util/PackageUserKey.java`、`util/DaggerSingletonObject.java` | 通知の受け取り（`NotificationListener`）とアプリごとの通知（`NotificationRepository`）は上流のまま、Dagger のルートの部品にする（`SettingsCache` は削ってコピー）。アイコンへの反映は `PopupDataProvider` の `updateNotificationDots`（ホームは `ActivityContext.getContent`、開いたフォルダ、全アプリ一覧と検索結果は `AllAppsStore` に登録した RecyclerView）、描画は `BubbleTextView` と `FolderIcon` の上流のコード（`DotRenderer` は iconloaderlib）。OS の通知ドットの設定がオフになると listener が外れ、オンに戻すと `ModelInitializer` と同じく `requestRebind` する（アプリの `Application` で）。通知へのアクセスを求める入口は、Launcher3 の設定画面（`NotificationDotsPreference`）の代わりに空いている場所の長押しメニューに置き、ダイアログは `NotificationAccessConfirmation` と同じ。許可の確認は `ENABLED_NOTIFICATION_LISTENERS` を読む代わりに `NotificationManager.isNotificationListenerAccessGranted`。長押しメニューの通知の行は入れない |
| ウィジェットのホストと追加 | 削ってコピー、書き写す | `CheckLongPressHelper.java` | `widget/LauncherAppWidgetHostView.java` は互換クラスにし、長押しの判定（`CheckLongPressHelper`）、`AppWidgetHostView` の既定の余白を付けないこと、ドラッグの種類・範囲（どちらも `NavigableAppWidgetHostView`）だけを持つ。追加したウィジェットは Launcher3 と同じく大きさ（`PendingAddWidgetInfo.bindOptions`）を付けてバインドする。ホスト（`LauncherWidgetHolder`、`APPWIDGET_HOST_ID` 1024）、大きさ（`LauncherAppWidgetProviderInfo.initSpans`、`WidgetSizes`、`WidgetSizeHandler`）、追加の流れ（`Launcher.addAppWidgetFromDrop`、`REQUEST_BIND_APPWIDGET`／`REQUEST_CREATE_APPWIDGET`、`WidgetAddFlowHandler.needsConfigure`、`completeAddAppWidget`）はアプリ側（`HomeWidgets.kt`）に書き写す。ドラッグ中はウィジェットの View そのものを `DragView` に入れ、落としたら `Workspace.onDrop` と同じく `detachContentView` でグリッドに戻す。セルの寸法は1つのプロファイル（今のセル）だけで計算する |
| 空き領域の長押しメニュー | 書き写す | — | `touch/WorkspaceTouchListener.java` はアプリ側（`HomeOptionsMenu`）にし、ページ送りの `Workspace` の代わりにホームのスクロールに渡す。項目は `popup/WorkspaceLongPressOptions.kt` から（「ホームの設定」は除く）。表示は `PopupContainer.showForMenuItems` と `showForSystemShortcuts`（View 版）を互換クラスに移す |
| グリッドの寸法 | 値を参照 | `res/xml/device_profiles.xml` | スマホ・折りたたみ・タブレットの `grid-option` と `display-option` の値（列数、フォルダの大きさ、`iconImageSize`、`iconTextSize`、`allAppsCellHeight`）を `GridSpec` に書き写す。選び方は `InvariantDeviceProfile.invDistWeightedInterpolate` と `GridOption.isEnabled` を移す。選んだグリッドの記憶（`GRID_NAME`）、グリッドの設定、`isScalable` の伸縮、横向き固定モードは持ってこない |
| グリッドの大きさと画面の向き | コピー、削ってコピー | `util/WindowBounds.java`、`util/window/CachedDisplayInfo.java`、`util/RotationUtils.java`、`shared/.../testing/shared/ResourceUtils.java` | `util/window/WindowManagerProxy.java`（各向きのウィンドウの大きさの見積もりだけ）と `states/RotationHelper.java`（構成の変化は Activity から受け取る）を削って互換クラスにする（`derived=`）。内蔵ディスプレイは、`SystemWindowManagerProxy` が使う `getPossibleMaximumWindowMetrics`（システムしか呼べない）の代わりに `DisplayManager` の `DISPLAY_CATEGORY_BUILT_IN_DISPLAYS`（API 36.1）で、使っていないものも数える。ホームを出せない小さいもの（255×400dp 未満）は数えない。大画面は回転するので、カットアウトは向きごとに回さず、一番大きいインセットを上に取る（角にあるカットアウトで、見積もりが起動したときの向きに左右されないように）。列数と行数はアプリの `GridSpec` が `InvariantDeviceProfile.invDistWeightedInterpolate` と同じく各向きの一番小さい大きさから決め（折りたたみ端末の内側は2パネルなので半分の幅）、今のウィンドウでは変えない。2パネルにするかどうかは `LauncherDisplayInfo.deviceType`（`TYPE_MULTI_DISPLAY`）と `DeviceProfile.isTwoPanels` と同じ。グリッドが変わったときの移し替えは `GridSizeMigrationLogic` をアプリ側（`GridSizeMigration`）に書き写す（DB はグリッドごとに分けず、入りきらないものは新しいページの代わりに削除）。ドラッグ中の向きの固定は `Launcher.onStateSetStart` と同じ |
| 「壁紙とスタイル」連携 | 取り決めだけ合わせる | — | `graphics/GridCustomizationsProxy.java`、`graphics/LauncherCustomizationProvider.kt` に倣って自前で書く |

コピーせずに参考にとどめたもの（依存が深いため）は次のとおり。

- `CellLayout.java` 本体、`dragndrop/DragLayer.java`、`Workspace.java`、`dragndrop/LauncherDragController.java`、`ButtonDropTarget`、`DeleteDropTarget`、`SecondaryDropTarget`、`DropTargetBar`：`Launcher`、`LauncherState`、`DeviceProfile`、Dagger に依存している。必要なメソッドだけを、名前とロジックを保って互換クラスかアプリ側に書き写した
- `AppWidgetResizeFrame.kt`：`Launcher`、`Workspace`、本物の `CellLayout` に依存している
- `folder/FolderIcon`、`folder/Folder`、`folder/FolderPagedView`：`Launcher`、`Workspace`、`BubbleTextView`、名前の欄、ページ、データベースなどに依存している。開閉、ドラッグ、中での並べ替え、中身の出し入れ、アニメーション、プレビュー、ドロップが使うメンバーだけを残して削ってコピーした（`derived=`）
- `popup/PopupContainerWithArrow.kt`、`popup/PopupContainer.kt`、`popup/SystemShortcut.java`、`touch/ItemLongClickListener.java`：`Launcher`、`LauncherState`、Compose の UI、アプリのショートカットの読み込みに依存している。メニューの行を出すのに使うメソッドだけを互換クラスに移した

### ハプティックを鳴らす場所

Launcher3 に合わせる。`msdllib` を使うものは、Launcher3 と同じく `Flags.msdlFeedback()`（リリース設定で有効）で切り替え、`util/MSDLPlayerWrapper`（互換クラス。Dagger の代わりにアプリで1つのプレイヤーを持つ）の `playToken` で鳴らす。

| 場面 | Launcher3 の鳴らし方 |
|---|---|
| ドラッグの開始 | `performHapticFeedback(LONG_PRESS)`（`LauncherDragController.onDragViewInitialized`） |
| 並び替えで落とし先のセルが変わったとき | MSDL `DRAG_INDICATOR_DISCRETE`（`CellLayout.visualizeDropLocation`） |
| フォルダを作れる位置に重ねたとき | MSDL `DRAG_INDICATOR_DISCRETE`（`Workspace`） |
| 削除・アンインストールの落とし先に入ったとき | MSDL `SWIPE_THRESHOLD_INDICATOR`（`ButtonDropTarget.onDragEnter`） |
| 全アプリ一覧の開閉の閾値 | MSDL `SWIPE_THRESHOLD_INDICATOR`（`AllAppsTransitionController`） |
| スクロールバーで見出しが変わったとき | `performHapticFeedback(CLOCK_TICK)`（`RecyclerViewFastScroller`） |

### 参考にするもの

コードは取り込まない。ただし、仕組みの組み立て方や、閾値・バネの強さなどの値は Launcher3 に合わせる。

| 部品 | 参考にする元ファイル | 合わせる点 |
|---|---|---|
| 全アプリ一覧の開閉 | `allapps/AllAppsTransitionController.java`、`allapps/ActivityAllAppsContainerView.java`、`quickstep/.../BaseDepthControllerImpl.java` | 0〜1 の進み具合で、シートの位置、ホームの縮小（`workspace_content_scale` を `ALL_APPS_SHEET_DEPTH` で）、奥行き（`getBottomSheetDepth`、`mapDepthToBlur`、`max_depth_blur_radius_enhanced`）を連動させる。一覧は `Flags.allAppsSheetForHandheld`（bp3a で有効）のハンドル付きボトムシート（`all_apps_bottom_sheet_background.xml`）で、色はぼかしの有無で `getBackgroundColor` と同じに切り替える（`WindowBlurState` の代わりに `CrossWindowBlurListeners`）。上流はランチャーの surface に背景のぼかしの半径を設定するが、アプリからはできないので、壁紙はウィンドウの `Window.setBackgroundBlurRadius`（フレームごとに描かれる。そのためウィンドウを `windowIsTranslucent` にする）で、ウィンドウ内のホームは `RenderEffect` でぼかす。`FLAG_BLUR_BEHIND` はウィンドウマネージャーを通して非同期に変わるので、指の動きに追いつかない。シートの色は Pixel Launcher の値（フレームワークの `shade_panel_fg`／`shade_panel_bg`。ダークテーマで変わる）にした。検索フィールドはシートの外で、シートの上端はいつもフィールドのすぐ下（キーボードの裏から出てくる。上流は画面の下から）。ジェスチャーなしの開閉は `config_allAppsOpenDuration`（600ms、EMPHASIZED）と 200ms（ACCELERATE）。シートの角の丸み（`Themes.getDialogCornerRadius`、`bg_celllayout` も）は、上流と同じくテーマが `Theme.DeviceDefault` を継ぐので端末の `android:dialogCornerRadius` になる |
| 開閉のジェスチャー | `touch/AllAppsSwipeController.java`、`touch/AbstractStateChangeTouchController.java` | 指の動きから進み具合への変換、離したときの速度による開く/戻るの判定（フリックならその向き、そうでなければ半分を越えたか）、`BaseSwipeDetector.calculateDuration` と `scrollInterpolatorForVelocity` での残りのアニメーション、閾値のハプティック（MSDL `SWIPE_THRESHOLD_INDICATOR`）。`mCurrentAnimation` がある間（ドラッグから残りのアニメーションが終わる `clearState` まで）は検出器を settling のままにして必ず奪い、触った時点で `onDragStart` がアニメーションを止める。離したときは、状態が変わるときだけ状態を移す（`goToTargetState`）。閉じられるかは `shouldContainerScroll` と同じく、検索中は検索結果の一覧で判定する（`getActiveRecyclerView`。`AllAppsSheet`） |
| 開閉のバネ | `anim/SpringAnimationBuilder.java`、`anim/FlingSpringAnim.java` | バネの強さと減衰、フリックの速度の引き継ぎ方 |
| 検索フィールドの振る舞い | `allapps/search/AllAppsSearchBarController.java` | 入力の監視、Enter で先頭の結果を起動、入力のクリア |

「一瞬引っ掛かってからクッと持ち上がる」動き（docs/spec.md 6.2）は Launcher3 にない。速度の判定とバネは Launcher3 に合わせ、引っ掛かりの部分だけを足す。

### 自前で書くもの

このランチャー独自の動きが中心で、取り込むメリットが小さいもの。上の「参考にするもの」に挙げた部品も、コード自体は自前で書く。

- 検索フィールド、常時キーボード、戻る操作の扱い
- 検索（正規化、照合、並び順、学習）
- 全アプリ一覧と、その開閉ジェスチャー
- 縦スクロールするホームの入れ物（中のセルの並び替えは Launcher3 のロジックを使う）
- データの保存

アプリ側のホーム画面のクラスは、受け持つ役割の Launcher3 のクラスに合わせて分け、各ファイルの説明に対応するクラスを書く（`HomeController` が Launcher の役割のまとめ、`HomeActivityContext` が Launcher の互換クラスを継承した Launcher 本体、`HomeLoader`（LoaderTask など）、`HomeItemInflater`（ItemInflater と ItemClickHandler）、`ItemLongClickListener`、`SpringLoadedListener`、`HomeWorkspace`、`HomeDragController`、`DropTargetBar`）。検索結果は `SearchResults`（ActivityAllAppsContainerView の検索部分）に置く。

ホームの Activity（`LauncherActivity`）は、Launcher3 の `BaseActivity` と同じくフレームワークの `Activity` を継承し、AppCompat と Material Components は使わない。テーマは `BaseLauncherTheme` と同じく `Theme.DeviceDefault.DayNight` を継ぎ、ダイアログは `android.app.AlertDialog`、色は dynamiccolors の `materialColor*`（システムの色）で取る。Compose（ウィジェットのリサイズ枠）に要るライフサイクルと保存状態は、`BaseActivity` と同じく `LifecycleHelper` で持つ。戻る操作は `registerBackDispatcher` と同じく `OnBackInvokedDispatcher` で受ける。AppCompat の View の置き換え（`TextView` を `MaterialTextView` に）は、ウィジェットの RemoteViews の表示にも効いて壊すため（Tex-uus1）。constraintlayout が AppCompat を推移的に持ち込むのは Launcher3 と同じで、使いはしない。

---

## 置き場所と記録

- 取り込んだコードは `third_party/` に置き、自前のコードと混ぜない
  - `third_party/launcher3/`：Launcher3 から持ってきたファイル
  - `third_party/systemui/`：`frameworks/libs/systemui` から持ってきたファイル（`iconloaderlib` など）
  - 中のパス構成とパッケージ名は、元のリポジトリと同じにする。差分を 1:1 で比べられるようにするためである
- 各ディレクトリに `UPSTREAM.md` を置き、取り込んだファイルごとに次を記録する
  - 元のリポジトリとパス
  - 元にしたタグ
  - こちらで手を入れたかどうか
- Gradle では、`third_party/` 以下をライブラリモジュールとしてビルドする
- 互換層（代わりのクラス）は `compat/` に置き、どこから使われるかで分ける
  - `compat/launcher3/`：`third_party/systemui/`（`iconloaderlib`）からも使うもの。今は `Flags` と `UserType` だけ。`iconloaderlib` は `:launcher3` より下のモジュールなので、別モジュール `:launcher3-compat` にする
  - `compat/launcher3-internal/`：それ以外のすべて。`third_party/launcher3/` と同じ `:launcher3` モジュールに含めてビルドする。コピーしたクラスやリソース（`R`）を使えるようにするためである

## 手を入れるときのルール

- 取り込んだファイルは、できるだけ直接書き換えない
- Launcher3 の他の部分（`Launcher`、`DeviceProfile`、`LauncherPrefs`、機能フラグなど）への依存は、`third_party/` の外に置いた代わりのクラス（互換層）で受け止める
- 互換層では同じパッケージ名・クラス名で、使われる部分だけを実装する
- どうしても書き換えが必要な場合は、該当箇所に `// TextLauncher:` で始まるコメントを付ける
- 上流のコードを持ってきた互換クラスは、上流のファイルから使わない部分を削って作り（`UPSTREAM.md` の `derived=`）、更新時に3方向マージできるようにする
  - 残す部分は上流の順・書き方（改行やインデントも）のまま。並べ替えたり書き直したりしない
  - 変えたところには、その場に `TextLauncher:` の印を付ける（クラスの説明にまとめて書くだけにしない）
  - TextLauncher だけのメンバーは、クラスの最後の `// TextLauncher: members TextLauncher adds.` の下にまとめる
  - 名前とシグネチャだけの互換クラスは `ref=` にして、上流の差分だけを見る
- 機能フラグ（`Flags.xxx()` など）は、基準タグのリリースでの値に固定する
  - 値は、AOSP の `build/release` の該当リリースの設定を参照する（`scripts/check-upstream.py` が `Flags` の互換クラスと `flag=` の行を突き合わせる）
  - 確認できないものは Pixel の実機の挙動に合わせる

## ライセンス

Launcher3 と `frameworks/libs/systemui` は Apache License 2.0 である。

- 取り込んだファイルのライセンスヘッダーは残す
- `third_party/` 以下に LICENSE と NOTICE を置く
- 書き換えたファイルには、変更したことを示す記載を入れる（Apache License 2.0 の条件）
- 互換層のクラスのうち、上流に同じ名前のファイルがあるものは、上流のライセンスヘッダーをそのまま付け、その下に「TextLauncher のために変更した」旨のコメントを置く。中身が上流のコードか、名前とシグネチャだけかは問わない
  - 上流のファイルにヘッダーがないもの（`popup/SystemShortcut.java`、`shortcuts/ShortcutKey.java`）は、年を除いた AOSP の標準のヘッダーを、上流にヘッダーがない旨と一緒に付ける
- 上流にソースファイルがないもの（ビルドで生成される `BuildConfig`、aconfig の `Flags`、Dagger の `ActivityComponent`、`com.android.users.UserType` など）は、上流のコードを含まないのでヘッダーを付けない

---

## 更新の手順

新しいリリースのタグが出たときの手順。

1. `scripts/update-upstream.sh third_party/launcher3 <新しいタグ>`（`third_party/systemui` も同じ）を実行する
   - `UPSTREAM.md` の `path=` に挙げたファイルと、`derived=` に挙げた互換クラス（上流のファイルから削って作ったもの）を、`git merge-file`（ours = こちら、base = 前回のタグ、theirs = 新しいタグ）で3方向マージする。削った部分で上流が変わると衝突として出るので、取り込むかどうかを判断する（多くは削ったままでよい。使っている部分に関わる修正なら取り込む）
   - 上流で増えたファイルはコピーし、消えたファイルは、こちらで手を入れていなければ消す（移動先の候補も出す）
   - 続けて `scripts/check-upstream.py <dir> --to <新しいタグ>` が、マージの対象にならないものの上流の変化を出す（下記）
   - 衝突やエラーがあると `UPSTREAM.md` のタグは前回のまま残る。直してから同じコマンドをもう一度実行する（残った衝突の印も衝突として数える）。すべて通ったときだけタグを書き換える
2. `check-upstream.py` が出した上流の変化を見て、こちらに反映する
   - `ref=` のファイル（互換層の代わりのクラス、アプリ側に書き写したもの、`Android.bp` とビルドの設定）の上流の差分。同じ言語のファイルは、こちらのファイルに出てくる行に関わる箇所だけを出す
   - 抜き出したリソースの値（`values_root=`）の追加・変更・削除
   - 機能フラグのリリースでの値の変化（`release_config=` のリリース設定を、継承をたどって引く）
3. 新しく必要になった依存は互換層に足す
4. `scripts/check-upstream.py <dir>`（タグを付けない）で、新しいタグとの食い違いがないことを確かめる。抜き出した値が上流と同じか、機能フラグの値がリリースと同じか、`ref=` のファイルが上流とこちらの両方にあるかを見る。わざと変えた値には、要素の直前に `TextLauncher:` で始まるコメントを付ける
5. ビルドとテストを通し、エミュレータと実機で手触りを確かめる
6. 「壁紙とスタイル」連携は、Pixel の実機で項目が出て動くことを確かめる

`check-upstream.py` は上流を浅く clone して `UPSTREAM_CACHE`（既定は一時ディレクトリの `textlauncher-upstream`）に取っておく。`UPSTREAM.md` のブロックに書く行の意味は、各 `UPSTREAM.md` に書いた。

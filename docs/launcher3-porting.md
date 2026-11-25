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
| `platform/packages/modules/IntentResolver`（共有シート） | タグ `android-17.0.0_r1` |

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
| アイコンのキャッシュ | 削ってコピー | `icons/CacheableShortcutInfo.kt`、`util/CancellableTask.kt` | `icons/IconCache.kt` から、Dagger・LoaderTask の一括読み込み・ホームのファイル・インストール中のセッション・アーカイブされたアプリ・インスタントアプリの部分を削って互換クラスにする（`derived=`）。キャッシュの本体（`BaseIconCache`、`IconCacheUpdateHandler` など）は iconloaderlib のまま。スレッドは上流と同じく `MODEL_EXECUTOR`。`LauncherIcons` は互換クラスにし、密度・大きさ・テーマはアプリが渡す。全アプリ一覧と検索結果のアイコンは上流と同じく `AllAppsList` の `AppInfo` が `DEFAULT_LOOKUP_FLAG` で持ち、ホームのアイコンは `LoaderTask` と上流の作業が `DESKTOP_ICON_FLAG` でホームのアイテムに引く（`IconCache.getTitleAndIcon`、まとめてではなく1件ずつ）。アプリ側にアイコンを覚えておく入れ物はない。キャッシュの更新は上流と同じく `LoaderTask.loadAllSurfacesOrdered` が行い、テーマの切り替えでは `ModelInitializer.refreshAndReloadLauncher` と同じくモデルを読み込み直す（`LauncherModel.forceReload`）。ホームの DB は上流の `favorites` と同じくアプリのショートカットのアイコンを持ち（`ContentWriter.putIcon`、既定のアイコンは持たない）、問い合わせられないときはそれを出す（`IconRequestInfo.parseIconBlob`） |
| 並び替え（押しのけ、空き探し） | アルゴリズムと反映をコピー | `celllayout/ReorderAlgorithm.java`、`celllayout/ItemConfiguration.kt`、`celllayout/ViewCluster.kt`、`celllayout/ReorderParameters.kt`、`celllayout/CellLayoutLayoutParams.java`、`celllayout/ReorderPreviewAnimation.kt`、`util/GridOccupancy.java`、`util/CellAndSpan.java`、`InterruptibleInOutAnimator.java` | `CellLayout.java` は上流と同じく画面に置く `ViewGroup` として削ってコピーし（`derived=`）、ホームの1ページとフォルダのページにする。アルゴリズム、結果の反映（`performReorder`、`animateItemsToSolution`、`revertTempState` など）、落とし先の枠（`visualizeDropLocation`）は上流のまま。ホームのページはアプリの `HomeScrollView`（縦スクロール）に入れ、セルの大きさをキーボードを下ろしたホームの高さから決めて渡す（docs/spec.md 5.1） |
| ドラッグ | コピー | `dragndrop/DragController.java`、`dragndrop/DragView.java`、`dragndrop/DragDriver.java`、`dragndrop/DragOptions.java`、`dragndrop/DraggableView.java`、`DropTarget.java`、`DragSource.java`、`graphics/DragPreviewProvider.java`、`util/TouchController.java`、`Alarm.java` など | `LauncherDragController`（フリックでの削除を除く）と `Workspace`（ドラッグとドロップ、フォルダ作成、並べ替え、外からのドロップ、アイテムの削除）は削ってコピーする（`derived=`）。`Workspace` は1ページなので `PagedView` でなく、ページのスクロールの入れ物を抱える `FrameLayout` にし、1ページ用のメンバー（`getPageAt`、`getPageCount` など）を足す。ドラッグの座標は上流と同じく `Workspace` の座標で、ページへの変換はスクロールを考えてドラッグレイヤーを通す（`mapPointFromDropLayout`）。ページの見えている部分にあるときだけページを落とし先にする（`setDropLayoutForDragObject`）。編集モード（`SPRING_LOADED`）への出入りは上流と同じく `Workspace.onDragStart` と各ドロップ先の `goToState(NORMAL, SPRING_LOADED_EXIT_DELAY)` で行い、見た目（縮小・ページの背景・壁紙を暗くする幕）は `WorkspaceStateTransitionAnimation`（`derived=`。ホットシート・ページのインジケーター・ページの透明度と横移動・ヒントの状態・システム UI の幕を削る）が描く。ピッカーから落としたウィジェットとショートカットは、上流ではアプリの追加の流れが終わるときに `NORMAL` に戻るが、アプリの追加の流れはホームの再開を待つので、落としたアニメーションの後で戻す。ページはキーボードを下ろした高さまで伸びながら縮むので、`SpringLoadedState` はその高さ（`DeviceProfile.getCellLayoutHeight`、アプリが渡す）で計算し、`HomeWorkspace` は縮小の中心をその高さの中央に置く。リサイズ枠の間の編集モード（キーボードを下ろす）は、上流と同じく `NORMAL` のまま、アプリが枠の開閉で切り替える。長押しメニューは上流と同じく `Launcher.getPopupControllerForAppIcons`／`getPopupControllerForHomeScreenItems`（`PopupController` の互換クラス）を通す。アプリの `HomeWorkspace` は `Workspace` を継承し、アイテムの長押しの受け口、モデルからの削除、ページの端での自動スクロールだけを持つ。ドラッグ中の上部のボタン（`DropTargetBar`・`ButtonDropTarget`・`DeleteDropTarget`・`SecondaryDropTarget`・`DropTargetHandler`）は削って互換クラスにし（`derived=`）、上流と同じく `drop_target_bar.xml` から作る。バーの置き場所（ホームのパネルの幅、ステータスバーの下）だけはアプリ側（`LauncherRootLayout.attachDropTargetBar`）が決める。`Folder` のドラッグ元と落とし先の部分は `Folder` の互換クラスに移す。`DragLayer` のドロップアニメーションは `DragLayer` の互換クラスに移す |
| 長押しメニューとドラッグへの移行 | コピー | `popup/ArrowPopup.java`、`popup/RoundedArrowDrawable.java`、`popup/Popup.kt`、`popup/IconViewController.kt`、`popup/Poppable.kt`、`AbstractFloatingView.java`、`InsettableFrameLayout.java`、`anim/AnimatedFloat.java`、`popup/PopupPopulator.java`、`shortcuts/ShortcutRequest.java`、`shortcuts/DeepShortcutTextView.java`、`shortcuts/ShortcutDragPreviewProvider.java`、メニューのレイアウトとドロワブル | `PopupContainer`、`PopupContainerWithArrow`、`SystemShortcut`（`AppInfo` を含む）は互換クラスにし、システムショートカットとアプリのショートカットの行を出す経路のメソッドを移す（Compose 版は除く）。`shortcuts/DeepShortcutView.java` はそのままコピーし、追加ボタンとバッジ付きのコピーは `Launcher` の互換クラスを通してアプリが受ける。ショートカットをメニューからドラッグする `LauncherPopupItemDragHandler` はアプリ側に移し、`Workspace.beginDragShared` の移植に渡す。追加ボタンは上流と同じく `LauncherAccessibilityDelegate.addToWorkspace`（互換クラス）で最初の空きセルに置き、`Launcher.bindInflatedItems`（互換クラスに移した）のバウンスで出す。ホームのショートカットのシステムへの固定は `BgDataModel.updateShortcutPinnedState` を移す。アプリのショートカットのアイコンは `IconCache` の `getShortcutIcon` で作る（バッジも付く）。「検索文字列を編集」はアプリ側で `SystemShortcut` を継承して書く。長押しは上流と同じく `touch/ItemLongClickListener.java`（`derived=`。オーバービュー・分割の選択・記録を削り、全アプリ一覧のアプリはホームの項目にしてドラッグする）が受け、アプリのメニューは `PopupControllerForAppIcon`、フォルダとウィジェットのメニューは `Flags.homeScreenEditImprovements`（リリース設定で有効）に合わせて `PopupControllerForExtraHomeScreenItems`（`PopupDataRepository` と `PopupDataSource` の `FolderSystemShortcuts`・`AppWidgetSystemShortcuts`。どれも `derived=`）が出す。メニューの項目はアプリの `HomeSystemShortcuts`（`Launcher.getSupportedShortcuts`。「検索文字列を編集」と「アプリ情報」）が決め、ウィジェットの「設定」はアプリの追加の流れが受ける。メニューからのショートカットのドラッグは上流の `LauncherPopupItemDragHandler`（`derived=`）。`FolderIcon` と `LauncherAppWidgetHostView` の互換クラスは上流と同じく `Poppable` を、`FolderIcon` は `IconViewController` も実装する。ドラッグへの移行は `PopupContainer.createPreDragCondition` をそのまま使う |
| ウィジェットのリサイズ枠 | コピー、削ってコピー | `widget/resize/ResizeFrame.kt`、`widget/resize/ResizeFrameViewModel.kt`、`util/compose/TestModifiers.kt`、`PopupTouchHandler.kt` | android-17 のリリース設定では `Flags.fixWidgetSinglePtrResize` が有効で、Launcher3 は Compose 版の枠（`AppWidgetResizeFrameCompose`）を出す。そのため Jetpack Compose を launcher3 モジュールに入れ、Compose 版を移す（View 版の `AppWidgetResizeFrame` は使わない。Tex-yw4e で決定）。`AppWidgetResizeFrame`（`IntRange` と `showForWidget`）、`ResizeManager`、`AppWidgetResizeFrameCompose` は互換クラスにし（`derived=`）、ウィジェットのスパン（`LauncherAppWidgetProviderInfo.initSpans`）とモデルの保存（`CellLayout.commitTempPlacement` の DB 書き込み）は `Launcher` の互換クラスを通してアプリから受ける。大きさの通知は上流と同じく `WidgetSizeHandler.updateSizeRanges`。`CellLayout` の互換クラスには `hasAreaForResize`・`createAreaForResize` を移す。枠を出す場面は上流と同じく、長押し（`Flags.homeScreenEditImprovements` が有効なので、`PopupControllerForExtraHomeScreenItems` のメニューと同時）、ドロップの後（`Workspace.getWidgetResizeFrameRunnable`）、追加の直後（`Launcher.showWidgetResizeFrame`）。ホームが縦にスクロールし、キーボードで動くので、枠はウィジェットに追従させる |
| フォルダ | コピー | `folder/` の開閉アニメーション一式（`FolderAnimationSpringBuilderManager.kt`、`FolderSpringAnimatorSet.kt`、`FolderAnimationData.kt`、`ClipRevealData.kt`、`IconAnimationData.kt` など）、`folder/PreviewItemManager.java`、`folder/FolderPreviewItemAnim.java`、`folder/PreviewBackground.java`、`folder/ClippedFolderIconLayoutRule.java`、`folder/FolderGridOrganizer.java`、`graphics/ShapeDelegate.kt`、ページ送りの `touch/PagedOrientationHandler.java`・`touch/DefaultPagedViewHandler.java`・`util/EdgeEffectCompat.java`、ページのインジケーターの `pageindicators/PageIndicator.java`・`PageIndicatorDots.java`・`PaginationArrow.kt` | `Folder`、`FolderIcon`、`FolderPagedView`、`BubbleTextView`、`Launcher` などは互換クラスにし、アプリ側の View がそれを継承する。`Folder` は上流と同じく `AbstractFloatingView` で、開閉、外側のタッチ、ドラッグ元、中身の出し入れを `Folder.java` から移す。開いたフォルダは上流と同じく落とし先（`DropTarget`）で、ドラッグ中も開いたまま、中での並べ替え（`onDragOver` → `FolderPagedView.realTimeReorder`）、外に出たら閉じる（`onDragExit` → `completeDragExit`）、失敗したドラッグの戻し（`onDropCompleted`）、外からのドロップ（`beginExternalDrag`、`onDrop`）も上流のまま。`FolderPagedView` と `PagedView` は削ってコピーし（`derived=`）、上流と同じく列×行ごとのページに分けて横にスクロールし、ページをまたぐ並べ替え（`Folder.onDragOver` のスクロールのヒントと `mOnScrollHintAlarm`・`mScrollPauseAlarm`）、下の段のページのインジケーターと矢印（`layout/user_folder_icon_normalized.xml` のフッターから名前の欄を除いた `res-caretlauncher/layout/folder_footer.xml`。ページが1つのときは高さを取らない）も上流のまま。ページは上流と同じく `CellLayout` が子の `ShortcutAndWidgetContainer` にアイコンを持つ。`FolderIcon` のプレビュー描画、ドロップ、作成アニメーション、上に重ねたら開く処理（`onDragEnter` の `mOpenAlarm`）のメソッドは `FolderIcon.java` から互換クラスに移す。データベースへの書き込みの代わりに、中身の変化をアイコンがアプリに伝え、アプリがモデルを保存する |
| 全アプリ一覧と見出しに飛ぶスクロールバー | コピー、削ってコピー | `views/RecyclerViewFastScroller.java`、`graphics/FastScrollThumbDrawable.java`、`FastScrollRecyclerView.java`、`allapps/AllAppsFastScrollHelper.java`、`allapps/LetterListTextView.java`、`allapps/AllAppsGridAdapter.java`、`allapps/AppInfoComparator.java`、`allapps/search/SearchAdapterProvider.java`、`allapps/search/DefaultSearchAdapterProvider.java`、`compat/AlphabeticIndexCompat.java`、`util/LabelComparator.java`、`util/ScrollableLayoutManager.java` | `allapps/AlphabeticalAppsList.java`、`allapps/BaseAllAppsAdapter.java`、`allapps/AllAppsRecyclerView.java` から、仕事用プロファイル・プライベートスペース・記録・文字の一覧（`Flags.letterFastScroller` はリリース設定で無効）・マウス操作（`AllAppsRecyclerView` の `Flags.enableCursorDrivenWorkflows`。リリース設定で無効）の部分を削って互換クラスにする（`derived=`）。`AllAppsStore` は互換クラス。`ActivityAllAppsContainerView` はアプリ側（`AllAppsView`）に、`AdapterHolder.setup` とスクロールバーへのタッチの振り分けを移す。アプリの一覧は `model/AllAppsList.java` を削った互換クラス（`derived=`。リポジトリとそのフラグ・インストールの進捗・ユーザーのキャッシュ・アーカイブ・自動化を削った）で、上流と同じくモデルのスレッド（`MODEL_EXECUTOR`）で `LoaderTask.loadAllApps`（アイコンは一括ではなく1件ずつ）とパッケージの作業が変え、`copyData` をメインスレッドに渡す（`bindAllApplications`。ホームの `ModelCallbacks` から、アプリ側の `AppRepository` に渡す）。全アプリ一覧・検索結果・ホームの引き当ては、どれもこの `AppInfo` を `ComponentKey` で引く（`AllAppsStore.getApp` と同じ二分探索）。見出しは `AlphabeticIndexCompat` で付ける。アイコンは `BubbleTextView.verifyHighRes`（`IconCache.updateIconInBackground`）も上流どおり。セルはアプリが作る（`all_apps_icon.xml` の代わり。検索結果と同じ見た目）。検索結果も上流の `AdapterHolder.SEARCH` と同じく、アプリを持たない `AlphabeticalAppsList` の `setSearchResults` を `AllAppsGridAdapter` で出し、全アプリ一覧と同じ `AppInfo`（`AllAppsStore` のもの）をバインドする（クリックは `ItemClickHandler`、長押しは `INSTANCE_ALL_APPS` と同じ経路）。ホームの検索結果は下から並ぶので、グリッドのレイアウトマネージャーを逆向きにする |
| 縦スワイプの判定、端での引っ張り | コピー | `touch/BaseSwipeDetector.java`、`touch/SingleAxisSwipeDetector.java`、`touch/OverScroll.java`、`MotionEventsUtils.java` | 全アプリ一覧の開閉（`AllAppsSwipeController`）が使う。引っ掛かりは `OverScroll.dampedScroll` で付ける。`util/TranslateEdgeEffect.java`、`util/EdgeEffectCompat.java` は使う場面がないので取り込んでいない |
| 全アプリ一覧の開閉 | 削ってコピー、参考にする | `allapps/AllAppsTransitionController.java`（`derived=`）、参考にするもの: `allapps/ActivityAllAppsContainerView.java`、`quickstep/.../BaseDepthControllerImpl.java`、`statehandlers/DepthController.java` | 上流と同じ形に分ける。状態は上流の `LauncherState`（`NORMAL`、`SPRING_LOADED`（編集中）、`ALL_APPS` だけを残した `derived=`。`ALL_APPS` は quickstep の `AllAppsState`）で、上流の `StateManager`（`derived=`）を `Launcher` の互換クラスが `StatefulContainer` として持つ。戻る・ホーム・メニュー・スワイプ・編集の始まりと終わりは `goToState` で移る。状態の切り替えでの検索フィールドのフォーカスとキーボードは `Launcher.onStateSetStart`／`onStateSetEnd` に当たるアクティビティのフックが受け持つ（ホームの `Launcher` が、移る前の状態とアニメーションするかを添えて渡す）。ハンドラーは上流と同じく全アプリ一覧の遷移、`Workspace`、奥行きの3つ。ホームのぼかしは上流の `LauncherState.shouldBlurWorkspace` に従い、全アプリ一覧の状態だけでかける（編集モードの奥行きは壁紙だけをぼかす）。状態のハンドラーは `AllAppsTransitionController`（縦の位置とホームの縮小。縮小は `WORKSPACE_SCALE_PROPERTY_FACTORY` の `SCALE_INDEX_WORKSPACE_STATE`）と `DepthController`（`stateDepth` と、編集中の奥行きを `Float::max` でまとめる `MultiPropertyFactory`、ぼかしの可否、`mapDepthToBlur`、`applyDepthAndBlur`）で、ジェスチャーでないアニメーションは `PendingAnimation`（コピー）に各ハンドラーが `setStateWithAnimation` で加える。補間は上流と同じく `QuickstepAtomicAnimationFactory` が `applyNormalToAllAppsAnimConfig`／`applyAllAppsToNormalConfig` で決める（`StateAnimationConfig` はコピー）。スワイプも上流と同じく `createAnimationToNewWorkspace` の `AnimatorPlaybackController`（コピー）で各ハンドラーを動かし、シートの位置、ホームの縮小（`workspace_content_scale` を `ALL_APPS_SHEET_DEPTH` で）、奥行き（`getBottomSheetDepth`、`mapDepthToBlur`、`max_depth_blur_radius_enhanced`）が連動する。`AllAppsTransitionController` は上流から削った互換クラス（`derived=`）で、進み具合も上流と同じく 0 が一覧、1 がホーム。進み具合でフィールドとすぐ下のシートを置くのはアプリの `AllAppsTransitionHost`（`Host`）で、移動の幅はフィールドの開いた位置から閉じた位置まで（`getShiftRange`。閉じた位置はキーボードに付いて動く）。一覧は `Flags.allAppsSheetForHandheld`（bp3a で有効）のハンドル付きボトムシート（`all_apps_bottom_sheet_background.xml`）で、色はぼかしの有無で `getBackgroundColor` と同じに切り替える（`WindowBlurState` の代わりに `CrossWindowBlurListeners`）。上流はランチャーの surface に背景のぼかしの半径を設定するが、アプリからはできないので、壁紙はウィンドウの `Window.setBackgroundBlurRadius`（フレームごとに描かれる。そのためウィンドウを `windowIsTranslucent` にする）で、ウィンドウ内のホームは `RenderEffect` でぼかす。`FLAG_BLUR_BEHIND` はウィンドウマネージャーを通して非同期に変わるので、指の動きに追いつかない。シートの色は Pixel Launcher の値（フレームワークの `shade_panel_fg`／`shade_panel_bg`。ダークテーマで変わる）にした。検索フィールドはシートの外で、シートの上端はいつもフィールドのすぐ下（キーボードの裏から出てくる。上流は画面の下から）。ジェスチャーなしの開閉は `config_allAppsOpenDuration`（600ms、EMPHASIZED）と 200ms（ACCELERATE）。上流と同じく、この補間は縦の位置（`ANIM_VERTICAL_PROGRESS`）だけにかけ、ホームの縮小と奥行き（`ANIM_WORKSPACE_SCALE`、`ANIM_DEPTH`）は素の進み具合に `ALL_APPS_SHEET_DEPTH` をかける。ジェスチャーでは縦の位置も素の進み具合で、離したあとは全体に `scrollInterpolatorForVelocity` をかける。シートの角の丸み（`Themes.getDialogCornerRadius`、`bg_celllayout` も）は、上流と同じくテーマが `Theme.DeviceDefault` を継ぐので端末の `android:dialogCornerRadius` になる |
| 開閉のジェスチャー | 削ってコピー | `touch/AbstractStateChangeTouchController.java`、`touch/AllAppsSwipeController.java`（`derived=`）、`util/FlingBlockCheck.java` | 上流と同じく `TouchController` で、`Launcher.createTouchControllers` のとおりドラッグの次にドラッグレイヤーに渡す（`canInterceptTouch`、`getSwipeDirection`、`getTargetState`）。離したときの速度による開く/戻るの判定（フリックならその向き、そうでなければ半分を越えたか。大画面は `TABLET_BOTTOM_SHEET_SUCCESS_TRANSITION_PROGRESS`）、`FlingBlockCheck`、`BaseSwipeDetector.calculateDuration` と `scrollInterpolatorForVelocity` での残りのアニメーション、`mCurrentAnimation` がある間は検出器を settling のままにして必ず奪い、触った時点で `onDragStart` がアニメーションを止めるのは上流のまま。印を付けて変えたのは次のところ。キーボードが下りるにつれてフィールドの閉じた位置が動くので、進み具合は `mProgressMultiplier` でなく、指が運んだフィールドの位置から出す。48dp までは `OverScroll.dampedScroll` で少しだけ動く溜めにし（docs/spec.md 6.2。Launcher3 にない）、越えたところでハプティック（MSDL `SWIPE_THRESHOLD_INDICATOR`。閉じるときも鳴らす）、フィールドが指に追いつき（150ms）、開くときはそこで状態が変わる（`dispatchOnStart`。キーボードが下りる）。閉じるときと、溜めの途中や動いている最中に触ったときは、指を離して向かう先が決まったときに状態が変わる。溜めの途中で離すと戻る。状態を変えたあとで元の状態に戻るときは `StateManager` の互換クラスに足した `onUserControlledTransitionStart`／`End` で戻し、拾ったアニメーションの終わりもそこで伝える。戻るときは `dispatchOnCancel` の代わりに成功のリスナーだけに取り消しを伝える（上流は直後の `dispatchOnStart` でコントローラーを再び動かすが、ここでは状態を変えないため）。始められるのは検索フィールドか下端までスクロールしたホームから（`Callbacks.canOpenFrom`）で、ドラッグ中は始めない。閉じられるかは `shouldContainerScroll` と同じく、検索中は検索結果の一覧で判定する（`AllAppsSheet`）。ドラッグの途中で長押しのメニューやフォルダが開いたら手放す。記録・トラックパッド・マウス・デスクトップ・一覧の引っ張り（`onPull`）は削った |
| 下スワイプで通知シェード | 削ってコピー | — | `quickstep/.../touchcontrollers/StatusBarTouchController.java` を互換クラスにする（`derived=`）。判定（`canInterceptTouch`、タッチスロップ）は上流のまま、`QuickstepLauncher.createTouchControllers` と同じくドラッグ、全アプリ一覧のスワイプの次のタッチコントローラーにする。`SystemUiProxy` は最近使ったアプリの画面を担うランチャーしか使えないので、タッチを System UI に渡す代わりに、判定した時点で `StatusBarManager.expandNotificationsPanel` を一度呼ぶ（指には追従しない）。指に追従しない代わりに、開いた瞬間に Launcher3 のスワイプのしきい値の振動（`SWIPE_THRESHOLD_INDICATOR`）を鳴らす（CaretLauncher 独自。上流にはない）。このメソッドは隠し API だが、`@UnsupportedAppUsage` に `maxTargetSdk` がなく、android-17.0.0_r1 の `frameworks/base/boot/hiddenapi` の max-target のリストにもないので、アプリから呼べる（リフレクション。権限は通常の `EXPAND_STATUS_BAR`）。NORMAL 状態の判定はアプリが渡し、縦スクロールするホームが一番上にあるときだけにする（docs/spec.md 5.6） |
| ハプティック | ライブラリごとコピー | `frameworks/libs/systemui/msdllib/` | `util/MSDLPlayerWrapper.java` の代わりを互換層に置く。鳴らす場所と種類は Launcher3 に合わせる（下記） |
| キーボードの出入りへの追従 | コピー | `anim/KeyboardInsetAnimationCallback.java` | — |
| キーボードのフォーカス枠 | コピー | `keyboard/ItemFocusIndicatorHelper.java`、`keyboard/FocusIndicatorHelper.java`、`keyboard/FocusedItemDecorator.java`、`keyboard/ViewGroupFocusHelper.java` | 検索結果への付け方は `ActivityAllAppsContainerView.AdapterHolder.setup` と `BaseAllAppsAdapter.setIconFocusListener` に、ホームは `DragLayer` と `ItemInflater` に（枠はスクロールするグリッドに描く）、フォルダは `FolderPagedView` と `Folder` のフォーカス処理（開いたらフォルダにフォーカス、中だけを探す、閉じたらアイコンに戻す）に、文字を打ったら検索フィールドに戻す処理は `AppsSearchContainerLayout.preDispatchKeyEvent` に倣う。入力が変わったら1位に枠を付ける処理は Pixel Launcher に合わせた自前のもの（docs/spec.md 2.6）。ページをまたぐフォーカスの移動は上流の `PagedView`（`addFocusables`、`requestChildFocus`、`dispatchUnhandledMove`）のまま。開いたフォルダの中だけを探すのは、アイコンからの探索も `HomeFolder.focusSearch` でフォルダに閉じる（後ろのホームのアイテムが隣のページより先に選ばれないように） |
| タップ | 削ってコピー | — | `touch/ItemClickHandler.java` から、アプリペア・ウィジェット・インストール中のアイコン・プライベートスペースの部分を削って互換クラスにする（`derived=`）。無効な項目（作成元が無効にしたショートカット、停止中のアプリなど）のタップも上流と同じ。起動そのものは `Launcher.startActivitySafely` をアプリが実装する。検索結果のタップは、学習の記録のためアプリ側で受ける |
| ウィジェットピッカー | コピー、削ってコピー | `modules/widgetpicker/`（別モジュール `:widgetpicker`）、`widgetpicker/`（`WidgetPickerActivity`、リポジトリ、データソース、テーマ、記録）、`model/WidgetsModel.java`・`WidgetItem.java`、`widget/DatabaseWidgetPreviewLoader.java`・`WidgetSections.java`、`util/ContextTracker.java` ほか | android-17 のピッカーは Compose 版だけなので、それを移す（Tex-r705。OS 標準の選択画面を使う Tex-h3ld の決定は取り消し）。上流と同じく Dagger（KSP で生成）でつなぐ。ルートの `LauncherAppComponent` には `LauncherWidgetPickerModule` と、移植していないモジュールの代わりの `CaretLauncherAppModule` を入れる。開き方（`ACTION_PICK` を自分のパッケージに）、「追加」の流れ（`Launcher.ACTIVITY_TRACKER` に `WidgetPickerAddItemListener` を登録してホームを前面にし、`LauncherAccessibilityDelegate.addToWorkspace` → `Launcher.addAppWidgetFromDrop`）は上流と同じ。CaretLauncher はウィジェットを作る許可（`BIND_APPWIDGET`）を持たないので OS の確認画面を出すことがあり、それが消えないよう追加はホームが resume してから始める。ピッカーからのドラッグも上流と同じく、ピッカーが始めたシステムのドラッグ（`View.startDragAndDrop`）を `WidgetPickerDragItemListener`（`BaseItemDragListener`）がホームの `DragLayer` で受け、`PendingItemDragHelper` でホームのドラッグにつなぐ（`Flags.enableSystemDrag` はリリース設定で無効なので、`DragLayer.setOnDragListener` と `DragDriver.SystemDragDriver` を使う）。落とすと `Workspace.onDropExternal` → `Launcher.addPendingItem` で「追加」と同じ流れに入る。ドラッグ中にウィジェットを先に作っておくこと（`WidgetHostViewLoader`）はしない。ショートカットの作成も上流と同じく、`Launcher.processShortcutFromDrop`（`REQUEST_CREATE_SHORTCUT`）でアプリの作成画面を開き、結果のピン留めの要求を `PinRequestHelper` で受けて置く（作成画面のあいだにホームが作り直されたときの保存もウィジェットと同じ）。落としたセルが埋まっていたらフォルダは作らず最初の空きセルに置く。ほかのアプリからのピン留めの要求（`AddItemActivity`）はまだ移していない。ほかのアプリのピン留めの要求（`LauncherApps.PinItemRequest`、`CONFIRM_PIN_SHORTCUT`・`CONFIRM_PIN_APPWIDGET`）は上流と同じく `AddItemActivity`（`derived=`）が受け、`WidgetPickerComposeWrapper.showWidgetsForPinRequest`（`PinAppWidgetCatalog`）でシートを出す。「追加」は `ItemInstallQueue`（`derived=`。ファイルに保存したキューを、ホームの読み込みのあとと前面に出たときに `AddWorkspaceItemsTask` で空きセルに置く。ページが1つなので、いっぱいなら置かない）に入れ、ドラッグは `PinItemDragListener`（`derived=`）と `PinWidgetFlowHandler`（そのままコピー）でホームに落とす。ウィジェットのバインドは、ほかの追加と同じく OS の確認画面を経る |
| 通知ドット | コピー、削ってコピー | `notification/NotificationListener.java`・`NotificationRepository.kt`・`NotificationKeyData.java`・`NotificationGroup.java`、`dot/DotInfo.java`・`FolderDotInfo.java`、`util/PackageUserKey.java`、`util/DaggerSingletonObject.java` | 通知の受け取り（`NotificationListener`）とアプリごとの通知（`NotificationRepository`）は上流のまま、Dagger のルートの部品にする（`SettingsCache` は削ってコピー）。アイコンへの反映は `PopupDataProvider` の `updateNotificationDots`（ホームは `ActivityContext.getContent`、開いたフォルダ、全アプリ一覧と検索結果は `AllAppsStore` に登録した RecyclerView）、描画は `BubbleTextView` と `FolderIcon` の上流のコード（`DotRenderer` は iconloaderlib）。OS の通知ドットの設定がオフになると listener が外れ、オンに戻すと `ModelInitializer` と同じく `requestRebind` する（アプリの `Application` で）。通知へのアクセスを求める入口は、Launcher3 の設定画面（`NotificationDotsPreference`）の代わりに空いている場所の長押しメニューに置き、ダイアログは `NotificationAccessConfirmation` と同じ。許可の確認は `ENABLED_NOTIFICATION_LISTENERS` を読む代わりに `NotificationManager.isNotificationListenerAccessGranted`。長押しメニューの通知の行は入れない |
| ウィジェットのホストと追加 | コピー、削ってコピー、書き写す | `CheckLongPressHelper.java` | `widget/LauncherAppWidgetHostView.java` は互換クラスにし、長押しの判定（`CheckLongPressHelper`）、`AppWidgetHostView` の既定の余白を付けないこと、ドラッグの種類・範囲（どちらも `NavigableAppWidgetHostView`）だけを持つ。追加したウィジェットは Launcher3 と同じく大きさ（`PendingAddWidgetInfo.bindOptions`）を付けてバインドする。大きさは上流のまま、`LauncherAppWidgetProviderInfo.initSpans`、`WidgetSizes`（コピー）、`WidgetSizeHandler`（ホットシートの QSB を削って互換クラスにする。`derived=`）が互換の `InvariantDeviceProfile` の1つのプロファイル（`supportedProfiles`）から計算する。ホスト（`LauncherWidgetHolder`、`APPWIDGET_HOST_ID` 1024）はアプリ側（`HomeWidgets.kt`）に書き写す。追加の流れ（`Launcher.addPendingItem`・`addAppWidgetFromDrop`・`processShortcutFromDrop`・`handleActivityResult`・`completeAdd`・`completeAddShortcut`・`completeAddAppWidget`）は互換の `Launcher` に上流の順で移し、`PendingRequestArgs`・`ActivityResultInfo`・`LauncherConstants` はそのままコピー、`WidgetAddFlowHandler` は削ってコピーにする（`LauncherWidgetHolder` の代わりにバインドの許可と設定画面を自分で開く）。上流は特権の `BIND_APPWIDGET` で自分でバインドし、設定画面の間は保留中のウィジェットの View を置くが、CaretLauncher は OS の確認画面を経て、設定画面から戻ってから置く。ピッカーから戻る Home の Intent が Activity を消さないよう、追加はホームが前面に出るのを待つ（`runWhenResumed`）。追加は `addPendingItem` が決めたセルとスパン（`PendingAddWidgetInfo`、設定画面の間は `PendingRequestArgs`）で置き、そのセルが埋まっていれば最初の空きに置く（ショートカットは上流と同じく、そこにアイテムがあればフォルダを作るか加える）。待っている追加の流れと結果（`mPendingRequestArgs`、`mPendingActivityResult`）は上流と同じく Activity の保存状態に入れる。ドラッグ中はウィジェットの View そのものを `DragView` に入れ、落としたら `Workspace.onDrop` と同じく `detachContentView` でグリッドに戻す。ドラッグ中の縮小は上流のまま、`Workspace.beginDragShared` の `preDragEndScale`（`Flags.homeScreenEditImprovements`）と `LauncherDragController.createDragView` の `scalePx` を `WidgetDragScaleUtils`（コピー）で決める。これが使う `DeviceProfile.getWorkspaceSpringLoadScale` は、編集中のホームの縮小（`SpringLoadedState`）と同じ値で、キーボードを下ろしたホームの高さからアプリが渡す。TalkBack からの操作（`LauncherAccessibilityDelegate`。リサイズ枠にも付く）は「TalkBack」の行を参照。セルの寸法は1つのプロファイル（今のセル。グリッドを測る前は、ディスプレイの一番小さいウィンドウでのセル）だけで計算する。読み込みのあと、DB にないウィジェットの id をホストから消す（`DatabaseHelper.removeGhostWidgets`。アプリの `HomeLoaderHooks` が行い、追加の途中の id は残す） |
| 空き領域の長押しメニュー | 削ってコピー | — | `touch/WorkspaceTouchListener.java` と `popup/WorkspaceLongPressOptions.kt` を削って互換クラスにし（`derived=`）、リスナーはページ送りの `Workspace` の代わりにホームのスクロールに付ける。画面の端とインセットでの除外は、ホームがステータスバーと検索フィールドの間に収まっているので省く。項目は上流のまま（「ホームの設定」は除き、その位置にアプリが作る「テキスト共有」（`Launcher.getShareTextOption`。docs/spec.md 2.7）を置き、通知ドットの設定が通知へのアクセスを欠くときだけ「通知ドット」を出す。2パネルでは「アプリの一覧」を出さない）。`Launcher.showDefaultOptions` は `Launcher` の互換クラスに移し、通知へのアクセスのダイアログ（`NotificationDotsPreference.NotificationAccessConfirmation`）はアプリ側（`NotificationAccessConfirmation`）に書き写す。表示は `PopupContainer.showForMenuItems` と `showForSystemShortcuts`（View 版）を互換クラスに移す |
| ホームのデータベース | コピー、削ってコピー | `util/ContentWriter.java`、`util/SQLiteTable.kt` | ホームの配置は Launcher3 の `favorites` の表（`LauncherSettings.Favorites` の列と値。アプリはインテント、ショートカットは `ShortcutKey` のインテント、`profileId`、`title`、`icon`）をアプリのデータベース（`launcher.db`）に持つ。版2までの独自の表（`home_items`）は版3への更新で移す。`ModelDbController` は上流から削った互換クラス（`derived=`）で、`DatabaseHelper` の代わりにアプリのデータベースを受け、id の数え方（`initIds`・`insertAndCheck`・`generateNewItemId`）を持つ。復元・空のデータベースの印・既定のレイアウト・グリッドごとのデータベースとその移し替え（`attemptMigrateDb`。アプリの `GridSizeMigration` が1つのデータベースの中で移す）は削った。`LauncherDbUtils` も同じく削ってコピーにした（`itemIdMatch`・`tableExists`・`SQLiteTransaction`）。メモリ上のモデルは `BgDataModel`（`derived=`。すべてのアイテムを id で持つ `WorkspaceData` と `updateShortcutPinnedState`）で、上流と同じくモデルのスレッド（`MODEL_EXECUTOR`）にある。`LauncherModel`（`derived=`。アプリに1つ）が `LoaderTask`（`LoaderCursor`・`WorkspaceItemProcessor`、どれも `derived=`）でデータベースから読み、`BaseLauncherBinder` でホームの `ModelCallbacks`（`HomeController`）にバインドする。項目は上流のクラスのまま（アプリとショートカットは `WorkspaceItemInfo`、フォルダは `FolderInfo`、ウィジェットは `LauncherAppWidgetInfo`）。読めない行は上流と同じく消える（`LoaderCursor.markDeleted`）。グリッドの移し替えは `ModelDbController.attemptMigrateDb` の場所で、アプリの `GridSizeMigration` が1つのデータベースの中で行う。モデルの書き換えはすべて `ModelWriter`（`derived=`。`IModelWriter`・`ModelWriterFactory` も）を通り、上流と同じくモデルのスレッドでメモリ上のモデルとデータベースを変え、変わったアイテムだけを書く（`enableTransactionalModelWriter` はリリース設定で無効なので、1件ずつ書く）。フォルダー（`Folder` の互換クラス）、`CellLayout.commitTempPlacement`、`Workspace.onDrop`、`Launcher.removeItem`（削除のドロップ先とメニューの「削除」）は上流と同じく `ModelWriter` を呼ぶ。ショートカットの固定は、上流と同じくアイテムの追加と削除（`BgDataModel.addItems`・`removeItems`）と読み込みの後（`LoaderTask.sanitizeWidgetsShortcutsAndPackages`）にだけ更新するので、ドラッグの途中で外れない。各アイテムの値は上流と同じく `ItemInfo.onAddToDatabase`（`ContentWriter`）で作り、アイコンの平坦化と既定のアイコンかどうかの判定（`ContentWriter.getValues`）、データベースの読み書き、ショートカットの問い合わせと固定の更新は `MODEL_EXECUTOR` で行う |
| ホームの部分更新 | 削ってコピー | — | アプリの変化は上流と同じく LauncherApps のコールバック1つ（`ModelLauncherCallbacks`、`derived=`）が受け、上流の作業（`PackageUpdatedTask`、`PackageTaskFactory` の `appsRemoved`・`appsUnavailable`・`appsSuspended`・`appsUnsuspended`、`CacheDataUpdatedTask`、`ShortcutsChangedTask`、どれも `derived=`）がモデルのスレッドでアプリの一覧・アイコンのキャッシュ・当てはまるアイテムだけを変えて書き、`ModelTaskController` がホームの `ModelCallbacks`（`HomeController`）にバインドさせる。バインドは `ModelCallbacks` と同じく `LauncherBindableItemsContainer.updateContainerItems`（開いているフォルダも）と `Workspace.removeItemsByMatcher`（フォルダは `Folder.removeFolderContent` から `replaceFolderWithFinalItem`）、`DragController.onAppsRemoved`、`PopupContainer.dismissInvalidPopup`。ドラッグの取り消しは、当てはまるアイテムやドラッグ元のフォルダの中身があるときだけ、ビューを変える前に行う。全体の読み込み直しは、アイコンのテーマや形の変更（`ModelInitializer.refreshAndReloadLauncher`）と、グリッドが変わったとき（`Launcher.onIdpChanged`）だけ |
| グリッドの寸法 | コピー、削ってコピー | `res/xml/device_profiles.xml`、`deviceprofile/parser/GridOption.kt`・`DisplayOption.kt`・`DeviceTypedMap.kt`、`GridType.kt` | `InvariantDeviceProfile` を削って互換クラスにする（`derived=`）。`initGrid` に、ディスプレイ情報（`LauncherDisplayInfo`）と保存した列数（`DeviceGridState` の代わりに `GridSizeMigration`）をアプリから渡し、上流と同じく `getPredefinedDeviceProfiles`（`canBeDefault`）、`filterByColumnCount`、`invDistWeightedInterpolate`、`initGridForDisplayOption` で選ぶ。属性とスタイル（`GridDisplayOption`・`ProfileDisplayOption`・`GridSize`・`AllAppsSize`・`CellStyleDefault`・`AllAppsStyleDefault`）は上流から写し、読まない参照先（既定のレイアウト、伸縮するグリッドの余白、横向き固定モードのスペックとフォルダのスタイル）は空の値で宣言する。アプリの `GridSpec` は、選ばれた値（列数、フォルダの大きさ、`iconSize`、`iconTextSize`、`allAppsCellSize`）にホームの行数・余白・セルの大きさを足す。グリッドはホームが作られるとき（`HomeController`）に1回だけ選び、互換の `DeviceProfile`（`HomeActivityContext`）を作る。全アプリ一覧と検索結果のセルのアイコン・文字の大きさとセルの高さは、上流の `BubbleTextView` と同じく `DeviceProfile.AllAppsProfile` から取る。構成の変化では上流の `InvariantDeviceProfile.onConfigChanged` と同じく選び直して比べ、違えば Activity を作り直す。選んだグリッドの記憶（`GRID_NAME`）、グリッドの設定、`isScalable` の伸縮、横向き固定モード、パートナーの上書き、対応する大きさごとのデバイスプロファイルは持ってこない |
| グリッドの大きさと画面の向き | コピー、削ってコピー | `util/WindowBounds.java`、`util/window/CachedDisplayInfo.java`、`util/RotationUtils.java`、`shared/.../testing/shared/ResourceUtils.java`、`display/LauncherDisplayInfo.kt`・`PortraitSize.kt`、`util/NavigationMode.java`、`util/FlagDebugUtils.kt` | `util/window/WindowManagerProxy.java`（Dagger と `DisplayController` を使うインセットの補正なし）と `states/RotationHelper.java`（構成の変化は Activity から受け取る）を削って互換クラスにする（`derived=`）。内蔵ディスプレイは、`SystemWindowManagerProxy` が使う `getPossibleMaximumWindowMetrics`（システムしか呼べない）の代わりに `DisplayManager` の `DISPLAY_CATEGORY_BUILT_IN_DISPLAYS`（API 36.1）で、使っていないものも数える。ホームを出せない小さいもの（255×400dp 未満）は数えない。大画面は回転するので、カットアウトは向きごとに回さず、一番大きいインセットを上に取る（角にあるカットアウトで、見積もりが起動したときの向きに左右されないように）。同じ理由で、大画面の今の向きの実際の大きさ（`getRealBounds`。`LauncherDisplayInfo` が見積もりを置き換える）も、その向きの見積もりにする。列数は `InvariantDeviceProfile` が、行数はアプリの `GridSpec` が `invDistWeightedInterpolate` と同じく各向きの一番小さい大きさから決め（折りたたみ端末の内側は2パネルなので半分の幅）、今のウィンドウでは変えない。2パネルにするかどうかは `LauncherDisplayInfo.deviceType`（`TYPE_MULTI_DISPLAY`）と `DeviceProfile.isTwoPanels` と同じ。グリッドが変わったときの移し替えは `GridSizeMigrationLogic` をアプリ側（`GridSizeMigration`）に書き写す（DB はグリッドごとに分けず、入りきらないものは新しいページの代わりに削除）。ドラッグ中の向きの固定は `Launcher.onStateSetStart` と同じ |
| TalkBack（ホーム全体） | コピー、削ってコピー | `accessibility/DragAndDropAccessibilityDelegate.java`、`AccessibleDragListenerAdapter.kt`、`FolderAccessibilityHelper.java`、`WidgetResizePopupDataSource.kt`、`anim/PropertyListBuilder.java`、リサイズのアイコン | `accessibility/BaseAccessibilityDelegate.java`（`Launcher` が Context でないので `asContext()`）、`LauncherAccessibilityDelegate.java`（アプリペア・ホームのファイル・自動化・キーボードからのドラッグを削る。1ページなので空きがなければ「空きスペースがありません」を出して追加しない。全アプリ一覧の `AppInfo` はホームが自分の項目にする `Launcher.makeWorkspaceItem`）、`WorkspaceAccessibilityHelper.java`、`DragViewStateAnnouncer.java`、`touch/ItemLongClickListener.java`（`beginDrag` だけ）、`UtilitiesKt.kt`（`isPersistedModelItem` だけ）を互換クラスにする（`derived=`）。`CellLayout`・`Workspace`・`Folder`・`FolderPagedView`・`FolderIcon`・`BubbleTextView`・`LauncherAppWidgetHostView`・`AppWidgetResizeFrameCompose`・`ButtonDropTarget`・`DeleteDropTarget`・`SecondaryDropTarget`・`DropTargetHandler`・`DragLayer`・`BaseDragLayer`・`LauncherState`・`AllAppsState` の互換クラスには、上流のアクセシビリティの部分（デリゲート、アクセシブルなドラッグ、読み上げ、ページと状態の説明）を戻す。ホームのページはスクロールの入れ物の子なので、アクセシブルなドラッグはその入れ物の `CellLayout` に付ける。`Workspace` はまだ状態のハンドラでないので、状態が決まるたびに `Launcher.onStateSetEnd` から `updateAccessibilityFlags` を呼ぶ。ドラッグレイヤーはホームの上に重ねた層なので、フォルダやメニューが開いている間にホームを隠す処理（`BaseDragLayer.addChildrenForAccessibility` など）は、アプリのルート（`LauncherRootLayout`）でもする。ウィンドウのタイトルと読み上げの題（`Launcher.setTitle`・`dispatchPopulateAccessibilityEvent`）は Activity が付ける |
| 「壁紙とスタイル」連携 | コピー、削ってコピー | `preview/PreviewLifecycleObserver.kt` | `graphics/GridCustomizationsProxy.java`（グリッドの選択肢・ラベルを隠す設定・静止画のプレビュー `get_preview_bitmap` を削り、アイコンの形とテーマアイコンはアプリの `IconSettings` に `Delegate` で任せる）と `preview/PreviewSurfaceRenderer.java`（Dagger の `PreviewContext` の代わりにディスプレイのウィンドウ Context、アプリのモデル、色はダークモードだけ）を削って互換クラスにする（`derived=`）。`LauncherPreviewRenderer` は名前とシグネチャだけの互換クラスで、ホームの見た目（キーボードを下ろしたときのホームのページと検索欄。キーボードは別アプリのウィンドウなので描かない）はアプリの `HomePreviewRenderer` が描く。`graphics/LauncherCustomizationProvider.kt` に倣ったアプリの `CustomizationProvider` が、呼び出し元を確かめて（`enforceCallerPermission`。`GRID_CONTROL` の権限の宣言と `grid_control_known_signers` も上流のまま）`GridCustomizationsProxy` に渡す |

コピーせずに参考にとどめたもの（依存が深いため）は次のとおり。

- `AppWidgetResizeFrame.kt`：`Launcher`、`Workspace`、本物の `CellLayout` に依存している
- `folder/FolderIcon`、`folder/Folder`、`folder/FolderPagedView`：`Launcher`、`Workspace`、`BubbleTextView`、名前の欄、データベースなどに依存している。開閉、ドラッグ、中での並べ替え、中身の出し入れ、ページ送り、アニメーション、プレビュー、ドロップが使うメンバーだけを残して削ってコピーした（`derived=`）
- `popup/PopupContainerWithArrow.kt`、`popup/PopupContainer.kt`、`popup/SystemShortcut.java`：`Launcher`、`LauncherState`、Compose の UI、アプリのショートカットの読み込みに依存している。メニューの行を出すのに使うメソッドだけを互換クラスに移した

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

### IntentResolver から持ってくるもの

「テキスト共有」の「追加」の一覧（docs/spec.md 2.7）を、Android の共有シート（`packages/modules/IntentResolver` の `ChooserActivity`）と同じ見た目と決まりにする。共有シートそのものは、選んだアプリを起動する作りで、Hilt・Kotlin の flow・システム API に深く依存しているので取り込まない。記録は `third_party/intentresolver/UPSTREAM.md`。

| 部品 | 取り込み方 | ファイル |
|---|---|---|
| グリッドのセル | コピー | `java/res/layout/chooser_grid_item_hover.xml`、`widget/ChooserTargetItemView.kt`（フォーカス枠）、`widget/BadgeTextView.kt`（▼ の印）、`chooser_group_background.xml`・`ic_chooser_group_arrow.xml`。使う値（属性・寸法・色）は `res-caretlauncher/` に抜き出す |
| 名前とアイコンの決め方 | 削ってコピー | `TargetPresentationGetter.java`（`derived=`。アイコンのビットマップを作る `SimpleIconFactory` はフレームワークの内部のリソースを使うので削り、Drawable のまま返す） |
| まとめたアプリの選択 | 削ってコピー、書き写す | `chooser_dialog.xml`・`chooser_dialog_item.xml`（`derived=`。フレームワークの非公開の id をモジュールの id に）。`ChooserStackedAppDialogFragment` と `ChooserTargetActionsDialogFragment` の中身は、Fragment の代わりにフレームワークの `Dialog` でアプリの `ShareTargetPicker` に書き写す（`ref=`） |
| まとめ方、ラベルの付け方、並び順 | 書き写す | `ChooserListAdapter` の `groupingBy` と `bindGroupIndicator`、`ResolverListAdapter.ViewHolder.bindLabel`、`DisplayResolveInfoAzInfoComparator` をアプリの `ShareTargetPicker` と `ShareTargetStore` に（`ref=`） |

### 参考にするもの

コードは取り込まない。ただし、仕組みの組み立て方や、閾値・バネの強さなどの値は Launcher3 に合わせる。

| 部品 | 参考にする元ファイル | 合わせる点 |
|---|---|---|
| 開閉のバネ | `anim/SpringAnimationBuilder.java`、`anim/FlingSpringAnim.java` | バネの強さと減衰、フリックの速度の引き継ぎ方 |
| 検索フィールドの振る舞い | `allapps/search/AllAppsSearchBarController.java` | 入力の監視、Enter で先頭の結果を起動、入力のクリア |

「一瞬引っ掛かってからクッと持ち上がる」動き（docs/spec.md 6.2）は Launcher3 にない（`AbstractStateChangeTouchController` の削ったコピーに印を付けて足した）。速度の判定とバネは Launcher3 に合わせ、引っ掛かりの部分だけを足す。

### 自前で書くもの

このランチャー独自の動きが中心で、取り込むメリットが小さいもの。上の「参考にするもの」に挙げた部品も、コード自体は自前で書く。

- 検索フィールド、常時キーボード、戻る操作の扱い
- 検索（正規化、照合、並び順、学習）
- テキスト共有（検索フィールドの共有ボタンと、それを選ぶダイアログ。docs/spec.md 2.7）。共有を受けるアプリの一覧は、下の「IntentResolver から持ってくるもの」の部品を組み立てる
- 全アプリ一覧（開閉とそのジェスチャーは上の削ったコピー）
- 縦スクロールするホームの入れ物（中のセルの並び替えは Launcher3 のロジックを使う）
- データの保存

アプリ側のホーム画面のクラスは、受け持つ役割の Launcher3 のクラスに合わせて分け、各ファイルの説明に対応するクラスを書く（`HomeController` が Launcher の役割のまとめ、`HomeActivityContext` が Launcher の互換クラスを継承した Launcher 本体、`HomeLoader`（LoaderTask など）、`HomeItemInflater`（ItemInflater と ItemClickHandler）、`HomeSystemShortcuts`（Launcher.getSupportedShortcuts）、`HomeWorkspace`（Workspace の互換クラスを継承）、`HomeScrollView`（ページの縦スクロール））。検索結果は `SearchResults`（ActivityAllAppsContainerView の検索部分）に置く。

ホームの Activity（`LauncherActivity`）は、Launcher3 の `BaseActivity` と同じくフレームワークの `Activity` を継承し、AppCompat と Material Components は使わない。テーマは `BaseLauncherTheme` と同じく `Theme.DeviceDefault.DayNight` を継ぎ、ダイアログは `android.app.AlertDialog`、色は dynamiccolors の `materialColor*`（システムの色）で取る。Compose（ウィジェットのリサイズ枠）に要るライフサイクルと保存状態は、`BaseActivity` と同じく `LifecycleHelper` で持つ。戻る操作は `registerBackDispatcher` と同じく `OnBackInvokedDispatcher` で受ける。AppCompat の View の置き換え（`TextView` を `MaterialTextView` に）は、ウィジェットの RemoteViews の表示にも効いて壊すため（Tex-uus1）。constraintlayout が AppCompat を推移的に持ち込むのは Launcher3 と同じで、使いはしない。

ホームの Activity は上流の `Launcher.java`（`StatefulActivity` のサブクラス）のコピーにしない（2026-11-25 に決定、Tex-zwo5）。上流の `Launcher` の処理のうちこのランチャーで意味のあるもの（ドラッグ、アクセシビリティ、ポップアップ、アイテムの追加とバインド、ウィジェット追加の流れ）は、Activity でない互換の `Launcher`（`compat/launcher3-internal`）に上流の順で `derived=` の区画として入れ、`HomeActivityContext` がそれを継ぐ。Activity に残るのは生存期間のつなぎと、上流にない検索フィールド・常時キーボード・検索結果の処理である。理由は次のとおり。

- `Launcher.java`（2,903 行）の残りの大部分は Activity の生存期間のつなぎ（Dagger の `LauncherComponent`、オーバーレイ、Quickstep のフック、回転、ページ、ホットシート）で、コピーにしても削除の印ばかりになり、上流の更新を当てる手間が上流に合わせる利点を上回る
- 上流は `DragLayer` をルートにしてキーボードをインセットで扱うが、このランチャーは `LauncherRootLayout` が「ドラッグレイヤーはキーボードの上まで」を受け持つ。キーボードの出入りのアニメーション中の配置はこのランチャーの土台で、作り直す危険が大きい
- `HomeController` が `Launcher` を作る今の持ち主の向きを逆にし、Kotlin から Java に移し、設定の変化を `recreate()` から `onIdpChanged` に変えることになり、直してきたドラッグや読み込みの競合が戻るおそれがある
- 得られるもの（互換の `addEventCallback`・`LauncherTracker`・`LauncherContextWrapper` がなくなる、予測型の戻る操作）は小さい。予測型の戻る操作が要る全アプリ一覧は、`AllAppsTransitionController.onBackProgressed`（Tex-ojza）で入る

---

## 置き場所と記録

- 取り込んだコードは `third_party/` に置き、自前のコードと混ぜない
  - `third_party/launcher3/`：Launcher3 から持ってきたファイル
  - `third_party/systemui/`：`frameworks/libs/systemui` から持ってきたファイル（`iconloaderlib` など）
  - `third_party/intentresolver/`：`packages/modules/IntentResolver`（共有シート）から持ってきたファイル。モジュール `:intentresolver`
  - 中のパス構成とパッケージ名は、元のリポジトリと同じにする。差分を 1:1 で比べられるようにするためである
- 各ディレクトリに `UPSTREAM.md` を置き、取り込んだファイルごとに次を記録する
  - 元のリポジトリとパス
  - 元にしたタグ
  - こちらで手を入れたかどうか
- Gradle では、`third_party/` 以下をライブラリモジュールとしてビルドする
- 互換層（代わりのクラス）は `compat/` に置き、どこから使われるかで分ける
  - `compat/launcher3/`：`third_party/systemui/`（`iconloaderlib`）からも使うもの。今は `Flags` と `UserType` だけ。`iconloaderlib` は `:launcher3` より下のモジュールなので、別モジュール `:launcher3-compat` にする
  - `compat/launcher3-internal/`：それ以外のすべて。`third_party/launcher3/` と同じ `:launcher3` モジュールに含めてビルドする。コピーしたクラスやリソース（`R`）を使えるようにするためである
  - `compat/intentresolver/`：`third_party/intentresolver/` の削ったコピー（`TargetPresentationGetter` とダイアログのレイアウト）。`:intentresolver` モジュールに含めてビルドする

## 手を入れるときのルール

- 取り込んだファイルは、できるだけ直接書き換えない
- Launcher3 の他の部分（`Launcher`、`DeviceProfile`、`LauncherPrefs`、機能フラグなど）への依存は、`third_party/` の外に置いた代わりのクラス（互換層）で受け止める
- 互換層では同じパッケージ名・クラス名で、使われる部分だけを実装する
- どうしても書き換えが必要な場合は、該当箇所に `// CaretLauncher:` で始まるコメントを付ける
- 上流のコードを持ってきた互換クラスは、上流のファイルから使わない部分を削って作り（`UPSTREAM.md` の `derived=`）、更新時に3方向マージできるようにする
  - 残す部分は上流の順・書き方（改行やインデントも）のまま。並べ替えたり書き直したりしない
  - 変えたところには、その場に `CaretLauncher:` の印を付ける（クラスの説明にまとめて書くだけにしない）
  - CaretLauncher だけのメンバーは、クラスの最後の `// CaretLauncher: members CaretLauncher adds.` の下にまとめる
  - 名前とシグネチャだけの互換クラスは `ref=` にして、上流の差分だけを見る
- 機能フラグ（`Flags.xxx()` など）は、基準タグのリリースでの値に固定する
  - 値は、AOSP の `build/release` の該当リリースの設定を参照する（`scripts/check-upstream.py` が `Flags` の互換クラスと `flag=` の行を突き合わせる）
  - 確認できないものは Pixel の実機の挙動に合わせる

## ライセンス

Launcher3 と `frameworks/libs/systemui` は Apache License 2.0 である。

- 取り込んだファイルのライセンスヘッダーは残す
- `third_party/` 以下に LICENSE と NOTICE を置く
- APK にも LICENSE と NOTICE を入れる（`assets/licenses/`。Apache License 2.0 の4節）。ビルドのたびに依存から作り直す（`app/build.gradle.kts` の `LicenseAssetsTask`）。設定の「アプリ情報」の「アプリ内のその他の設定」から開く画面（`AppSettingsActivity`）の「ライセンス」（`LicensesActivity`）で見られる
  - LICENSE は最上位の `LICENSE`（CaretLauncher 自身の Apache License 2.0。取り込んだ AOSP のコードとライブラリも同じライセンス）。NOTICE は最上位の `NOTICE`（CaretLauncher 自身）、`third_party/NOTICE`（取り込んだ AOSP のコード。ここだけ手で書く）、その版に入るライブラリの一覧と各 POM（なければ親の POM）が宣言するライセンス、ライブラリの Java リソースにある NOTICE ファイル（今は Jakarta Inject）の順
  - POM のライセンスが Apache License 2.0 でない（または書かれていない）ライブラリが入ると、ビルドが止まる。そのライブラリのライセンスを確かめ、LICENSE と NOTICE に何が要るかを決めてから入れる
- 書き換えたファイルには、変更したことを示す記載を入れる（Apache License 2.0 の条件）
- 互換層のクラスのうち、上流に同じ名前のファイルがあるものは、上流のライセンスヘッダーをそのまま付け、その下に「CaretLauncher のために変更した」旨のコメントを置く。中身が上流のコードか、名前とシグネチャだけかは問わない
  - 上流のファイルにヘッダーがないもの（`popup/SystemShortcut.java`、`accessibility/LauncherAccessibilityDelegate.java`）は、年を除いた AOSP の標準のヘッダーを、上流にヘッダーがない旨と一緒に付ける。`third_party/` の逐語コピー（`shortcuts/ShortcutKey.java`、`util/PackageUserKey.java` など）は、上流のままヘッダーなしで置く
  - `com.android.users.UserType` は `usertypelib` の `UserType.kt` から Parcelable を外したものなので、上流のヘッダーを付ける
- 上流にソースファイルがないもの（ビルドで生成される `BuildConfig`、aconfig の `Flags`、Dagger の `ActivityComponent` など）は、上流のコードを含まないのでヘッダーを付けない

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
4. `scripts/check-upstream.py <dir>`（タグを付けない）で、新しいタグとの食い違いがないことを確かめる。抜き出した値が上流と同じか、機能フラグの値がリリースと同じか、`ref=` のファイルが上流とこちらの両方にあるかを見る。わざと変えた値には、要素の直前に `CaretLauncher:` で始まるコメントを付ける
5. ビルドとテストを通し、エミュレータと実機で手触りを確かめる
6. 「壁紙とスタイル」連携は、Pixel の実機で項目が出て動くことを確かめる

`check-upstream.py` は上流を浅く clone して `UPSTREAM_CACHE`（既定は一時ディレクトリの `caretlauncher-upstream`）に取っておく。`UPSTREAM.md` のブロックに書く行の意味は、各 `UPSTREAM.md` に書いた。

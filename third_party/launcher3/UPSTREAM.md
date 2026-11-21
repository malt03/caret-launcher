# packages/apps/Launcher3 から取り込んだコード

方針: [docs/launcher3-porting.md](../../docs/launcher3-porting.md)

`scripts/update-upstream.sh` は、次のブロックを読んで更新する。パスは、このディレクトリと上流のリポジトリで同じ。ただし上流の `res/` は、ここでは `res-textlauncher/` に置く（`res_dir`）。`derived=` の行は、上流のファイルから使わない部分を削り、変更に `TextLauncher:` の印を付けて作った互換クラスで、同じく3方向マージで更新する（`PopupContainer` と `PopupContainerWithArrow` は上流から削っただけ。`ItemClickHandler` はアプリペア・ウィジェット・インストール中のアイコン・プライベートスペースの部分を削った。`IconCache` は Dagger・LoaderTask の一括読み込み・ホームのファイル・インストール中のセッション・ウィジェットの分類・アーカイブされたアプリ・インスタントアプリの部分を削った）。

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
derived=src/com/android/launcher3/touch/ItemClickHandler.java compat/launcher3-internal/com/android/launcher3/touch/ItemClickHandler.java
derived=src/com/android/launcher3/popup/PopupContainer.kt compat/launcher3-internal/com/android/launcher3/popup/PopupContainer.kt
derived=src/com/android/launcher3/popup/PopupContainerWithArrow.kt compat/launcher3-internal/com/android/launcher3/popup/PopupContainerWithArrow.kt
derived=src/com/android/launcher3/icons/IconCache.kt compat/launcher3-internal/com/android/launcher3/icons/IconCache.kt
path=res/color/drop_target_text.xml
path=res/color/popup_shade_first.xml
path=res/color/popup_shade_second.xml
path=res/color/popup_shade_third.xml
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
```

## 取り込んでいないもの

| 上流のパス | 理由 |
|---|---|
| `build.gradle.kts`、`AndroidManifest.xml` | 自前のもの（`build.gradle.kts`、`TextLauncherManifest.xml`）を置く |
| `res/` | 丸ごとは取り込まない。そのままコピーした `color/`・`drawable/`・`layout/` のファイルは上のブロックに載せ、`res-textlauncher/` に置く。コピーしたコードが使う値は抜き出して `res-textlauncher/` に置く（`values/strings.xml`、`values-ja/strings.xml` の `*_shape_title`、各言語の `values-*/strings.xml` の `app_info_drop_target_label`、`action_deep_shortcut`、`action_add_to_workspace`、`values/launcher3.xml` と `values-night/launcher3.xml` にまとめたフォルダ、ドラッグ、落とし先の枠、削除・アンインストールのボタン、長押しメニュー、キーボードのフォーカス枠用の attr・色・寸法・時間・スタイル）。抜き出した値は更新スクリプトの対象外なので、更新時は手で突き合わせる |

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
| `com.android.launcher3.util.Executors`（`MODEL_EXECUTOR` だけ） | `IconCache` のスレッド、長押しメニューのショートカットの問い合わせ（`PopupContainerWithArrow`）、`dragndrop/DragView.java` |
| `com.android.launcher3.Flags` | `iconloaderlib`（`third_party/systemui/`）、`shapes/ShapesProvider.kt`、ドラッグ、フォルダ、`keyboard/`（`enableFocusOutline` は bp1a で有効） |
| `com.android.launcher3.PagedView`（`isPageInTransition`、`getScrollForPage` だけを持つ抽象クラス。TextLauncher にページ送りの View はないので、継承するものはない） | `keyboard/ViewGroupFocusHelper.java` |
| `com.android.launcher3.LauncherSettings`、`BuildConfig`、`dagger/`、`logging/`、`accessibility.DragViewStateAnnouncer`、`graphics.PreloadIconDelegate`、`graphics.AutomatedIconDelegate`、`apppairs/`、`widget.LauncherAppWidgetHostView` | ドラッグと `folder/PreviewItemManager.java`。TextLauncher で起きない場合（システムのドラッグ、ウィジェット、App Pair、インストール中のアプリなど）は何もしないか、通らない |
| `com.android.launcher3.popup.PopupContainer`、`PopupContainerWithArrow`、`popup.PopupItemDragHandler`、`SystemShortcut`、`popup.PopupDataProvider`、`util.ApplicationInfoWrapper`、`util.ShortcutUtil`、`logging.FileLog`、`anim.PendingAnimation`、`compat.AccessibilityManagerCompat`、`com.android.wm.shell.Flags` | 長押しメニュー（`popup/ArrowPopup.java`、`AbstractFloatingView.java`、`popup/PopupPopulator.java`）。システムショートカットとアプリのショートカットの行を出す経路のメソッドを元のファイルから移した。ショートカット数は Launcher3 のモデルが裏で持つ代わりに、メニューを開くときに同じ条件で問い合わせる。Compose 版のメニュー（`Flags.expandableLongPressMenu` はリリース設定で無効）は持たない。`PopupItemDragHandler` はインターフェースだけを置き、`LauncherPopupItemDragHandler` はアプリ側（`ItemLongClickListener`）に移した。`shortcuts/DeepShortcutView.java` はそのままコピーし、追加ボタンとバッジ付きのコピーは `Launcher` の互換クラス（`getAccessibilityDelegate().addToWorkspace`、`getModel().updateAndBindWorkspaceItem`）を通してアプリが受ける。`BubbleTextView` は元と同じく `TextView` を継承し、`IconViewController` を実装する |
| `com.android.launcher3.util.MSDLPlayerWrapper`（Dagger の代わりに `INSTANCE.get(context)` でアプリに1つのプレイヤーを返す。履歴の dump は持たない） | ドラッグのハプティック（`CellLayout`、`HomeWorkspace`、`DropTargetBar`。`msdllib` は `third_party/systemui/msdllib/`） |
| `com.android.users.UserType` | `iconloaderlib`（`third_party/systemui/`） |

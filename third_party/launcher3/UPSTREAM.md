# packages/apps/Launcher3 から取り込んだコード

方針: [docs/launcher3-porting.md](../../docs/launcher3-porting.md)

`scripts/update-upstream.sh` は、次のブロックを読んで更新する。パスは、このディレクトリと上流のリポジトリで同じ。

```upstream
repo=https://android.googlesource.com/platform/packages/apps/Launcher3
tag=android-17.0.0_r1
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
```

## 取り込んでいないもの

| 上流のパス | 理由 |
|---|---|
| `build.gradle.kts`、`AndroidManifest.xml` | 自前のもの（`build.gradle.kts`、`TextLauncherManifest.xml`）を置く |
| `res/` | 丸ごとは取り込まない。コピーしたコードが使う文字列だけを `res-textlauncher/` に抜き出している（`values/strings.xml`、`values-ja/strings.xml` の `*_shape_title`、各言語の `values-*/strings.xml` の `app_info_drop_target_label`、`action_deep_shortcut`、`action_add_to_workspace`、`values/launcher3.xml` と `values-night/launcher3.xml` にまとめたフォルダ、ドラッグ、落とし先の枠、削除・アンインストールのボタン、長押しメニュー用の attr・色・寸法・時間・スタイル）と、そのままコピーした `drawable/round_rect_folder.xml`、`drawable/drop_target_background.xml`、`drawable/drop_target_frame.xml`、`drawable/drop_target_frame_hover.xml`、`drawable/ic_remove_no_shadow.xml`、`drawable/ic_uninstall_no_shadow.xml`、`color/drop_target_text.xml`。更新スクリプトの対象外なので、更新時は手で突き合わせる |

## 手を入れたファイル

なし。

## 互換層で受け止めている依存

`compat/launcher3/` にある。

| クラス | 使っているファイル |
|---|---|
| `com.android.launcher3.Utilities`（`mapRange`） | `anim/KeyboardInsetAnimationCallback.java` |
| `com.android.launcher3.CellLayout`（画面には置かない。アルゴリズム、並び替えの反映、落とし先の枠、委譲描画のメンバーを `CellLayout.java` から移した。子 View はホームのグリッドが `CellLayout.Children` として持つ） | `celllayout/ReorderAlgorithm.java`、`celllayout/ViewCluster.kt`、`celllayout/ReorderPreviewAnimation.kt`、`folder/PreviewBackground.java` |
| `com.android.launcher3.model.data.ItemInfo`、`ItemInfoWithIcon`、`WorkspaceItemInfo`、`FolderInfo`、`AppPairInfo` | `util/GridOccupancy.java`、ドラッグ、`folder/PreviewItemManager.java`。`ItemInfoWithIcon.newIcon` は元の処理を写した |
| `com.android.launcher3.LauncherAnimUtils`（`VIEW_TRANSLATE_X`、`VIEW_TRANSLATE_Y`） | `util/MultiTranslateDelegate.java` |
| `com.android.launcher3.Workspace`（`REORDER_TIMEOUT`） | `celllayout/ReorderPreviewAnimation.kt` |
| `com.android.launcher3.util.window.RefreshRateTracker`（`getSingleFrameMs`。ディスプレイのリフレッシュレートから計算） | `anim/SpringAnimationBuilder.java` |
| `com.android.launcher3.folder.Folder`、`FolderIcon`、`FolderPagedView`、`LauncherDelegate` | フォルダのアニメーション、プレビュー、ドロップ（`folder/` 以下）。アプリ側の `OpenFolder`、`FolderIconView` がこれらを継承する。1ページだけで、名前とフッターは持たない。`FolderIcon` のプレビュー描画、`onDrop`、`performCreateAnimation` などは `FolderIcon.java` から移した。`FolderIcon` が `Folder` に対して呼ぶ処理は `FolderIcon.FolderContent` が受ける |
| `com.android.launcher3.BubbleTextView`、`apppairs.AppPairIcon` | フォルダのアニメーション。アプリ側の `AppIconView` が `BubbleTextView` を継承する。ラベルがないので文字の透明度は何もしない |
| `com.android.launcher3.Launcher`、`Workspace`、`Hotseat`、`views.ScrimView` | フォルダの背景の暗転と縮小。`Workspace` はホームのグリッドを、`Hotseat` は検索フィールドを包む |
| `com.android.launcher3.views.BaseDragLayer`、`views.ActivityContext`、`DeviceProfile` | フォルダの配置と寸法、ドラッグ、長押しメニュー。`BaseDragLayer` は元と同じく `InsettableFrameLayout` を継承し、タッチコントローラーの振り分け（開いている `AbstractFloatingView` を先に、次にドラッグ）を `BaseDragLayer.java` から移した。ホームの上に重ねた層なので、タッチは親から渡してもらう。ほかに `DragLayer` のドロップアニメーション（`animateViewIntoPosition`、`animateView`）を移した。ホームの上に重ねた層なので、層の外の View は親を通して座標を変換する。`DeviceProfile` はフォルダの列数・行数とアイコンサイズだけ |
| `com.android.launcher3.graphics.ThemeManager` | アイコンのテーマの有無と形状（アプリのアイコン読み込みが設定する）、フォルダの形。フォルダの形は選んだアイコン形状の `folderRadiusRatio` から作る（`ThemeManager.parseIconState` と同じ） |
| `com.android.launcher3.ShortcutAndWidgetContainer`、`util.Themes`、`model.data.FolderInfo` | フォルダのアニメーションと `FolderGridOrganizer` |
| `com.android.launcher3.icons.IconCache`、`LauncherAppState` | `folder/PreviewItemManager.java`、`popup/PopupPopulator.java`。アプリのショートカットのアイコンは、アプリのアイコン読み込みが渡す関数で作る（キャッシュしない） |
| `com.android.launcher3.Flags` | `iconloaderlib`（`third_party/systemui/`）、`shapes/ShapesProvider.kt`、ドラッグ、フォルダ |
| `com.android.launcher3.LauncherSettings`、`BuildConfig`、`dagger/`、`logging/`、`accessibility.DragViewStateAnnouncer`、`graphics.PreloadIconDelegate`、`graphics.AutomatedIconDelegate`、`apppairs/`、`widget.LauncherAppWidgetHostView`、`util.Executors` | ドラッグと `folder/PreviewItemManager.java`。TextLauncher で起きない場合（システムのドラッグ、ウィジェット、App Pair、インストール中のアプリなど）は何もしないか、通らない |
| `com.android.launcher3.popup.PopupContainer`、`PopupContainerWithArrow`、`SystemShortcut`、`shortcuts.DeepShortcutView`、`shortcuts.ShortcutKey`、`popup.PopupDataProvider`、`util.ApplicationInfoWrapper`、`util.ShortcutUtil`、`logging.FileLog`、`dragndrop.DragLayer`、`anim.PendingAnimation`、`compat.AccessibilityManagerCompat`、`com.android.wm.shell.Flags` | 長押しメニュー（`popup/ArrowPopup.java`、`AbstractFloatingView.java`、`popup/PopupPopulator.java`）。システムショートカットとアプリのショートカットの行を出す経路のメソッドを元のファイルから移した。ショートカット数は Launcher3 のモデルが裏で持つ代わりに、メニューを開くときに同じ条件で問い合わせる。Compose 版のメニュー（`Flags.expandableLongPressMenu` はリリース設定で無効）、ホームへの固定とアプリのショートカットのドラッグは持たない。`BubbleTextView` は元と同じく `TextView` を継承し、`IconViewController` を実装する |
| `com.android.launcher3.dragndrop.DragLayerAccess` | `BaseDragLayer` の互換クラスから、`DragController` のパッケージ内メソッド（`onDeferredEndDrag`）を呼ぶため |
| `com.android.users.UserType` | `iconloaderlib`（`third_party/systemui/`） |

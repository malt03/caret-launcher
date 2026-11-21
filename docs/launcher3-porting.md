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
| アイコンのキャッシュ | 削ってコピー | `icons/CacheableShortcutInfo.kt`、`util/CancellableTask.kt` | `icons/IconCache.kt` から、Dagger・LoaderTask の一括読み込み・ホームのファイル・インストール中のセッション・ウィジェットの分類・アーカイブされたアプリ・インスタントアプリの部分を削って互換クラスにする（`derived=`）。キャッシュの本体（`BaseIconCache`、`IconCacheUpdateHandler` など）は iconloaderlib のまま。スレッドは上流と同じく `MODEL_EXECUTOR`。`LauncherIcons` は互換クラスにし、密度・大きさ・テーマはアプリが渡す。アプリのアイコン読み込み（`AppIconLoader`）が、検索結果は `DEFAULT_LOOKUP_FLAG`、ホームは `DESKTOP_ICON_FLAG` で引き、起動後の最初の表示のあとに `LoaderTask.loadAllSurfacesOrdered` と同じく DB を更新し、パッケージの変化で `PackageUpdatedTask`、テーマの切り替えで `ModelInitializer.refreshAndReloadLauncher` と同じことをする。ホームの DB は上流の `favorites` と同じくアプリのショートカットのアイコンを持ち（`ContentWriter.putIcon`、既定のアイコンは持たない）、問い合わせられないときはそれを出す（`IconRequestInfo.parseIconBlob`） |
| 並び替え（押しのけ、空き探し） | アルゴリズムと反映をコピー | `celllayout/ReorderAlgorithm.java`、`celllayout/ItemConfiguration.kt`、`celllayout/ViewCluster.kt`、`celllayout/ReorderParameters.kt`、`celllayout/CellLayoutLayoutParams.java`、`celllayout/ReorderPreviewAnimation.kt`、`util/GridOccupancy.java`、`util/CellAndSpan.java`、`InterruptibleInOutAnimator.java` | `CellLayout` は画面に置かない互換クラスにし、アルゴリズム、結果の反映（`performReorder`、`animateItemsToSolution`、`revertTempState` など）、落とし先の枠（`visualizeDropLocation`）を `CellLayout.java` から移す。子 View の並べ方（`ShortcutAndWidgetContainer`）はホームのグリッドが受け持つ |
| ドラッグ | コピー | `dragndrop/DragController.java`、`dragndrop/DragView.java`、`dragndrop/DragDriver.java`、`dragndrop/DragOptions.java`、`dragndrop/DraggableView.java`、`DropTarget.java`、`DragSource.java`、`graphics/DragPreviewProvider.java`、`util/TouchController.java`、`Alarm.java` など | `LauncherDragController` はアプリ側の `HomeDragController` に、`Workspace` のドラッグ部分（`onDragOver`、`onDrop`、フォルダ作成など）は `HomeWorkspace` に、`ButtonDropTarget`・`DeleteDropTarget`・`SecondaryDropTarget`・`DropTargetBar` は `DropTargetBar` にに、同じ名前とロジックのまま書き写す。`Folder` のドラッグ元の部分は `Folder` の互換クラスに移す。`DragLayer` のドロップアニメーションは `DragLayer` の互換クラスに移す |
| 長押しメニューとドラッグへの移行 | コピー | `popup/ArrowPopup.java`、`popup/RoundedArrowDrawable.java`、`popup/Popup.kt`、`popup/IconViewController.kt`、`AbstractFloatingView.java`、`InsettableFrameLayout.java`、`anim/AnimatedFloat.java`、`popup/PopupPopulator.java`、`shortcuts/ShortcutRequest.java`、`shortcuts/DeepShortcutTextView.java`、`shortcuts/ShortcutDragPreviewProvider.java`、メニューのレイアウトとドロワブル | `PopupContainer`、`PopupContainerWithArrow`、`SystemShortcut`（`AppInfo` を含む）は互換クラスにし、システムショートカットとアプリのショートカットの行を出す経路のメソッドを移す（Compose 版は除く）。`shortcuts/DeepShortcutView.java` はそのままコピーし、追加ボタンとバッジ付きのコピーは `Launcher` の互換クラスを通してアプリが受ける。ショートカットをメニューからドラッグする `LauncherPopupItemDragHandler` はアプリ側に移し、`Workspace.beginDragShared` の移植に渡す。追加ボタンは `LauncherAccessibilityDelegate.addToWorkspace` に合わせて最初の空きセルに置き、`Launcher.bindInflatedItems` と同じバウンスで出す。ホームのショートカットのシステムへの固定は `BgDataModel.updateShortcutPinnedState` を移す。アプリのショートカットのアイコンは `IconCache` の `getShortcutIcon` で作る（バッジも付く）。「検索文字列を編集」はアプリ側で `SystemShortcut` を継承して書く。ドラッグへの移行は `PopupContainer.createPreDragCondition` をそのまま使う |
| ウィジェットのリサイズ枠 | 自前（参考） | — | `AppWidgetResizeFrame.kt` に倣う。`applyDeltaAndBound`、`getSpanIncrement` の計算は移植する |
| フォルダ | コピー | `folder/` の開閉アニメーション一式（`FolderAnimationSpringBuilderManager.kt`、`FolderSpringAnimatorSet.kt`、`FolderAnimationData.kt`、`ClipRevealData.kt`、`IconAnimationData.kt` など）、`folder/PreviewItemManager.java`、`folder/FolderPreviewItemAnim.java`、`folder/PreviewBackground.java`、`folder/ClippedFolderIconLayoutRule.java`、`folder/FolderGridOrganizer.java`、`graphics/ShapeDelegate.kt` | `Folder`、`FolderIcon`、`FolderPagedView`、`BubbleTextView`、`Launcher` などは互換クラスにし、アプリ側の View がそれを継承する。`Folder` は上流と同じく `AbstractFloatingView` で、開閉、外側のタッチ、ドラッグ元、中身の出し入れを `Folder.java` から移す。`FolderIcon` のプレビュー描画、ドロップ、作成アニメーションのメソッドは `FolderIcon.java` から互換クラスに移す |
| 全アプリ一覧と見出しに飛ぶスクロールバー | コピー、削ってコピー | `views/RecyclerViewFastScroller.java`、`graphics/FastScrollThumbDrawable.java`、`FastScrollRecyclerView.java`、`allapps/AllAppsFastScrollHelper.java`、`allapps/LetterListTextView.java`、`allapps/AllAppsGridAdapter.java`、`allapps/AppInfoComparator.java`、`allapps/search/SearchAdapterProvider.java`、`allapps/search/DefaultSearchAdapterProvider.java`、`compat/AlphabeticIndexCompat.java`、`util/LabelComparator.java`、`util/ScrollableLayoutManager.java` | `allapps/AlphabeticalAppsList.java`、`allapps/BaseAllAppsAdapter.java`、`allapps/AllAppsRecyclerView.java` から、仕事用プロファイル・プライベートスペース・記録・文字の一覧（`Flags.letterFastScroller` はリリース設定で無効）の部分を削って互換クラスにする（`derived=`）。`AllAppsStore` は互換クラス。`ActivityAllAppsContainerView` はアプリ側（`AllAppsView`）に、`AdapterHolder.setup` とスクロールバーへのタッチの振り分けを移す。アプリの一覧は `AllAppsList` と同じく `AppInfo` で持ち、見出しは `AlphabeticIndexCompat` で付ける。アイコンは `BubbleTextView.verifyHighRes`（`IconCache.updateIconInBackground`）も上流どおり。セルはアプリが作る（`all_apps_icon.xml` の代わり。検索結果と同じ見た目） |
| 縦スワイプの判定、端での引っ張り | コピー | `touch/BaseSwipeDetector.java`、`touch/SingleAxisSwipeDetector.java`、`touch/OverScroll.java`、`MotionEventsUtils.java` | 全アプリ一覧の開閉（`AllAppsSwipeController`）が使う。引っ掛かりは `OverScroll.dampedScroll` で付ける。`util/TranslateEdgeEffect.java`、`util/EdgeEffectCompat.java` は使う場面がないので取り込んでいない |
| ハプティック | ライブラリごとコピー | `frameworks/libs/systemui/msdllib/` | `util/MSDLPlayerWrapper.java` の代わりを互換層に置く。鳴らす場所と種類は Launcher3 に合わせる（下記） |
| キーボードの出入りへの追従 | コピー | `anim/KeyboardInsetAnimationCallback.java` | — |
| キーボードのフォーカス枠 | コピー | `keyboard/ItemFocusIndicatorHelper.java`、`keyboard/FocusIndicatorHelper.java`、`keyboard/FocusedItemDecorator.java`、`keyboard/ViewGroupFocusHelper.java` | 検索結果への付け方は `ActivityAllAppsContainerView.AdapterHolder.setup` と `BaseAllAppsAdapter.setIconFocusListener` に、ホームは `DragLayer` と `ItemInflater` に（枠はスクロールするグリッドに描く）、フォルダは `FolderPagedView` と `Folder` のフォーカス処理（開いたらフォルダにフォーカス、中だけを探す、閉じたらアイコンに戻す）に、文字を打ったら検索フィールドに戻す処理は `AppsSearchContainerLayout.preDispatchKeyEvent` に倣う。入力が変わったら1位に枠を付ける処理は Pixel Launcher に合わせた自前のもの（docs/spec.md 2.6）。`PagedView` は名前だけの互換クラス |
| タップ | 削ってコピー | — | `touch/ItemClickHandler.java` から、アプリペア・ウィジェット・インストール中のアイコン・プライベートスペースの部分を削って互換クラスにする（`derived=`）。無効な項目（作成元が無効にしたショートカット、停止中のアプリなど）のタップも上流と同じ。起動そのものは `Launcher.startActivitySafely` をアプリが実装する。検索結果のタップは、学習の記録のためアプリ側で受ける |
| ウィジェットピッカー | 要調査 | — | OS 標準の選択画面（`AppWidgetManager.ACTION_APPWIDGET_PICK`）と比べて決める（Tex-h3ld） |
| グリッドの寸法 | 値を参照 | `res/xml/device_profiles.xml` | — |
| 「壁紙とスタイル」連携 | 取り決めだけ合わせる | — | `graphics/GridCustomizationsProxy.java`、`graphics/LauncherCustomizationProvider.kt` に倣って自前で書く |

コピーせずに参考にとどめたもの（依存が深いため）は次のとおり。

- `CellLayout.java` 本体、`dragndrop/DragLayer.java`、`Workspace.java`、`dragndrop/LauncherDragController.java`、`ButtonDropTarget`、`DeleteDropTarget`、`SecondaryDropTarget`、`DropTargetBar`：`Launcher`、`LauncherState`、`DeviceProfile`、Dagger に依存している。必要なメソッドだけを、名前とロジックを保って互換クラスかアプリ側に書き写した
- `AppWidgetResizeFrame.kt`：`Launcher`、`Workspace`、本物の `CellLayout` に依存している
- `folder/FolderIcon`、`folder/Folder`、`folder/FolderPagedView`：`Launcher`、`Workspace`、`BubbleTextView`、名前の欄、ページ、データベースなどに依存している。開閉、ドラッグ、中身の出し入れ、アニメーション、プレビュー、ドロップが使うメンバーだけを互換クラスに移した
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
| 全アプリ一覧の開閉 | `allapps/AllAppsTransitionController.java`、`allapps/ActivityAllAppsContainerView.java`、`quickstep/.../BaseDepthControllerImpl.java` | 0〜1 の進み具合で、シートの位置、ホームの縮小（`workspace_content_scale` を `ALL_APPS_SHEET_DEPTH` で）、奥行き（`getBottomSheetDepth`、`mapDepthToBlur`、`max_depth_blur_radius_enhanced`）を連動させる。一覧は `Flags.allAppsSheetForHandheld`（bp3a で有効）のハンドル付きボトムシート（`all_apps_bottom_sheet_background.xml`）で、色はぼかしの有無で `getBackgroundColor` と同じに切り替える（`WindowBlurState` の代わりに `CrossWindowBlurListeners`）。上流はランチャーの surface に背景のぼかしの半径を設定するが、アプリからはできないので、壁紙はウィンドウの `Window.setBackgroundBlurRadius`（フレームごとに描かれる。そのためウィンドウを `windowIsTranslucent` にする）で、ウィンドウ内のホームは `RenderEffect` でぼかす。`FLAG_BLUR_BEHIND` はウィンドウマネージャーを通して非同期に変わるので、指の動きに追いつかない。シートの色は Pixel Launcher の値（フレームワークの `shade_panel_fg`／`shade_panel_bg`。ダークテーマで変わる）にした。検索フィールドはシートの外（TextLauncher の配置）。ジェスチャーなしの開閉は `config_allAppsOpenDuration`（600ms、EMPHASIZED）と 200ms（ACCELERATE） |
| 開閉のジェスチャー | `touch/AllAppsSwipeController.java`、`touch/AbstractStateChangeTouchController.java` | 指の動きから進み具合への変換、離したときの速度による開く/戻るの判定（フリックならその向き、そうでなければ半分を越えたか）、`BaseSwipeDetector.calculateDuration` と `scrollInterpolatorForVelocity` での残りのアニメーション、閾値のハプティック（MSDL `SWIPE_THRESHOLD_INDICATOR`） |
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
- 機能フラグ（`Flags.xxx()` など）は、基準タグのリリースでの値に固定する
  - 値は、AOSP の `build/release` の該当リリースの設定を参照する
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

1. `UPSTREAM.md` に記録したファイルごとに、次の3つを用意する
   - 前回のタグの元ファイル（base）
   - 新しいタグの元ファイル（theirs）
   - 今のこちらのファイル（ours）
2. `git merge-file ours base theirs` で、上流の変更をこちらに取り込む。`UPSTREAM.md` の `derived=` に挙げた互換クラス（上流のファイルから削って作ったもの）も同じくマージする。削った部分で上流が変わると衝突として出るので、取り込むかどうかを判断する
3. 衝突した箇所を手で直す
4. 新しく必要になった依存は互換層に足し、機能フラグの値も新しいリリースに合わせ直す
5. ビルドとテストを通し、エミュレータと実機で手触りを確かめる
6. 「壁紙とスタイル」連携は、Pixel の実機で項目が出て動くことを確かめる
7. `UPSTREAM.md` のタグを更新する

手順 1〜2 はスクリプトにして自動化する。

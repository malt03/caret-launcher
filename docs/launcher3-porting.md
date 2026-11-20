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
| 並び替え（押しのけ、空き探し） | アルゴリズムだけコピー | `celllayout/ReorderAlgorithm.java`、`celllayout/ItemConfiguration.kt`、`celllayout/ViewCluster.kt`、`celllayout/ReorderParameters.kt`、`celllayout/CellLayoutLayoutParams.java`、`util/GridOccupancy.java`、`util/CellAndSpan.java` | `CellLayout` は View ではない互換クラスにし、アルゴリズムが使うメンバー（`findNearestArea` 系、`cellToRect` など）だけを `CellLayout.java` から移す。結果をグリッドに反映する処理（`performReorder`、`animateItemsToSolution` など）は `CellLayout.java` に倣って自前で書く |
| ドラッグ | 土台をコピー | `dragndrop/DragDriver.java`、`dragndrop/DragOptions.java`、`DropTarget.java`、`DragSource.java`、`dragndrop/DraggableView.java`、`util/TouchController.java`。余力があれば `dragndrop/DragController.java`、`dragndrop/DragView.java` も | ドラッグレイヤーと削除・アンインストールの落とし先は、`ButtonDropTarget` の閾値とアニメーションに倣って自前で書く |
| 長押しメニューとドラッグへの移行 | 部品をコピー | `popup/RoundedArrowDrawable.java`、`popup/Popup.kt`、必要なら `popup/ArrowPopup.java` | メニュー本体は自前。ドラッグへの移行は `DragController` の pre-drag の仕組み（`DragOptions.preDragCondition`）を使う |
| ウィジェットのリサイズ枠 | 自前（参考） | — | `AppWidgetResizeFrame.kt` に倣う。`applyDeltaAndBound`、`getSpanIncrement` の計算は移植する |
| フォルダ | コピー | `folder/` の開閉アニメーション一式（`FolderAnimationSpringBuilderManager.kt`、`FolderSpringAnimatorSet.kt`、`FolderAnimationData.kt`、`ClipRevealData.kt`、`IconAnimationData.kt` など）、`folder/PreviewBackground.java`、`folder/ClippedFolderIconLayoutRule.java`、`folder/FolderGridOrganizer.java`、`graphics/ShapeDelegate.kt` | `Folder`、`FolderIcon`、`BubbleTextView`、`Launcher` などは互換クラスにし、アプリ側の View がそれを継承する |
| 見出しに飛ぶスクロールバー | スクロールバーをコピー | `views/RecyclerViewFastScroller.java`、`graphics/FastScrollThumbDrawable.java`、`FastScrollRecyclerView.java`、`allapps/AllAppsFastScrollHelper.java` | 見出しの区切りの作り方は `AlphabeticalAppsList.java` に倣って自前で書く |
| 縦スワイプの判定、端での引っ張り | コピー | `touch/BaseSwipeDetector.java`、`touch/SingleAxisSwipeDetector.java`、`touch/OverScroll.java`、`util/TranslateEdgeEffect.java`、`util/EdgeEffectCompat.java`、`MotionEventsUtils.java` | — |
| ハプティック | ライブラリごとコピー | `frameworks/libs/systemui/msdllib/` | `util/MSDLPlayerWrapper.java` の代わりを互換層に置く。鳴らす場所と種類は Launcher3 に合わせる（下記） |
| キーボードの出入りへの追従 | コピー | `anim/KeyboardInsetAnimationCallback.java` | — |
| ウィジェットピッカー | 要調査 | — | OS 標準の選択画面（`AppWidgetManager.ACTION_APPWIDGET_PICK`）と比べて決める（Tex-h3ld） |
| グリッドの寸法 | 値を参照 | `res/xml/device_profiles.xml` | — |
| 「壁紙とスタイル」連携 | 取り決めだけ合わせる | — | `graphics/GridCustomizationsProxy.java`、`graphics/LauncherCustomizationProvider.kt` に倣って自前で書く |

コピーせずに参考にとどめたもの（依存が深いため）は次のとおり。

- `CellLayout.java` 本体、`dragndrop/DragLayer.java`、`ButtonDropTarget`、`DeleteDropTarget`、`DropTargetBar`：`Launcher`、`Workspace`、`DeviceProfile`、Dagger に依存している
- `AppWidgetResizeFrame.kt`：`Launcher`、`Workspace`、本物の `CellLayout` に依存している
- `folder/FolderIcon`、`folder/Folder`、`folder/PreviewItemManager`：`Launcher`、`Workspace`、`BubbleTextView` などに依存している。アニメーションが使うメンバーだけを互換クラスで用意した
- `popup/PopupContainerWithArrow.kt`、`popup/PopupContainer.kt`、`touch/ItemLongClickListener.java`：`Launcher`、`LauncherState`、Compose の UI に依存している
- `allapps/AlphabeticalAppsList.java`：`AllAppsStore`、仕事用プロファイルなどの管理クラスに依存している

### ハプティックを鳴らす場所

Launcher3 に合わせる。`msdllib` を使うものは、Launcher3 と同じく `Flags.msdlFeedback()` で切り替える。

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
| 全アプリ一覧の開閉 | `allapps/AllAppsTransitionController.java` | 0〜1 の進み具合で、パネルの位置、背景の暗さ（スクリム）、ホームの薄れ方を連動させる組み立て方 |
| 開閉のジェスチャー | `touch/AllAppsSwipeController.java`、`touch/AbstractStateChangeTouchController.java` | 指の動きから進み具合への変換、離したときの速度による開く/戻るの判定、フリックの閾値 |
| 開閉のバネ | `anim/SpringAnimationBuilder.java`、`anim/FlingSpringAnim.java` | バネの強さと減衰、フリックの速度の引き継ぎ方 |
| 検索フィールドの振る舞い | `allapps/search/AllAppsSearchBarController.java` | 入力の監視、Enter で先頭の結果を起動、入力のクリア |
| 一覧と検索結果の切り替え | `allapps/SearchTransitionController.java` | ホームと検索結果の切り替え、一覧内の検索に入るときのアニメーション |

「一瞬引っ掛かってからクッと持ち上がる」動き（docs/spec.md 6.2）は Launcher3 にない。速度の判定とバネは Launcher3 に合わせ、引っ掛かりの部分だけを足す。

### 自前で書くもの

このランチャー独自の動きが中心で、取り込むメリットが小さいもの。上の「参考にするもの」に挙げた部品も、コード自体は自前で書く。

- 検索フィールド、常時キーボード、戻る操作の扱い
- 検索（正規化、照合、並び順、学習）
- 全アプリ一覧と、その開閉ジェスチャー
- 縦スクロールするホームの入れ物（中のセルの並び替えは Launcher3 のロジックを使う）
- データの保存

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

---

## 更新の手順

新しいリリースのタグが出たときの手順。

1. `UPSTREAM.md` に記録したファイルごとに、次の3つを用意する
   - 前回のタグの元ファイル（base）
   - 新しいタグの元ファイル（theirs）
   - 今のこちらのファイル（ours）
2. `git merge-file ours base theirs` で、上流の変更をこちらに取り込む
3. 衝突した箇所を手で直す
4. 新しく必要になった依存は互換層に足し、機能フラグの値も新しいリリースに合わせ直す
5. ビルドとテストを通し、エミュレータと実機で手触りを確かめる
6. 「壁紙とスタイル」連携は、Pixel の実機で項目が出て動くことを確かめる
7. `UPSTREAM.md` のタグを更新する

手順 1〜2 はスクリプトにして自動化する。

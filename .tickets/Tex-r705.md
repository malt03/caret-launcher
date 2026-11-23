---
id: Tex-r705
status: closed
deps: [Tex-t2ea]
links: []
created: 2026-11-22T15:43:20Z
type: feature
priority: 3
assignee: Koji Murata
parent: Tex-ogmk
tags: [home, launcher3]
---
# Launcher3 のウィジェットピッカー（Compose 版）を移植する

Tex-h3ld の調査結果に基づく。今は OS 標準の選択画面（ACTION_APPWIDGET_PICK）を使っている。android-17.0.0_r1 のピッカーは Compose 版だけで、modules/widgetpicker（65 ファイル・約 9,800 行）と Launcher 側のつなぎ（23 ファイル・約 2,700 行）からなり、Dagger で組み立てる。移植には Compose（Material3）を依存に足し、Dagger を手で組み直し、プレビューの読み込みも持ってくる。ピッカーからドラッグして置く操作もここで入れる。


## 調査（2026-11-23）

android-17.0.0_r1 の流れ:
- 空き領域のメニューの「ウィジェット」（WorkspaceLongPressOptions.openWidgetPicker）は、自分のパッケージ宛ての Intent.ACTION_PICK で WidgetPickerActivity（別の Activity、署名の権限付き）を開く。ランチャーは隠れるので、メニューは onSaveInstanceState で閉じる
- WidgetPickerActivity は WidgetPickerComposeWrapperImpl.showAllWidgets で ComposeView にピッカーを出す
- 「追加」を押すと、ACTIVITY_TRACKER に WidgetPickerAddItemListener を登録してホームを開き、自分は終わる。ホームが出たら LauncherAccessibilityDelegate.addToWorkspace(PendingAddWidgetInfo) で空きセルに置く（バインド、必要なら ACTION_APPWIDGET_BIND の許可、設定画面）
- ドラッグは WidgetPickerDragItemListener（BaseItemDragListener、システムのドラッグ）でホームに渡す

コードの量と依存:
- modules/widgetpicker（65 ファイル・約 9,800 行）は、Launcher3 の他のコードに依存しない。外への依存は Compose（material3、activity-compose、window、material3-window-size-class、icons-extended）と Dagger（@Inject、@AssistedInject、@Subcomponent など）だけ。上流にも独立した build.gradle.kts がある
- Launcher 側のつなぎ（src/.../widgetpicker と widget/picker、23 ファイル・約 2,700 行）は、WidgetsModel と WidgetItem（ウィジェットの一覧）、DatabaseWidgetPreviewLoader（プレビュー）、IconCache、PendingAddWidgetInfo、ShortcutConfigActivityInfo（ショートカットの作成画面も一覧に出る）、BaseItemDragListener とシステムのドラッグ、ContextTracker、SimpleDragLayer、SystemUiController などに依存する

## Notes

**2026-11-23T06:16:03Z**

ピッカーを開いて「追加」でホームに置くところまで移した（Dagger は KSP で生成、ルートは LauncherAppComponent + LauncherWidgetPickerModule + TextLauncherAppModule）。TextLauncher は BIND_APPWIDGET を持たないので OS の確認画面が出ることがあり、ピッカーが閉じるときのホームへの切り替え（singleTask）で消えないよう、追加はホームが resume してから始める。エミュレータ（Pixel9ProFold）で、ピッカーの表示・分類（会話）の名前とアイコン・確認画面・設定画面・配置・削除を確かめた。ピッカーからのドラッグ、ショートカットの作成、ピン留めの要求は別チケットに分けた。

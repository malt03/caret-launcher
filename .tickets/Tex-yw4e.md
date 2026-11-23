---
id: Tex-yw4e
status: closed
deps: []
links: []
created: 2026-11-22T16:04:35Z
type: task
priority: 2
assignee: Koji Murata
parent: Tex-ogmk
tags: [home, launcher3]
---
# ウィジェットのリサイズ枠

ホームのウィジェットをリサイズできるようにする（docs/spec.md 5.2、5.3）。Launcher3 の AppWidgetResizeFrame.kt に倣い、applyDeltaAndBound と getSpanIncrement の計算は移植する（docs/launcher3-porting.md の「ウィジェットのリサイズ枠」）。置いた直後とドラッグで落とした後に枠を出す（Workspace.getWidgetResizeFrameRunnable、Launcher.showWidgetResizeFrame）。リサイズしたら WidgetSizeHandler.updateSizeRanges で大きさを伝える。最小と最大のスパンは LauncherAppWidgetProviderInfo.initSpans（HomeWidgetSizes.initSpans）から。


## 結果

Android 17 のリリース設定では Flags.fixWidgetSinglePtrResize が有効で、Launcher3 は Compose 版の枠（AppWidgetResizeFrameCompose）を出すため、View 版ではなく Compose 版を移植した（ユーザーと相談して決定）。launcher3 モジュールに Jetpack Compose を入れ、ResizeFrame.kt と ResizeFrameViewModel.kt はそのままコピー、ResizeManager・AppWidgetResizeFrameCompose・AppWidgetResizeFrame（IntRange と showForWidget）は互換クラス。Flags.homeScreenEditImprovements も有効なので、ウィジェットの長押しは PopupControllerForExtraHomeScreenItems と同じくメニュー（削除・設定）と枠を出し、指を動かすとドラッグに移る。枠が出ている間は編集モード（キーボードを下ろす、docs/spec.md 5.4）。

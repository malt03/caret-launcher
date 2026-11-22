---
id: Tex-yw4e
status: open
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


---
id: Tex-44r9
status: open
deps: []
links: [Tex-4cve]
created: 2026-11-23T04:37:46Z
type: feature
priority: 3
assignee: Koji Murata
parent: Tex-ogmk
tags: [home, launcher3]
---
# ウィジェットの移植で省いた部分（ドラッグ中の縮小、アクセシビリティの操作）

ウィジェットの移植（Tex-t2ea、Tex-yw4e）で、次の部分は持ってきていない。
- ドラッグ中の縮小：LauncherDragController.createDragView の DRAGGABLE_WIDGET の scalePx と WidgetDragScaleUtils.getWidgetDragScalePx、Workspace.startDrag の preDragEndScale（Flags.homeScreenEditImprovements）。今はウィジェットが元の大きさのまま動く
- アクセシビリティ：リサイズ枠に付ける LauncherAccessibilityDelegate（TalkBack からのリサイズ・移動の操作）。枠の Compose 部分の semantics（つまみのボタン、＋／−）は上流のまま入っている


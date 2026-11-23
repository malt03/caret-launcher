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


## Notes

**2026-11-23T16:34:07Z**

ドラッグ中の縮小を移した：HomeDragController に LauncherDragController.createDragView（DRAGGABLE_WIDGET の scalePx）と getWidgetDragScalePx、WidgetDragScaleUtils をコピー（path=）、HomeWorkspace.beginDragShared に preDragEndScale と deferDragToPreDragEnd（上流と同じ場所に移した）。DeviceProfile.getWorkspaceSpringLoadScale は SpringLoadedListener と同じ計算で、キーボードを下ろしたホームの高さから渡す。アクセシビリティは残した：上流の LauncherAccessibilityDelegate はリサイズ枠だけでなくホームのすべてのアイテム（ItemInflater）に付く同じ delegate を枠が借りているもので、枠の操作（リサイズ・移動・削除・閉じる）だけを出すにも BaseAccessibilityDelegate、ButtonDropTarget の getSupportedAccessibilityAction／onAccessibilityDrop、WidgetResizePopupDataSource、移動のための accessible drag（DragAndDropAccessibilityDelegate／WorkspaceAccessibilityHelper を画面にない CellLayout の代わりにホームのグリッドへ付ける、Workspace の enableAccessibleDrag と accessible drop）が要る。今の LauncherAccessibilityDelegate は addToWorkspace だけのインターフェースで、ホームのアイテムにも TalkBack の操作は付いていない。ホーム全体の TalkBack 操作として別にまとめて移すのがよい（範囲の判断待ち）

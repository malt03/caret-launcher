---
id: Tex-44r9
status: closed
deps: [Tex-ydua]
links: [Tex-4cve, Tex-op7u]
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

**2026-11-24T13:36:17Z**

2026-11-24: 設計の見直し（Tex-op7u）で ③ にした。② の後なら大部分が上流のコードになる: LauncherAccessibilityDelegate・BaseAccessibilityDelegate を derived、WorkspaceAccessibilityHelper・DragAndDropAccessibilityDelegate・AccessibleDragListenerAdapter を path=。TalkBack での移動は CellLayout が画面にある状態での Workspace.startDrag（isAccessibleDrag、上流 1777-1801）。HomeController の addToWorkspace・addPendingItemToWorkspace・findSpaceOnWorkspace（405-456、625）は上流の LauncherAccessibilityDelegate.addToWorkspace（408）・findSpaceOnWorkspace（358）に戻る。削っていた setAccessibilityDelegate(getAccessibilityDelegate())（上流 BubbleTextView:423、FolderIcon:247、LauncherAppWidgetHostView:109）と ButtonDropTarget の操作を戻す。印: 追加の画面はない（findSpaceOnWorkspace は空きがなければトースト）。操作のメニューは ⑤ の PopupController の形を使う。範囲はホーム全体に決めた

**2026-11-24T15:41:48Z**

2026-11-25: ホーム全体の TalkBack 対応を移した。BaseAccessibilityDelegate・LauncherAccessibilityDelegate・WorkspaceAccessibilityHelper・DragViewStateAnnouncer・ItemLongClickListener（beginDrag だけ）・UtilitiesKt（isPersistedModelItem だけ）を derived、DragAndDropAccessibilityDelegate・AccessibleDragListenerAdapter・FolderAccessibilityHelper・WidgetResizePopupDataSource・PropertyListBuilder を path=。CellLayout・Workspace・Folder・FolderPagedView・FolderIcon・BubbleTextView・LauncherAppWidgetHostView・AppWidgetResizeFrameCompose・ドロップターゲット・DragLayer・BaseDragLayer・LauncherState・AllAppsState の a11y 部分を戻した。Launcher の互換クラスが delegate を作り、bindInflatedItems・inflateAndBindItemWithAnimation を上流から移した。HomeController の addToWorkspace 系は削除。ドラッグレイヤーが重ねた層なので、フォルダ等が開いている間のホームの隠し（addChildrenForAccessibility・onRequestSendAccessibilityEvent・hover）は LauncherRootLayout でもする。エミュレータで UiAutomation から、移動（仮想セル）、ホーム画面に追加、削除、フォルダ内の並べ替え、フォルダを開いている間にホームが木から消えることを確認。TalkBack そのものでの確認は実機で（docs/device-checklist.md）

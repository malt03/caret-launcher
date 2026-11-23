---
id: Tex-4cve
status: closed
deps: []
links: [Tex-44r9, Tex-iuyb, Tex-c7qj]
created: 2026-11-23T06:16:03Z
type: feature
priority: 3
assignee: Koji Murata
parent: Tex-ogmk
tags: [home, launcher3]
---
# ウィジェットピッカーからドラッグしてホームに置く

Launcher3 は WidgetPickerDragItemListener（BaseItemDragListener、システムのドラッグ）でピッカーからホームにウィジェットを渡す。今の移植（Tex-r705）ではドラッグは何もしない（WidgetPickerComposeWrapperImpl でログを出して返す）。


## Notes

**2026-11-23T16:34:38Z**

上流と同じく、ピッカーが始めたシステムのドラッグを WidgetPickerDragItemListener（BaseItemDragListener をそのままコピー）がホームの DragLayer で受け、PendingItemDragHelper（削ってコピー）でホームのドラッグにつなぐようにした。Flags.enableSystemDrag はリリース設定で無効なので setOnDragListener と SystemDragDriver の経路。落とすと HomeWorkspace.onDropExternal が PendingAddItemInfo を受け（最小の大きさまでの押しのけ、animateWidgetDrop）、Launcher.addPendingItem → addAppWidgetFromDrop で「追加」と同じ流れに入る。Launcher に getRotationHelper・getIntent・setIntent・addPendingItem・goToNormalState（StateManager の代わり）、Workspace に estimateItemSize を足した。ドラッグ中にウィジェットを先に作る WidgetHostViewLoader は移していない（落としてから bind する）。ピッカーが閉じるときのホームの intent でドラッグが取り消されないよう、onNewIntent はシステムのドラッグを取り消さない（Launcher3 の onNewIntent もドラッグを取り消さない）。端末では未確認

---
id: Tex-pb0n
status: open
deps: [Tex-cklf]
links: []
created: 2026-11-21T09:18:12Z
type: task
priority: 2
assignee: Koji Murata
parent: Tex-neft
tags: [refactor]
---
# DragLayer の処理を DragLayer の互換クラスへ移す

BaseDragLayer.java の 228〜406 行目（animateViewIntoPosition、animateView、playDropAnimation、clearAnimatedView、ANIMATION_END_*、setDragController）は、上流では dragndrop/DragLayer.java にある。今 16 行の DragLayer 互換クラスへ移し、アプリ（LauncherRootLayout.kt:37、HomeController.kt:104）は DragLayer を作る。dragndrop パッケージに入れば onDeferredEndDrag を直接呼べるので DragLayerAccess.java を消す。

## Acceptance Criteria

- [ ] BaseDragLayer の中身が上流の BaseDragLayer にある処理だけになっている
- [ ] DragLayerAccess.java がない
- [ ] ドロップ・取り消し・フォルダへのドロップのアニメーションが今と同じ


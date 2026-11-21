---
id: Tex-alrp
status: open
deps: [Tex-f33r]
links: []
created: 2026-11-21T09:18:13Z
type: task
priority: 3
assignee: Koji Murata
parent: Tex-neft
tags: [refactor]
---
# ホームまわりの小さな重複をなくす

- REORDER_TIMEOUT が HomeWorkspace.kt:892 と Workspace.java:12 の 2 か所にある
- findSpaceOnHome（HomeController.kt:633）が GridOccupancy.findVacantCell / CellLayout.findCellForSpan の書き直し
- replaceFolderWithFinalItem（HomeController.kt:651）は LauncherDelegate のメソッド。互換クラスには getLauncher しかない

## Acceptance Criteria

- [ ] 3 つとも上流のメソッドを使う形になっている
- [ ] メニューの追加ボタンでの配置、フォルダが残り 1 つでアイコンに戻る動きが今と同じ


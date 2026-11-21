---
id: Tex-alrp
status: closed
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


## Notes

**2026-11-21T10:07:29Z**

REORDER_TIMEOUT は Workspace の互換クラスのものを使う。findSpaceOnHome をやめ、上流の LauncherAccessibilityDelegate.findSpaceOnWorkspace と同じく CellLayout.findCellForSpan（互換クラスへ上流から移した）で空きセルを探す。見つからないときは空の画面を足す代わりに最後の行の下に置く。replaceFolderWithFinalItem は Tex-dnjc で LauncherDelegate の上書きになった（ホームのモデルと View の操作なのでアプリ側に残す）。エミュレータでメニューの + から最初の空きセルに置かれることを確認

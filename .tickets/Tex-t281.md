---
id: Tex-t281
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
# MSDL のハプティック代用を MSDLPlayerWrapper の互換クラスにまとめる

MSDL の代わりに performHapticFeedback で鳴らす処理が 3 か所にある: CellLayout.java:239、HomeWorkspace.kt:790、DropTargetBar.kt:299。docs/launcher3-porting.md で予定している util/MSDLPlayerWrapper と MSDLToken の互換クラスを作り、呼ぶ側を上流と同じ行にする。

## Acceptance Criteria

- [ ] 代用の処理が MSDLPlayerWrapper の互換クラス 1 か所だけにある
- [ ] 呼ぶ側が上流と同じ形（msdlPlayerWrapper.playToken(...)）になっている
- [ ] ドラッグ中の並び替え・フォルダ作成・削除ボタンに入ったときのハプティックが今と同じに鳴る


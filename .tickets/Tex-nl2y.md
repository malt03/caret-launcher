---
id: Tex-nl2y
status: open
deps: []
links: []
created: 2026-11-21T16:03:47Z
type: bug
priority: 1
assignee: Koji Murata
parent: Tex-ogmk
tags: [home, drag]
---
# アイテムを置いた直後、配置がないのに下にスクロールできる

ホームにアプリを置いた直後、画面の外にアイテムがないのに下にスクロールできる。

## 見込み

- 編集中（ドラッグ中はキーボードが下りて、ホームが画面全体まで広がる）や、キーボードがない状態の大きさで計算したグリッドの高さ（行数、余白）が、編集が終わってキーボードが戻った後も残っていそう
- 調べる場所: ホームのグリッドの行数の決め方（HomeWorkspace、SpringLoadedListener、HomeController）、編集中に足す余白（DropTargetBar の高さ分）、スクロールの入れ物の高さの更新

## 再現手順（実機）

1. 全アプリ一覧か検索結果から、アプリを長押ししてホームに置く
2. 置いた直後にホームを下にスクロールする

## Acceptance Criteria

- [ ] 置いた直後も、配置の一番下までしかスクロールできない
- [ ] 編集中の広がった大きさは、編集が終わると元に戻る


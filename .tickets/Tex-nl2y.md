---
id: Tex-nl2y
status: closed
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

- [x] 置いた直後も、配置の一番下までしかスクロールできない
- [x] 編集中の広がった大きさは、編集が終わると元に戻る

## 結果（2026-11-22）

- 原因: グリッドは、表示範囲の高さ（viewportHeight）をスクロールの入れ物の OnLayoutChangeListener で受け取り、requestLayout していた。リスナーは入れ物の layout の途中で呼ばれ、入れ物の PFLAG_FORCE_LAYOUT がまだ立っているので、requestLayout が親に伝わらずに消えていた。編集中（キーボードなし）の高さが残り、その分スクロールできた
- 直し方: グリッドは入れ物（fillViewport）が EXACTLY で測り直すときの高さを表示範囲として使う。リスナーは消した
- エミュレータで確認: 全アプリ一覧からホームに置いた直後、一番下までスクロールしても最後のアイテムの下は余白（8dp）だけ

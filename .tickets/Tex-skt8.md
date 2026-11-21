---
id: Tex-skt8
status: open
deps: [Tex-t2ea, Tex-17s1]
links: []
created: 2026-11-20T11:16:26Z
type: task
priority: 2
assignee: Koji Murata
parent: Tex-ogmk
tags: [home]
---
# 空き領域の長押しメニュー

ホームの空いている領域の長押しでメニューを出す。
- 「ウィジェットの追加」: ウィジェット一覧を開く
- 「アプリ一覧」: 全アプリ一覧を普段と同じアニメーションで開く（上に引っ張る操作に気付いてもらうため）
- 「壁紙とスタイル」: OS の「壁紙とスタイル」を開く
仕様: docs/spec.md 5.3、6.3、7.1

## Acceptance Criteria

- [ ] 空き領域を長押しすると3項目のメニューが出る
- [ ] 「アプリ一覧」→ 検索フィールドが上がって一覧が出てくるアニメーションで開く
- [ ] 「壁紙とスタイル」→ OS の画面が開く

## Notes

**2026-11-21**

「アプリ一覧」は LauncherActivity.openAllApps（Tex-17s1 で用意した、ジェスチャーなしで普段と同じアニメーションで開く処理）を呼ぶ。

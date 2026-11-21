---
id: Tex-9cyk
status: open
deps: [Tex-17s1, Tex-f4yo]
links: []
created: 2026-11-20T11:16:26Z
type: task
priority: 2
assignee: Koji Murata
parent: Tex-pa8s
tags: [drawer]
---
# 全アプリ一覧内の検索

一覧を開いた状態で検索フィールドをタップすると、一覧内の検索になる。
- 並び順、Enter での起動、学習の記録は通常の検索と同じ
- 結果はフィールドの直下から上詰め（1位がフィールドに一番近い）
- 戻る操作は普通の挙動: 1回目でキーボードを閉じる（入力は残す）、2回目で一覧を閉じる
- この間は 1.4 のキーボード監視をしない
仕様: docs/spec.md 6.5

## Launcher3 からの移植

- 一覧と検索結果の切り替えにアニメーションは付けない（docs/spec.md 6.5）

## Acceptance Criteria

- [ ] 一覧でフィールドをタップ → キーボードが出て、入力で一覧が絞り込まれる
- [ ] Enter → 1位が起動する
- [ ] 戻る1回目 → キーボードだけ閉じ、絞り込みは残る
- [ ] 戻る2回目 → 一覧が閉じ、通常のホームでキーボードが出ている

## Notes

**2026-11-21**

Tex-hibk の時点では、一覧を開いている間に検索フィールドをタップすると一覧を閉じる（LauncherActivity の searchBar.onFocusChanged）。一覧内の検索を作るときに置き換える。

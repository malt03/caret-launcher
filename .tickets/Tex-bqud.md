---
id: Tex-bqud
status: in_progress
deps: [Tex-maoz]
links: []
created: 2026-11-20T11:16:26Z
type: task
priority: 1
assignee: Koji Murata
parent: Tex-epcb
tags: [search]
---
# 戻る操作の無効化と入力クリア

戻る操作を実質的に「入力クリア」にする。
- Activity の戻る操作は OnBackPressedCallback で消費する
- ホームが前面にある間にキーボードが閉じられたら、入力をクリアして表示し直す
- 編集モード中と全アプリ一覧の表示中は、このキーボード監視を止められるようにしておく
仕様: docs/spec.md 1.4

## Acceptance Criteria

- [ ] 文字を入力して戻る操作 → 入力が消え、キーボードが出たまま
- [ ] 何も入力せずに戻る操作 → 画面は変わらず、キーボードが出たまま


## Notes

**2026-11-20T12:06:10Z**

戻る操作で入力がクリアされキーボードが出たままになることを確認。編集モード・全アプリ一覧中に監視を止める仕組みは未実装

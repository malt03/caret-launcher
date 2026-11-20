---
id: Tex-m5mh
status: open
deps: [Tex-f4yo, Tex-4hiv]
links: []
created: 2026-11-20T11:16:26Z
type: task
priority: 2
assignee: Koji Murata
parent: Tex-epcb
tags: [search]
---
# アプリの長押しメニューと検索文字列の編集

アプリの長押しでその場にメニューを出す。
- 項目: 「検索文字列を編集」「アプリ情報」
- 検索文字列はアプリごとに複数登録・削除できる。照合時は正規化される
- ホーム、検索結果、全アプリ一覧で共通の部品にする（ドラッグへの移行は別チケット）
仕様: docs/spec.md 4、5.3

## Acceptance Criteria

- [ ] 検索結果のアプリを長押し → メニューが出る
- [ ] 検索文字列に「かめら」を登録 → 「かめら」でそのアプリが最上位に出る
- [ ] カタカナで「カメラ」と登録 → 「かめら」で一致する
- [ ] 「アプリ情報」→ OS のアプリ情報画面が開く


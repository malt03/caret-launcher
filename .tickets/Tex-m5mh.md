---
id: Tex-m5mh
status: closed
deps: [Tex-f4yo, Tex-4hiv, Tex-wgyx]
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

## Launcher3 からの移植

- メニューの見た目は Launcher3 の popup/ に合わせる（Tex-e1ph と共通の部品にする）

## Acceptance Criteria

- [ ] 検索結果のアプリを長押し → メニューが出る
- [ ] 検索文字列に「かめら」を登録 → 「かめら」でそのアプリが最上位に出る
- [ ] カタカナで「カメラ」と登録 → 「かめら」で一致する
- [ ] 「アプリ情報」→ OS のアプリ情報画面が開く


## Notes

**2026-11-20T12:13:01Z**

検索文字列の編集ダイアログ（SearchTermsDialog）とアプリ情報を実装し、エミュレータで登録→検索を確認。メニューは仮に標準の PopupMenu。Launcher3 の popup/ への置き換えは Tex-e1ph と一緒に行う

**2026-11-20T15:40:21Z**

メニューを Launcher3 の popup/ の見た目に置き換えた（Tex-e1ph）。検索文字列の編集とアプリ情報をエミュレータで確認

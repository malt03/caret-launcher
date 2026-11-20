---
id: Tex-f4yo
status: open
deps: [Tex-l0eq, Tex-rq0b, Tex-maoz]
links: []
created: 2026-11-20T11:16:26Z
type: task
priority: 1
assignee: Koji Murata
parent: Tex-epcb
tags: [search]
---
# 検索結果リストと起動

入力があるときにホームの配置の代わりに検索結果を出す。
- 下寄せで、1位を検索フィールドの直上に置く
- 各候補はアイコンとアプリ名
- 1位（Enter で起動される候補）を強調表示
- Enter または候補のタップで起動し、学習を記録して入力をクリア
- 日本語 IME の変換中の Enter は確定として扱われる（起動しない）
- 候補が0件、または未入力なら Enter で何もしない
仕様: docs/spec.md 2.4、2.5、3.1

## Launcher3 からの移植

- Enter で先頭の結果を起動する流れは allapps/search/AllAppsSearchBarController.java を参考にする
- ホームと検索結果の切り替えのアニメーションは allapps/SearchTransitionController.java を参考にする

## Acceptance Criteria

- [ ] 文字を入力 → 候補が下寄せで出て、1位が強調される
- [ ] Enter → 1位のアプリが起動する
- [ ] 日本語入力の変換中に Enter → 確定されるだけで起動しない
- [ ] 同じ入力で別のアプリを何度か起動 → そのアプリが上位に来る


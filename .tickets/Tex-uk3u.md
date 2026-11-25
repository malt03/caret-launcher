---
id: Tex-uk3u
status: open
deps: []
links: []
created: 2026-11-25T02:16:22Z
type: feature
priority: 2
assignee: Koji Murata
tags: [search]
---
# 検索結果に「Web で検索」のセルを足す

検索欄の文字を ACTION_WEB_SEARCH（SearchManager.QUERY）で既定のアプリに渡すセルを、検索結果のグリッドのアプリの後ろに出す。アプリが0件ならこのセルが1位になり、Enter で動く。調査と案は docs/text-handoff-research.md（4.4 の1）。

## Design

- 仕様（docs/spec.md）を先に書き換える: 2.1・2.4・2.5・8章。2.6 の枠は、1位が操作のセルのときも付ける。6.5（全アプリ一覧の中の検索）にも同じセルを出す。3章の学習には記録しない
- アイコンとラベル: resolveActivity(ACTION_WEB_SEARCH, MATCH_DEFAULT_ONLY) で既定のアプリが分かればそのアイコン。既定がなく選択画面（ResolverActivity）が返るなら虫眼鏡のアイコンで、選択画面をそのまま出す
- manifest の <queries> に WEB_SEARCH を足す
- タップと Enter はアプリの起動と同じく、起動したアプリが画面を覆ってから入力をクリアする（1.2）。長押しのメニューとドラッグはしない

## Acceptance Criteria

- アプリ名に当たらない語を打って Enter を押すと、既定の Web 検索アプリで検索される
- アプリが見つかるときも、セルのタップか矢印キーで Web 検索できる
- Pixel 6a（既定は Google）とエミュレータ（既定なしで選択画面が出る）の両方で動く


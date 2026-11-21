---
id: Tex-vpm4
status: open
deps: []
links: []
created: 2026-11-21T16:03:46Z
type: feature
priority: 2
assignee: Koji Murata
parent: Tex-ogmk
tags: [home]
---
# ホームの縦スクロールのスクロールバー

縦スクロールするホームに、今どこを見ているか分かるスクロールバーを付ける。Launcher3 のホームは横のページ送りでページインジケーターを使うので、そのままの部品はない。

## 決めること

- 見た目と動き: 全アプリ一覧のスクロールバー（views/RecyclerViewFastScroller、つまみが細い線から太くなる）に合わせるか、Android 標準のスクロールバー（スクロール中だけ出て消える）にするか
- つまみをつかんで動かせるようにするか
- 編集中（縮小中、Tex-qkyu）の出し方

## Acceptance Criteria

- [ ] ホームをスクロールすると、位置の分かるスクロールバーが出る
- [ ] 配置が画面に収まるときは出ない


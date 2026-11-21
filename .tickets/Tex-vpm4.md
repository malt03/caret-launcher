---
id: Tex-vpm4
status: closed
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

- [x] ホームをスクロールすると、位置の分かるスクロールバーが出る
- [x] 配置が画面に収まるときは出ない

## 結果（2026-11-22）

- 決めたこと: ホームのスクロールの入れ物の標準のスクロールバーを、全アプリ一覧のスクロールバー（RecyclerViewFastScroller）の色と太さにした。つまみはアクセント色（Themes.getColorAccent）、トラックは textColorPrimary の不透明度 30（MAX_TRACK_ALPHA）、幅は fastscroll_track_min_width（6dp）。スクロールできる間は消えずに出ている
- つまみの長さは見えている割合で変わる（全アプリ一覧のような固定の 52dp ではない）。つまみをつかんで動かすことはできない。見出しのない一続きのホームなので、一覧のような見出しへの飛び先がないため
- 編集中はホームと一緒に縮小される
- エミュレータで確認: 画面に収まらないときは右端に出る。収まるときは出ない（ScrollBarDrawable は範囲が見えている高さ以下ならつまみもトラックも描かない）

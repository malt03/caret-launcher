---
id: Tex-9ipm
status: open
deps: [Tex-l0eq, Tex-4hiv, Tex-maoz, Tex-wgyx]
links: []
created: 2026-11-20T11:16:26Z
type: task
priority: 2
assignee: Koji Murata
parent: Tex-ogmk
tags: [home]
---
# ホームのグリッドと縦スクロール

未入力時に表示する配置エリアを作る。
- 列数、アイコンサイズ、セル幅は Launcher3 の device_profiles.xml の grid-option を参照して決める
- アプリ名は出さず、その分セルの高さを詰める
- 行数の上限なし。収まらなければ縦スクロール
仕様: docs/spec.md 5.1

## Launcher3 からの移植

- グリッドの寸法は device_profiles.xml（android-17.0.0_r1）の値を使う
- セルの並び替えは CellLayout の並び替えロジックを移植して使う（範囲は Tex-wgyx で決める）
- 縦スクロールの入れ物は自前で書く

## Acceptance Criteria

- [ ] 配置したアイテムがグリッドに並び、アプリ名は出ない
- [ ] 画面に収まらない分は縦にスクロールできる
- [ ] 文字を入力すると配置エリアが検索結果に切り替わる


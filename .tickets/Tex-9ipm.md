---
id: Tex-9ipm
status: closed
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

- [x] 配置したアイテムがグリッドに並び、アプリ名は出ない
- [x] 画面に収まらない分は縦にスクロールできる
- [x] 文字を入力すると配置エリアが検索結果に切り替わる


## Notes

**2026-11-20T12:14:50Z**

移植の範囲は docs/launcher3-porting.md の表で確定（Tex-wgyx）

**2026-11-20T12:32:36Z**

ui/home/HomeGridView を実装。列数とアイコンサイズは GridSpec（device_profiles.xml の近似）。アプリ名なし、行数上限なし、NestedScrollView で縦スクロール。並び替えは Launcher3 の ReorderAlgorithm を CellLayout 互換クラス経由で使用し、エミュレータで押しのけを確認。多数の行でのスクロールは未確認

**2026-11-21T05:11:29Z**

ホームに 6 行並べ、縦スクロールで最下行まで表示できることをエミュレータで確認

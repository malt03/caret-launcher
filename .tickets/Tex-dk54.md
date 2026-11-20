---
id: Tex-dk54
status: open
deps: [Tex-1eos, Tex-w8ui]
links: []
created: 2026-11-20T11:16:26Z
type: task
priority: 3
assignee: Koji Murata
parent: Tex-mdus
tags: [icons]
---
# 「壁紙とスタイル」連携 Provider

テーマアイコンとアイコン形状を「壁紙とスタイル」から操作できるようにする。
- ホームの Activity に meta-data com.android.launcher3.themedicon.option と com.android.launcher3.grid.control
- Provider は BIND_WALLPAPER で保護
- icon_themed（読み出し・書き込み）、shape_options、shape を実装。グリッド関連は null
- 変更時は notifyChange し、アイコンの表示をすぐ更新する
仕様: docs/spec.md 7.3、付録

## Acceptance Criteria

- [ ] 「壁紙とスタイル」でテーマアイコンを切り替えると、ランチャーにすぐ反映される
- [ ] 「壁紙とスタイル」で形状を変えると、ランチャーにすぐ反映される
- [ ] ランチャーを再起動しても設定が残る


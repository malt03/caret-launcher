---
id: Tex-w8ui
status: open
deps: [Tex-6b20]
links: []
created: 2026-11-20T11:16:26Z
type: task
priority: 1
assignee: Koji Murata
parent: Tex-mdus
tags: [icons, spike]
---
# 「壁紙とスタイル」連携の実機検証

非公開の取り決めなので、早い段階で成立するかを確かめる。
- AOSP の Launcher3 と ThemePicker のソースで、meta-data 名、パス、列の形式を確認する
- 最小の Provider（icon_themed だけ）を実装し、Pixel（Android 17）の「壁紙とスタイル」にテーマアイコンの項目が出るか確かめる
- 結果をこのチケットのノートに残す。出なかった場合は自前の設定に切り替える方針を検討する
仕様: docs/spec.md 7.3、付録

## Acceptance Criteria

- [ ] ソースで確認した取り決めの内容をノートに記録した
- [ ] Pixel（Android 17）で項目が出るかどうかを確認し、結果を記録した


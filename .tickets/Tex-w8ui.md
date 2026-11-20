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

- [x] ソースで確認した取り決めの内容を記録した（docs/spec.md の付録）
- [ ] Pixel（Android 17）で項目が出るかどうかを確認し、結果を記録した


## Notes

**2026-11-20T11:49:01Z**

android-17.0.0_r1 の Launcher3（graphics/LauncherCustomizationProvider.kt、graphics/GridCustomizationsProxy.java、shapes/ShapesProvider.kt）と ThemePicker（res/values/strings.xml）で取り決めを確認した。内容は docs/spec.md の付録に反映済み。残りは最小の Provider での実機確認。

**2026-11-20T12:11:12Z**

エミュレータ（Android 17 google_apis_playstore）の Google 製「壁紙とスタイル」で、テーマアイコンと形状の項目が出て動くことを確認。実機の Pixel での確認は未実施

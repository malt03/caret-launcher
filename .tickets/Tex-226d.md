---
id: Tex-226d
status: open
deps: []
links: []
created: 2026-11-21T16:03:46Z
type: bug
priority: 1
assignee: Koji Murata
parent: Tex-mdus
tags: [search, theme]
---
# 検索結果の文字色を壁紙に合わせる（Launcher3 の WallpaperColorHints）

検索結果のアプリ名は、ライトモードでは黒になり、暗い壁紙だと読めない。検索結果は壁紙の上に直接出るので、Launcher3 のホームの文字と同じ決め方にする。

## Launcher3 の決め方（android-17.0.0_r1）

- WallpaperColorHints が WallpaperManager の色のヒント（WallpaperColors の HINT_SUPPORTS_DARK_TEXT、HINT_SUPPORTS_DARK_THEME）を見張る
- Themes.getActivityThemeRes が、ヒントとシステムのダーク／ライトから Activity のテーマを選ぶ（AppTheme、AppTheme.DarkText、AppTheme.DarkMainColor と、それぞれの Dark）
- テーマの workspaceTextColor が文字色: 通常は workspace_text_color_light（system_neutral1_0、白）に影 workspaceShadowColor（#B0000000）。DarkText（壁紙が明るく暗い文字が合う）では workspace_text_color_dark（system_neutral1_1000、黒）で影なし
- アイコンの文字は BaseIcon.Workspace.Shadows で、この色と影を使う
- 壁紙の色が変わってヒントが変わると、Launcher は Activity を作り直してテーマを付け直す

つまり、ライトモードかどうかではなく、壁紙が暗い文字に向いているかで決まる。

## 合わせて見ること

- 今の ResultIconView は colorOnSurface の文字に自前の影（4dp、#88000000）を付けている。上の決め方に置き換える
- 検索フィールドの入力文字や、ホームのフォルダ名など、壁紙の上に出るほかの文字も同じ決め方にするか

## Acceptance Criteria

- [ ] 暗い壁紙では、ライトモードでも検索結果の文字が白（影付き）になる
- [ ] 明るい壁紙では黒（影なし）になる
- [ ] 壁紙を変えると、ランチャーに戻ったときに文字色が変わる


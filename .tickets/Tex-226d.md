---
id: Tex-226d
status: closed
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

- [x] 暗い壁紙では、ライトモードでも検索結果の文字が白（影付き）になる
- [x] 明るい壁紙では黒（影なし）になる
- [x] 壁紙を変えると、ランチャーに戻ったときに文字色が変わる

## 結果（2026-11-22）

- WallpaperColorHints と WallpaperThemeManager を互換クラスにし、Themes.getActivityThemeRes をそのまま移した。LauncherActivity は Launcher.onCreate と同じく super.onCreate の直後にテーマを選び、isWorkspaceDarkText でシステムバーのアイコンの明暗を決める
- テーマ（AppTheme と DarkText・DarkMainColor・Dark の各派生）はアプリの res/values/themes.xml に、上流の LauncherTheme の値で書いた。ドロップ先のボタンの色（workspaceAccentColor など）も DarkText で上流どおり切り替わる
- 検索結果のアプリ名は workspaceTextColor と影（BaseIcon.Workspace.Shadows の shadowRadius 2.0、workspaceShadowColor）。上流のアイコンの二重の影（DoubleShadowBubbleTextView の ambient/key）は持たない
- エミュレータで確認: ライトモードで暗い壁紙 → 白い文字に影、明るい壁紙（テクスチャの羽根）に変える → ランチャーが作り直され、黒い文字で影なし、ステータスバーのアイコンも暗くなる

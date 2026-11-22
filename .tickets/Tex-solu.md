---
id: Tex-solu
status: open
deps: []
links: []
created: 2026-11-22T00:57:38Z
type: bug
priority: 1
assignee: Koji Murata
parent: Tex-mdus
---
# ライトモードで壁紙によっては検索結果のアプリ名とシートのハンドルが透明になる

マニフェストの Activity のテーマが Theme.TextLauncher で、WallpaperThemeManager は themeRes = AppTheme から始め、期待するテーマがそれと違うときだけ setTheme する。ライトモードで壁紙の色のヒントが 0（中間色の壁紙、色が取れないとき）だと getActivityThemeRes が AppTheme を返し、setTheme されない。そのため workspaceTextColor、workspaceShadowColor、isWorkspaceDarkText、bottomSheetDragHandleColor が無く、getAttrColor が 0（透明）を返す（ResultsAdapter の文字色、AllAppsSheet のハンドル、LauncherActivity のシステムバーのアイコン）。

- Launcher3: マニフェストのテーマは @style/AppTheme

直し方: マニフェストのテーマを @style/AppTheme にする（AppTheme の親は Theme.TextLauncher なので互換クラスは変えない）


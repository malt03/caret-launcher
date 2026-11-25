---
id: Tex-solu
status: closed
deps: []
links: []
created: 2026-11-22T00:57:38Z
type: bug
priority: 1
assignee: Koji Murata
parent: Tex-mdus
---
# ライトモードで壁紙によっては検索結果のアプリ名とシートのハンドルが透明になる

マニフェストの Activity のテーマが Theme.CaretLauncher で、WallpaperThemeManager は themeRes = AppTheme から始め、期待するテーマがそれと違うときだけ setTheme する。ライトモードで壁紙の色のヒントが 0（中間色の壁紙、色が取れないとき）だと getActivityThemeRes が AppTheme を返し、setTheme されない。そのため workspaceTextColor、workspaceShadowColor、isWorkspaceDarkText、bottomSheetDragHandleColor が無く、getAttrColor が 0（透明）を返す（ResultsAdapter の文字色、AllAppsSheet のハンドル、LauncherActivity のシステムバーのアイコン）。

- Launcher3: マニフェストのテーマは @style/AppTheme

直し方: マニフェストのテーマを @style/AppTheme にする（AppTheme の親は Theme.CaretLauncher なので互換クラスは変えない）


## Notes

**2026-11-23T15:53:35Z**

マニフェストの application のテーマを Launcher3 と同じ @style/AppTheme にした。WallpaperThemeManager は AppTheme から始めるので、色のヒントが 0 のとき（setTheme されないとき）も workspaceTextColor などが入り、検索結果のアプリ名、シートのハンドル、システムバーのアイコンが透明にならない。ビルドと単体テストのみ確認（端末では未確認）

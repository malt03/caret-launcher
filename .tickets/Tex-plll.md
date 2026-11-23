---
id: Tex-plll
status: open
deps: []
links: []
created: 2026-11-22T00:59:03Z
type: chore
priority: 3
assignee: Koji Murata
parent: Tex-vs8t
---
# 使われていないコードと古いコメントの整理

使われていないもの:
- search/DrawerIndex.kt とそのテスト（AlphabeticIndexCompat に置き換え済み）
- NoPressDelayGridLayout（NoPressDelay.kt）
- dimens.xml の result_icon_size、result_row_height
- LauncherRootLayout.dropTargetBarHeight、isInsideSearchBar
- IconCache.getBadgedIcon（マージのため残してもよい）

古いコメント:
- LauncherRootLayout.kt の KDoc が二重で、最初のもの（「ドラッグ中にホームのグリッドが場所を空ける」）は宙に浮いている。シートが「画面の下から」出るという記述（docs/spec.md 6.4 と違う）
- DropTargetBar.kt の「ドラッグ中ホームのグリッドは縮小しない」（dd7bba6 以降は違う。結果は同じ）
- CellLayout.java の setGridSize の二重の Javadoc（「行が置いたものに合わせて増減する」）、HomeWorkspace.kt の rebuildOccupied の理由（「行が中身で増減する」）、HomeWorkspace.kt の「行の上限がない」、HomeController.kt の「下に伸びる」（Tex-8w0r と重なる）
- dimens.xml の見出しの 4_by_5「Nexus 4」と GridSpec の 4_by_4

重複:
- LauncherActivity の launch と startShortcut が同じ範囲とオプションを作っている


## Notes

**2026-11-23T07:00:29Z**

- LauncherAppState の説明（「フォルダの PreviewItemManager が読むアイコンのキャッシュだけ」）が古い。今はウィジェットピッカー用に InvariantDeviceProfile も持つ

**2026-11-23T12:17:52Z**

2026-11-23 整理: CellLayout.setGridSize の二重の Javadoc（上流に戻した）と HomeWorkspace.onDragStart の rebuildOccupied の理由は直した（Tex-n0ut）。ほかはまだ

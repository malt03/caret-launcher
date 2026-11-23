---
id: Tex-ydua
status: open
deps: []
links: []
created: 2026-11-23T09:06:34Z
type: task
priority: 2
assignee: Koji Murata
parent: Tex-vs8t
tags: [launcher3, upstream, home]
---
# アプリ側の大きい書き写しを derived= の互換クラスにする（Workspace、DropTargetBar、WorkspaceTouchListener）

2026-11-23 のレビューの結果。アプリに上流の書き写しが約 2,500 行あり、derived= ではないので更新が追えない。すでにずれが出ている。

- HomeWorkspace.kt（960 行、約 80% が Workspace の onDragOver・manageReorderOnDragOver・manageFolderFeedback・onDrop・createUserFolderIfNecessary）。42 行目は「ウィジェットは省く」とあるが 514・571 行目はウィジェットを扱う
- DropTargetBar.kt（662 行、約 85% が DropTargetBar・ButtonDropTarget・DeleteDropTarget・SecondaryDropTarget）。getButtonType がユーザー制限の確認を落とした（Tex-tely）、onNoCellFound がない（Tex-8w0r）
- HomeOptionsMenu.kt は WorkspaceTouchListener と言いつつ、端の余白とインセットでの除外（上流 110-118 行）、マウスの右クリックのメニュー（125）と長押しの抑制（208）、壁紙へのタップのコマンド（onWallpaperTap、167-172）、ACTION_POINTER_UP を印なしで落としている
- HomeDragController.kt（41 行）は LauncherDragController.java（164 行）とほぼ同じ

やること:
- 互換の Workspace.java を Workspace.java の derived=（ドラッグとドロップのメンバーだけ）にし、スクロールと自動スクロールはアプリが上書きするフックにする（Tex-2es1 の「HomeWorkspace に互換の Workspace を継承させる」をこの形で）
- ドロップ先の4ファイルを互換の Launcher／LauncherState のスタンドインに対する derived= にし、アプリには配置（画面いっぱい、ツールチップなし）だけを残す
- WorkspaceTouchListener.java と WorkspaceLongPressOptions.kt を derived= にする（「ホームの設定」を外したことと「通知ドット」の項目を TextLauncher: で印す）。通知へのアクセスのダイアログはアプリに残す
- HomeDragController は余裕があれば LauncherDragController の derived=
- 残す書き写し（GridSizeMigration、GridSpec、HomeLoader、ItemLongClickListener、SpringLoadedListener、HomeWidgets の追加フロー）は、上の「上流の追跡を広げる」チケットの ref= に載せる


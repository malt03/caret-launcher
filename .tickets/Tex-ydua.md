---
id: Tex-ydua
status: closed
deps: [Tex-mibn]
links: []
created: 2026-11-23T09:06:34Z
type: task
priority: 2
assignee: Koji Murata
parent: Tex-op7u
tags: [launcher3, upstream, home]
---
# ② Workspace と LauncherDragController を上流のコピーにする（ドロップ先と WorkspaceTouchListener は済み）

2026-11-23 のレビューの結果。アプリに上流の書き写しが約 2,500 行あり、derived= ではないので更新が追えない。すでにずれが出ている。

- HomeWorkspace.kt（960 行、約 80% が Workspace の onDragOver・manageReorderOnDragOver・manageFolderFeedback・onDrop・createUserFolderIfNecessary）。42 行目は「ウィジェットは省く」とあるが 514・571 行目はウィジェットを扱う
- DropTargetBar.kt（662 行、約 85% が DropTargetBar・ButtonDropTarget・DeleteDropTarget・SecondaryDropTarget）。getButtonType がユーザー制限の確認を落とした（Tex-tely）、onNoCellFound がない（Tex-8w0r）
- HomeOptionsMenu.kt は WorkspaceTouchListener と言いつつ、端の余白とインセットでの除外（上流 110-118 行）、マウスの右クリックのメニュー（125）と長押しの抑制（208）、壁紙へのタップのコマンド（onWallpaperTap、167-172）、ACTION_POINTER_UP を印なしで落としている
- HomeDragController.kt（41 行）は LauncherDragController.java（164 行）とほぼ同じ

やること:
- 互換の Workspace.java を Workspace.java の derived=（ドラッグとドロップのメンバーだけ）にし、スクロールと自動スクロールはアプリが上書きするフックにする（Tex-2es1 の「HomeWorkspace に互換の Workspace を継承させる」をこの形で）
- ドロップ先の4ファイルを互換の Launcher／LauncherState のスタンドインに対する derived= にし、アプリには配置（画面いっぱい、ツールチップなし）だけを残す
- WorkspaceTouchListener.java と WorkspaceLongPressOptions.kt を derived= にする（「ホームの設定」を外したことと「通知ドット」の項目を CaretLauncher: で印す）。通知へのアクセスのダイアログはアプリに残す
- HomeDragController は余裕があれば LauncherDragController の derived=
- 残す書き写し（GridSizeMigration、GridSpec、HomeLoader、ItemLongClickListener、SpringLoadedListener、HomeWidgets の追加フロー）は、上の「上流の追跡を広げる」チケットの ref= に載せる


## Notes

**2026-11-23T12:17:52Z**

2026-11-23: これらの書き写しは Tex-hpdq で ref= に載り、更新時に上流の差分が出るようになった。derived= の互換クラスにする作業はまだ（Tex-n0ut で互換の Folder・CellLayout などを derived= にした形に合わせる）

**2026-11-23T17:16:42Z**

2026-11-24: Tex-2es1 でモデルの書き換えは ModelWriter にまとまった（互換の Workspace に removeWorkspaceItem・getViewByItemId、互換の Launcher に removeItem・getModelWriter・getAppWidgetHolder を足した）。HomeWorkspace.Host（addFolder・addItem・removeWorkspaceItem・updateWidgetSize・runWhenNormal）と HomeController の replaceFolderWithFinalItem（LauncherDelegateImpl の本体）は、HomeWorkspace を Workspace にするときに Launcher・Workspace・LauncherDelegate の互換クラスへ移す

**2026-11-23T18:03:40Z**

2026-11-24（途中経過）: ドロップ先の4ファイルと DropTargetHandler を derived= にした（132f85a。drop_target_bar.xml から作り、置き場所だけ LauncherRootLayout。アンインストールの結果は Launcher.addEventCallback(EVENT_RESUMED) で待つ）。WorkspaceTouchListener と WorkspaceLongPressOptions も derived= にした（4fbcbd0。リスナーはホームのスクロールに付け、isHandlingTouch はスクロールしたかで見る。壁紙へのタップを送るようにした。端とインセットの除外は CaretLauncher: で省いた。通知へのアクセスのダイアログはアプリの NotificationAccessConfirmation）。Workspace と LauncherDragController はこれから

**2026-11-23T18:05:32Z**

2026-11-24: 残りは Workspace（HomeWorkspace を互換の Workspace の derived= に）と LauncherDragController。次の人への手がかり: (1) 上流の Workspace のコンストラクターは Launcher.getLauncher(context) で Launcher を引くが、こちらは Launcher の互換クラスが Workspace をコンストラクターで受けるので循環する。互換の Launcher.getWorkspace を abstract にして HomeActivityContext を先に作り、Workspace は HomeActivityContext.kt に足した LauncherContextWrapper（ActivityContext.Provider）の Context で作る（ドロップ先のバーはこれで drop_target_bar.xml から作っている）。(2) 互換の Workspace は FrameLayout を継承したまま（スクロールビューを抱える）にして CaretLauncher: で印し、DropTarget・DragSource・DragListener を上流のメンバーの順で持たせる。ドラッグの座標はグリッドの座標（getDropView がグリッド）なので mapPointFromSelfToChild・mapPointFromDropLayout は印を付けて素通しにする。setDropLayoutForDragObject（見えている範囲）と自動スクロール（onDragOver・onDragExit・onDragEnd）をアプリが上書きするフックにする。(3) beginDragShared の長押しメニューは、上流が btv.startLongPressAction(mLauncher.getPopupControllerForAppIcons()) と getPopupControllerForHomeScreenItems().show(child)（Tex-ukrg の Poppable）だが、アプリは ItemLongClickListener.startLongPressAction のラムダを渡している。PopupController の形を Launcher の互換クラスに足すか、ラムダの引数を印付きで残すかを決める。ウィジェットの preDragEndScale・deferDragToPreDragEnd（Tex-44r9）は上流の形のまま残る。(4) HomeWorkspace.Host は、addFolder を互換の Launcher.addFolder、addItem を上流 onDropExternal の addOrMoveItemInDatabase と addInScreen、updateWidgetSize を WidgetSizeHandler.updateSizeRanges、runWhenNormal を互換の Launcher に足す goToState(NORMAL, delay, callback) 相当（SpringLoadedListener.runWhenNormal）に置き換えて消す。HomeController.replaceFolderWithFinalItem（Tex-a8os の確認付き）は互換の LauncherDelegate に LauncherDelegateImpl として移す。(5) 残す挙動: onNoCellFound のトースト（Tex-8w0r）、onDropExternal の PendingAddItemInfo（Tex-4cve。ドラッグ中にウィジェットを作らない）、onDragStart の rebuildOccupied、onDropCompleted の遅れた結果での markCellsAsOccupiedForView（アンインストールの結果）、ドロップ後のリサイズ枠（getWidgetResizeFrameRunnable）。(6) DropTargetHandler.getViewUnderDrag は今 null を返す（RECONFIGURE がないので使わない）。Workspace.getDragInfo ができたら上流の本体に戻せる

**2026-11-24T13:36:17Z**

2026-11-24: 設計の見直し（Tex-op7u）で、この残り（Workspace と LauncherDragController）を ② にした。先に ①（Tex-mibn、ホームの CellLayout を画面につなぐ）をやる。形: Workspace は PagedView にせず FrameLayout（編集中に縮める）> NestedScrollView > CellLayout。上流のメソッドの本体をそのまま残すため、クラスの最後に1ページ用のメンバー（getPageCount()=1、getPageAt(0)、getCurrentPage()/getNextPage()=0、getVisiblePageIndices()={0}、isPageInTransition()=false）を足し、mWorkspaceScreens は1つだけ持つ。座標: getDropView は上流と同じく Workspace、mapPointFromDropLayout は常にホットシートの分岐（Workspace.java:2750-2757。Utilities.mapCoordInSelfToDescendant が scrollY を見る）を通す印1つ。AccessibleDragListenerAdapter はスクロールの View で作る（子は CellLayout だけ）。自動スクロールは onDragOver・onDragExit・onDragEnd のフック。HomeWorkspace.kt は 150 行ほどのフックに、HomeController の匿名の Workspace（133-176）と WorkspaceHost と HomeDragController はなくなる。印: PagedView でない、1ページのメンバー、スクロールを見る座標変換、自動スクロール、onNoCellFound のトースト、事前に bind しない PendingAddItemInfo、rebuildOccupied、遅れて来る占有の結果。⑤ の ItemLongClickListener（beginDragShared が PopupController を呼ぶ）は一緒に進めてよい

**2026-11-24T15:01:17Z**

2026-11-25: 済み。互換の Workspace を上流から作り直した（derived。ドラッグとドロップ、フォルダ作成、並べ替え、外からのドロップ、アイテムの削除を上流のまま残し、ページ・ホットシート・オーバーレイ・壁紙のオフセット・状態の遷移・タッチ・保存・システムのドラッグ・ホーム画面のファイルを削除）。PagedView でなく FrameLayout で、1ページ用のメンバー（addPage、getPageAt、getPageCount、getDestinationPage、snapToPage、isVisible、isPageInTransition など）を末尾に足した。座標は上流と同じく Workspace の座標で、mapPointFromDropLayout はドラッグレイヤーを通してスクロールを考える。setDropLayoutForDragObject はページのスクロールの入れ物の範囲。編集モードへの出入り（EDIT_MODE／SPRING_LOADED の goToState）は印を付けて SpringLoadedListener に任せ、コールバックは runWhenNormal（Workspace の abstract）。WorkspaceLayoutManager（derived、ホットシートを削除、長押しの受け口は abstract）、LauncherDragController（derived、フリックでの削除を削除）、PopupController（derived、型の制約と factory を削除）。CellPosMapper・CellInfo・IntSparseArrayMap は path=。互換の Launcher は getWorkspace・addFolder・getDropTargetBar・getPopupControllerFor*・isTouchInProgress・inflateItem・getCellLayout・isHotseatLayout を持つ（Workspace はコンストラクターで Launcher を引くので、Launcher を先に作る）。アプリの HomeWorkspace は Workspace を継承し、長押しの受け口・persistRemoveItemsByMatcher・自動スクロールだけ。HomeDragController は削除。ItemLongClickListener は上流と同じく Workspace.startDrag(CellInfo) を呼び、メニューは PopupController（popupControllerForAppIcons／ForHomeScreenItems）。エミュレータ（スマホ）でメニュー、移動、フォルダへの追加、全アプリ一覧からのフォルダ作成、再起動後の保存、フォルダから外へ、削除への落としを確認。残り: SpringLoadedListener を WorkspaceStateTransitionAnimation にすること（Tex-00l4 の残り）

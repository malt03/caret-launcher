---
id: Tex-n0ut
status: closed
deps: []
links: []
created: 2026-11-22T00:58:38Z
type: task
priority: 1
assignee: Koji Murata
parent: Tex-vs8t
---
# 互換クラスにコピーした上流コードを更新の手順に載せ、印のない変更とアプリの都合を整理する

CellLayout（1152行。performReorder、animateItemsToSolution、visualizeDropLocation、commitTempPlacement など）、Folder、FolderIcon、FolderPagedView、BubbleTextView、BaseDragLayer、DragLayer、ShortcutAndWidgetContainer、ItemInfoWithIcon、WorkspaceItemInfo、SystemShortcut、Interpolators は「本体は X.java からコピー」と書いた手書きの互換クラスだが derived= ではないので、scripts/update-upstream.sh が上流の修正を知らせない。

やること:
1. 追跡の仕組み: たとえば ref=<上流のパス> <互換のパス> を UPSTREAM.md に足し、スクリプトが base → theirs の上流の差分を見せる
2. 印のない変更（docs/launcher3-porting.md の規則違反）:
   - Folder.handleClose に getParent() != null && VISIBLE の条件を追加、clearLeaveBehindIfExists とアクセシビリティのイベントを削除
   - Folder.removeFolderContent を書き換え（上流は View を外して並べ直し、1つ以下なら閉じるか置き換える）
   - Folder.getContentAreaHeight の getMaxContentAreaHeight / MIN_CONTENT_DIMEN の制限を削除
   - Folder.dispatchDraw で毎回 mBackground.setBounds、ぼかしの helper を削除
   - Folder.getFolderAnimationManager のフラグの畳み込み、Folder.startDrag の mEmptyCellRank = item.rank の削除
   - BubbleTextView.applyLabel が無効なアプリの読み上げ（disabled_app_label、「無効」）を落としている（停止中のアプリや無効なショートカットがあるのでアクセシビリティの退行）。getIconCreationFlagsForInfo の DISPLAY_SEARCH_RESULT_SMALL と FLAG_SKIP_USER_BADGE の分岐の削除
   - PopupContainer.kt の onDropCompleted の引数を View? に、PopupContainerWithArrow.kt の setPopupItemDragHandler の位置の移動、IconCache.kt の close() の workerHandler.post
   - WallpaperThemeManager の unregisterComponentCallbacks 追加と setWallpaperDependentTheme の削除、WallpaperColorHints の .toList()
3. 互換クラスに入ったアプリの都合をアプリ側のフックに出す: CellLayout.setGridSize の占有の引き継ぎ（Javadoc も二重）、Folder.closeComplete のフォーカスの決まり（ドキュメントでは HomeFolder が決める）、Folder.onDropCompleted の失敗時の戻し方、FolderIcon.onDragEnter（ばねで開かない）、FolderPagedView.removeItem の並べ直し、ThemeManager.getIconShape がフォルダーの形を返す


## Notes

**2026-11-23T09:06:57Z**

2026-11-23 の再レビュー（Tex-hpdq と合わせて読む）:
1. derived= にできる候補（上流と同じ順で残っている）: Folder（メソッド 48/57）、ShortcutAndWidgetContainer（12/12）、AllAppsStore（64/71、通知ドットで追加）、PopupDataProvider（65/66、同）、BaseActivity、SystemShortcut、WorkspaceItemInfo、ItemInfo、LauncherAnimUtils、DragLayer。CellLayout（上流と同じ順のメソッドは 21/76。中身は書き換えではなく呼び出し元ごとに並べ替えただけ）、BubbleTextView（30/62）、FolderIcon（27/44）は、上流の順に並べ直し、CaretLauncher だけのメソッドを印つきの1か所にまとめてから derived= にする。FolderPagedView、Utilities、BaseDragLayer は作り直しが大きいので ref=（Tex-hpdq）で追う。合成した1行の上流の変更での merge-file の試験では、今の derived= は 82〜95%、スタンドインは 40〜80% が衝突なしで入った（移動したブロックは必ず衝突になるので、黙って落ちることはない）
2. 印のない変更（まだある・新しく見つかったもの）: 上の Folder・BubbleTextView の項目はまだそのまま。新しく: Folder.closeComplete が上流のドットのアニメーション（mFolderIcon.hasDot() なら animateDotScale(0f,1f)）、setIconVisible(true)、animateBgShadowAndStroke、onFolderClose を落としている（通知ドットの移植で戻すべきだった）。CellLayout.markCellsAsOccupiedForView / markCellsAsUnoccupiedForView が getParent() != mShortcutsAndWidgets を indexOfChild < 0 に変え、ウィジェットの CellPosMapper の分岐を落としている。derived= では PopupContainer.kt 175 行の View?（元に戻す）、PopupContainerWithArrow.kt 83-85 行の setPopupItemDragHandler の移動（UPSTREAM.md の「削っただけ」と食い違う）、IconCache.kt 125 行の workerHandler.post（印がない）と 320-322 行の getTitleNoCache の移動、ResizeManager.kt 271-282 行の saveItemPlacement の分岐と 65 行の as ItemInfo、WidgetPickerComposeWrapperImpl.kt 140・169・209・283 行の複数行の呼び出しを1行に詰めたこと（上流の変更が衝突する）、WallpaperThemeManager.kt 56 行・WallpaperColorHints.kt 69 行。RotationHelper、AppWidgetResizeFrameCompose、PopupContainerWithArrow、WidgetPickerAddItemListener はクラスの説明でしか印していない（規則は「該当箇所に」）
3. 互換に入ったアプリの都合（新しく derived= に入ったもの）: PopupContainer.keepPlaceOnScreen（キーボードに合わせてメニューを動かす）、AppWidgetResizeFrameCompose.followWidget（グリッドのスクロールに枠を追従）、WindowManagerProxy.canShowHome（外側の画面の条件）。Launcher の互換クラスのフック（saveItemPlacement、onWidgetResizeFrameOpenChanged）はうまく働いているので、同じ形でアプリから渡す

**2026-11-23T11:16:42Z**

2026-11-23: 1・2 を行った。上流のコードを抱える互換クラス 18（Folder、FolderIcon、BubbleTextView、CellLayout、ShortcutAndWidgetContainer、DragLayer、ItemInfo、ItemInfoWithIcon、WorkspaceItemInfo、AppInfo、AllAppsStore、PopupDataProvider、SystemShortcut、BaseActivity、ActivityContext、LauncherAnimUtils、LauncherAppWidgetHostView、Utilities）を、上流のファイルから削った形（上流の順・書き方、変更はその場に CaretLauncher: の印、CaretLauncher だけのメンバーはクラスの最後）に作り直し、ref= から derived= に移した。LauncherAccessibilityDelegate は本文もそろえたが、インターフェースなので ref= のまま。上の印のない変更は、上流に戻すか印を付けた（Folder: handleClose・removeFolderContent・getContentAreaHeight・dispatchDraw・getFolderAnimationManager・closeComplete のドットのアニメーションなどを上流に。BubbleTextView: 無効なアプリの読み上げ（AppIconView も）・getIconCreationFlagsForInfo を上流に。CellLayout: markCells* の親の確認を上流に）。既存の derived=（PopupContainerWithArrow、IconCache、ResizeManager、WidgetPickerComposeWrapperImpl、RotationHelper、AppWidgetResizeFrameCompose、WidgetPickerAddItemListener）と WallpaperThemeManager・WallpaperColorHints も上流の書き方に戻すか印を付けた。PopupContainer.onDropCompleted の View? は、ポップアップからのドラッグの取り消しで null が来るので印を付けて残した。エミュレータで、フォルダーの開閉とドット、フォルダーから出す・最後の1つで置き換え・作成・追加、押しのけ、ウィジェットのメニューと枠、App info、ウィジェットピッカーを確かめた。残り: 3（アプリの都合をフックに出す）。案: CellLayout.setGridSize は上流に戻し、アプリがサイズを変えるときに rebuildOccupied を呼ぶ（今は items のない init でしか呼ばないので占有の引き継ぎは効いていない）、Folder.closeComplete のフォーカスは protected boolean shouldFocusIconOnClose()、onDropCompleted の失敗時は canReturnItemOnFailedDrop(DragObject)、FolderIcon.onDragEnter のばねは shouldSpringLoad(ItemInfo)（上流の mOpenAlarm などを戻す）、FolderPagedView.removeItem、ThemeManager.getIconShape。PopupContainer の showForSystemShortcuts・PopupData・keepPlaceOnScreen はまだ上流の位置にない。16_r4→17_r1 の試算では derived= の衝突はファイルごとに数か所〜30か所ほど（削った部分の上流の変更）

**2026-11-23T11:33:13Z**

2026-11-23: 3 を行った。CellLayout.setGridSize は上流に戻した（グリッドの大きさは起動時に一度だけ決まり、占有は addItemView・removeView が保つ。HomeWorkspace.onDragStart の rebuildOccupied は念のための作り直しとして残し、古いコメントを直した）。Folder の closeComplete のフォーカスは shouldFocusIconOnClose(hadFocus)、失敗したドラッグの戻し方は canReturnItemOnFailedDrop(DragObject) のフックにして、HomeFolder が決める（キーで開いたときだけアイコンにフォーカスを戻す、フォルダーアイコンがホームにあるときだけ戻す）。ThemeManager.getIconShape はフォルダーの形ではなく、選んだアイコン形状から pickBestShape で作った形を返す（DragView のマスク）。FolderPagedView.removeItem は1ページなだけで上流と同じなので、印だけ直した。FolderIcon.onDragEnter でフォルダーが開かないのは、開いたフォルダーがドロップ先にならない（外からのドラッグを受けない）という構造の違いなので、印を付けたまま残す。PopupContainer.kt も上流の順に並べ直した（showForSystemShortcuts は View 版の枝だけ、PopupData・PopupCategory と keepPlaceOnScreen は最後に）。keepPlaceOnScreen・followWidget・canShowHome は derived= の最後の CaretLauncher だけのブロックにまとまっているので、そのままにする。エミュレータで、タッチとキーでのフォルダーの開閉のフォーカス、長押しメニュー2種、ドラッグ中のアイコン、全アプリ一覧からの追加を確かめた

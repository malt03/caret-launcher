---
id: Tex-n0ut
status: open
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


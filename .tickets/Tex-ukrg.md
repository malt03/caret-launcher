---
id: Tex-ukrg
status: closed
deps: []
links: []
created: 2026-11-23T02:39:38Z
type: feature
priority: 3
assignee: Koji Murata
parent: Tex-ogmk
tags: [home, launcher3]
---
# フォルダの長押しで Launcher3 と同じメニュー（削除）を出す

android-17 のリリース設定では Flags.homeScreenEditImprovements が有効で、フォルダ（Poppable）の長押しは PopupControllerForExtraHomeScreenItems のメニュー（FolderSystemShortcuts の「削除」）を出し、指を動かすとドラッグに移る（Workspace.startDrag）。今はウィジェットだけ対応していて、フォルダは長押しですぐドラッグが始まる（ItemLongClickListener.onHomeFolderLongPress）。docs/spec.md 5.3 も合わせて直す。


## Notes

**2026-11-23T08:55:13Z**

通知ドットの移植（Tex-4o3v）で FolderIcon にドットの描画と setForceHideDot を入れたが、FolderIcon はまだ IconViewController を実装していない（上流は実装し、長押しメニューを出す間 PopupContainer が setForceHideDot(true) でドットを隠す）。フォルダの長押しメニューを入れるときに、IconViewController の実装（setIconVisible・getIconHeight・setForceHideDot・getFloatingViewTextAlpha）も上流どおりにする。

**2026-11-23T16:01:42Z**

フォルダの長押しで、ウィジェットと同じく PopupControllerForExtraHomeScreenItems のメニューを出し、指を動かすとドラッグに移るようにした。項目は PopupDataRepository.getAllSupportedPopupActions に合わせ、FolderSystemShortcuts（「削除」）と AppWidgetSystemShortcuts（「削除」と「設定」）を ItemLongClickListener に移した。「削除」はフォルダを中身ごとホームから外す（元に戻す操作はない。ウィジェットと同じ）。popup/Poppable.kt をそのままコピーし、FolderIcon と LauncherAppWidgetHostView の互換クラスに上流どおり実装させ、HomeWorkspace.beginDragShared は上流と同じく Poppable で分ける。FolderIcon は IconViewController も上流どおり実装した（setIconVisible・getIconHeight・setForceHideDot。名前がないので getFloatingViewTextAlpha は null）。docs/spec.md 5.3、docs/launcher3-porting.md、UPSTREAM.md を直した

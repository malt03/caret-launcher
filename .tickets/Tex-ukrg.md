---
id: Tex-ukrg
status: open
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

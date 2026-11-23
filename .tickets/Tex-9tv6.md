---
id: Tex-9tv6
status: closed
deps: []
links: []
created: 2026-11-23T11:16:42Z
type: bug
priority: 2
assignee: Koji Murata
parent: Tex-ogmk
---
# フォルダーからのドラッグが失敗して戻ると、フォルダーのプレビューで戻したアイテムが隠れたままになる

2026-11-23 に Pixel9ProFold のエミュレータで再現（Tex-n0ut の変換前のビルドでも起きる）。3つ入りのフォルダーを開き、中のアイテムを長押しして、縮んだホームのグリッドの外（落とせない場所）で離す。DB ではフォルダーに戻っている（rank も同じ）が、フォルダーアイコンのプレビューにそのアイテムが描かれない。毎回ではない（変換前のビルドで1回は正しく戻った）。FolderIcon.onDrop の itemReturnedOnFailedDrop の経路で PreviewItemManager.hidePreviewItem(index, true) を戻す DragLayer.animateView の終わりの処理（hidePreviewItem(finalIndex, false)）が、走らないか別の index に効いていると思われる。Launcher3 では失敗時に Folder.onDropCompleted がビューを並べ直してから FolderIcon.onDrop を呼ぶ（TextLauncher はフォルダーを閉じているので、その部分を持たない）


## Notes

**2026-11-23T13:49:24Z**

2026-11-23: 直した。フォルダーの中のドラッグを Launcher3 の実装に戻したので、失敗時は上流どおり Folder.onDropCompleted が View を戻してから FolderIcon.onDrop を呼ぶ。プレビューが隠れたままになるのは、ドロップのアニメーションの最後に hidePreviewItem(false) したあと FolderIcon を描き直すものがなかったため（上流も同じコード）。FolderIcon.onDrop の終わりの処理に invalidate() を足して印を付けた。エミュレータで、引き出して外で離す操作でプレビューに戻ることを確かめた

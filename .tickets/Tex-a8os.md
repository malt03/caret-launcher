---
id: Tex-a8os
status: open
deps: []
links: [Tex-3dnt, Tex-ijxl]
created: 2026-11-22T00:57:38Z
type: bug
priority: 0
assignee: Koji Murata
parent: Tex-ogmk
---
# フォルダーからドラッグ中のアイテムが消える・重複する

HomeLoader.removeItems は、matcher が何も消していなくても、中身が1つのフォルダーを畳んで changed=true にする。2つ入りのフォルダーから1つをドラッグしている間（Folder.onDragStart で中身からはもう外れている）にアプリやショートカットの変化が来ると、モデル上でフォルダーが畳まれて rebuild され、cancelDrag → Folder.onDropCompleted(false) でモデルにないフォルダーに戻されて保存され、アイテムが消える。再バインドより先にドロップすると、HomeController.replaceFolderWithFinalItem が最後のアイテムを model.items に二重に足す（Tex-ijxl の候補）。

- Launcher3: 削除は View を通して行う（Workspace.removeItemsByMatcher → Folder.onRemove）。最後の1つへの置き換えは replaceFolderWithFinalItem だけで、ドラッグ中は mDeleteFolderOnDropCompleted で後回しにする。PackageUpdatedTask はモデルでフォルダーを畳まない
- 「最後の1つでフォルダーを置き換える」処理が HomeController と HomeLoader の2つにあり、動きが違う

直し方: モデルでの畳み込みをやめ、Folder.removeFolderContent → replaceFolderWithFinalItem の1本の経路にする（Folder.mIsDragInProgress を尊重）。部分更新（別チケット）と合わせて行う


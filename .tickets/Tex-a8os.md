---
id: Tex-a8os
status: closed
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


## Notes

**2026-11-23T11:44:02Z**

2026-11-23: 直した。HomeLoader.removeItems は、中身を実際に外したフォルダーだけを畳む（ドラッグで1つになっているフォルダーを、関係のない削除で畳まない）。読み込み時に1つしかないフォルダーは Folder.bind の上流の後片付け（post で replaceFolderWithFinalItem）を戻して処理する。アプリの変化（onAppsChanged）と rebuild の始めにドラッグを取り消し（ModelCallbacks.startBinding と同じ。モデルを変える前に）、ショートカットの消滅（pruneMissing）でもモデルから消えるものがあれば先に取り消す。HomeController.replaceFolderWithFinalItem は、モデルにもうないフォルダーでは何もしない（二重に足さない）。エミュレータで、2つ入りのフォルダーから引き出している間に別のアプリを停止・再開して、アイテムがフォルダーに戻り何も消えないことを確かめた。モデルでの畳み込みを Folder の経路1本にまとめるのは部分バインド（Tex-3dnt）と合わせて

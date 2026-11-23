---
id: Tex-536b
status: closed
deps: []
links: [Tex-2es1]
created: 2026-11-22T00:57:38Z
type: bug
priority: 0
assignee: Koji Murata
parent: Tex-ogmk
---
# 途中の保存でショートカットの固定が外れる

HomeModel.save() は保存のたびにその時点の shortcutKeys() で ShortcutRepository.updatePinnedStateBlocking を呼び、LauncherApps.pinShortcuts で固定の集合を丸ごと入れ替える。save() は操作の途中でも呼ばれる:
- HomeWorkspace.createUserFolderIfNecessary が元と先を WorkspaceHost.removeWorkspaceItem（View を外し、モデルから消し、保存）で外してからフォルダーを作る → ショートカット同士でフォルダーを作ると、いったん両方の固定が外れる
- Folder.onDragStart → onContentRemoved → FolderIconView.Callbacks.onContentChanged → save()（ドラッグの間ずっと固定が外れる）
- HomeActivityContext.updateAndBindWorkspaceItem がメニューからのドラッグのたびに保存する
固定でしか残っていないショートカット（会話のショートカットなど）は、外れた時点でシステムに消され、二度と固定できない。

- Launcher3: Workspace.removeWorkspaceItem は View を外すだけ。フォルダー作成は addOrMoveItemInDatabase で移す。Folder.removeFolderContent は DB を変えない。updateShortcutPinnedState は本当の追加・削除（BgDataModel.addItems / removeItems）でだけ走る

直し方: View の削除とモデルの削除を分け、保存はユーザーの操作ごとに1回にする。固定の更新は、ショートカットのキーの集合が変わったときだけにする（ドラッグごとの IPC もなくなる）


## Notes

**2026-11-23T11:44:02Z**

2026-11-23: 直した。HomeModel.save() はドラッグの間（DragListener の onDragStart〜onDragEnd。ドラッグ元より先に登録）は保存を後回しにし、終わりに1回だけ保存する（フォルダーからのドラッグ・フォルダー作成・メニューからのドラッグの途中の状態は書かない）。固定の更新はショートカットのキーの集合が前回と変わったときだけ（読み込み後の最初の保存では必ず行う）。エミュレータで、ショートカットとアプリでフォルダーを作る・フォルダーからショートカットを引き出して止めている間に、DB が変わらず固定も外れないことを確かめた。View とモデルの削除を分ける本来の整理は Tex-2es1 に残る

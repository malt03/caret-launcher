---
id: Tex-536b
status: open
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


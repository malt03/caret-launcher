---
id: Tex-2es1
status: open
deps: []
links: [Tex-536b]
created: 2026-11-22T00:58:38Z
type: task
priority: 1
assignee: Koji Murata
parent: Tex-vs8t
---
# Workspace・Launcher・ModelWriter の役割をまとめ、モデルの書き換えを1か所にする

Workspace の役割が、互換の Workspace（HomeController の匿名サブクラスで persistRemoveItemsByMatcher だけ）、HomeWorkspace（ドラッグ。Workspace ではない）、HomeController（addInScreen、bindItems、WorkspaceHost の removeWorkspaceItem）に分かれている。Launcher の役割は HomeController と HomeActivityContext に分かれ、HomeActivityContext は addToWorkspace や updateAndBindWorkspaceItem（LauncherModel の仕事）を HomeController に戻している。モデルを書き換える場所が WorkspaceHost、HomeLoader.removeItems、FolderIconView.Callbacks、HomeActivityContext の4か所ある。HomeWorkspace、DropTargetBar、HomeController は launcher を2〜3通りの形（activity、mLauncher、host、ドラッグコントローラーのラムダ）で受け取っている。

アイテムの位置も2か所で持つ: 互換の CellLayout.commitTempPlacement はモデルを更新せず（「持ち主が保存する」）、HomeController.onItemsMoved が全部の子の LayoutParams を ItemInfo に書き戻す。今は正しいが、ウィジェットのリサイズ（Tex-t2ea）などの新しい経路でずれる。

案:
- HomeWorkspace に互換の Workspace（スクロールの入れ物を包む FrameLayout）を継承させ、addInScreen、removeWorkspaceItem、removeItemsByMatcher、persistRemoveItemsByMatcher を持たせる
- 互換の Launcher に addFolder、removeItem、getModelWriter、getItemInflater を足して HomeActivityContext が実装する。HomeWorkspace.Host をなくし、LauncherDelegate.replaceFolderWithFinalItem（LauncherDelegateImpl）をほぼそのまま互換に移す（newIcon.requestFocus() を落とすなら TextLauncher: の印を付ける）
- HomeModel に ModelWriter 型の操作（addOrMove、delete、modify）を持たせ、操作の終わりに1回保存する
- commitTempPlacement を上流の本体（modifyItemInDatabase）に戻す
- Tex-a8os、Tex-536b の根本対応を兼ねる


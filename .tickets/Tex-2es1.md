---
id: Tex-2es1
status: closed
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


## 追記（2026-11-23）

ウィジェットのリサイズ（Tex-yw4e）は、互換の ResizeManager が CellLayout.createAreaForResize で確定したあと Launcher.saveItemPlacement を呼び、HomeController.onItemsMoved で子の LayoutParams を ItemInfo に書き戻している。上流では CellLayout.commitTempPlacement が DB に書く。経路がもう1つ増えたので、まとめるときはここも含める。

## Notes

**2026-11-23T09:06:57Z**

2026-11-23: HomeWorkspace は「互換の Workspace を継承」だけでなく、Workspace.java の derived=（ドラッグとドロップのメンバー）にする方向で（Tex-ydua）。ウィジェットのサイズも互換の DeviceProfile／IDP を唯一の元にする（Tex-qnof）

**2026-11-23T12:17:52Z**

2026-11-23: P0 の応急の直し（Tex-536b・Tex-twu9）が入った。HomeModel.save() は読み込み前は何もせず、ドラッグの間は後回し（deferSaves）、固定の更新はキーの集合が変わったときだけ。モデルを丸ごと書く形とモデル・View の削除の混在はそのままなので、ここで1か所にまとめるときに deferSaves を ModelWriter 相当の書き込みに置き換える

**2026-11-23T17:16:42Z**

ModelWriter（derived=、IModelWriter も）と BgDataModel（derived=、WorkspaceData も）を移し、ホームのモデルの書き換えをすべて ModelWriter に通した（変わったアイテムだけを書く）。モデルはメインスレッドに置き、ModelWriter がその場でメモリ上のモデルを変え、データベースの書き込みを順に MODEL_EXECUTOR に送る。HomeModel.save・deferSaves・pinnedKeys・FolderIcon の onContentAdded/onContentRemoved・Launcher.saveItemPlacement をなくした。Folder の互換クラスは上流の addOrMoveItemInDatabase・updateItemLocationsInDatabaseBatch・FolderInfo.setOption(writer) に戻し、CellLayout.commitTempPlacement は上流の modifyItemInDatabase（ウィジェットのリサイズもここで書く）、HomeWorkspace.onDrop は modifyItemInDatabase、onDropExternal は addOrMoveItemInDatabase、Launcher.addFolder は addItemToDatabase。Workspace.removeWorkspaceItem は View を外すだけになり、削除は Launcher.removeItem（上流の本体を互換の Launcher に移した。削除のドロップ先の completeDrop とメニューの「削除」から）で行う。ショートカットの固定は BgDataModel.addItems/removeItems と読み込み後（sanitizeWidgetsShortcutsAndPackages 相当、HomeModel.sanitizeShortcutsOnce）にだけ更新するので、ドラッグ・フォルダー作成・メニューからのドラッグの途中では変わらない（Tex-536b の応急処置の置き換え）。replaceFolderWithFinalItem は LauncherDelegateImpl の本体（addOrMoveItemInDatabase と Launcher.removeItem）にしたが HomeController に残した。残り: HomeWorkspace を互換の Workspace にして Host をなくすこと、addFolder/getItemInflater を互換の Launcher に移すこと、replaceFolderWithFinalItem の互換への移動は Tex-ydua で（HomeWorkspace を Workspace.java の derived= にする作業と同じ）。端末では未確認

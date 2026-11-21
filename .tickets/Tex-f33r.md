---
id: Tex-f33r
status: open
deps: [Tex-dnjc]
links: []
created: 2026-11-21T09:18:13Z
type: task
priority: 1
assignee: Koji Murata
parent: Tex-neft
tags: [refactor]
---
# ホームのモデルを ItemInfo に一本化する

保存用の HomeItem と、Launcher3 のコードが扱う ItemInfo が、位置と中身を別々に持っている。View の tag には HomeItem が入っている（HomeGridView.kt:69）。そのため次の同期と回り道がある。
- HomeWorkspace.Host.itemInfoOf（HomeWorkspace.kt:64、HomeController.kt:760）と AppIconView.itemInfo
- onItemsMoved でレイアウトの位置を HomeItem に写す処理、フォルダの中身を callback で HomeItem.Folder.itemKeys に写す処理
- DeepShortcutView.java:178-186 の TextLauncher: 改変
上流（BgDataModel / ModelWriter）と同じく ItemInfo（WorkspaceItemInfo / FolderInfo。id・container・cell・span・rank は互換クラスにある）をメモリ上のモデルにし、tag にも ItemInfo を入れる。HomeModel は ItemInfo と DB の行の読み書きだけにする。Folder.willAccept(Object) を互換クラスに入れ、HomeWorkspace.kt:835 を置き換える。

## Acceptance Criteria

- [ ] HomeItem がなくなり、View の tag が ItemInfo
- [ ] itemInfoOf と AppIconView.itemInfo がない
- [ ] 置く・動かす・フォルダの作成と出し入れ・削除・ショートカット・アプリの削除による整理・再起動後の保持が今と同じ


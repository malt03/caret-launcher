---
id: Tex-f33r
status: closed
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


## Notes

**2026-11-21T10:06:18Z**

HomeItem をなくし、ホームのモデルを Launcher3 の ItemInfo にした。AppItemInfo / ShortcutItemInfo / WidgetItemInfo は data/HomeItemInfo.kt に移し、キーから読み込んで、見つかったアプリやショートカットを後から結び付ける（LoaderTask と同じ順）。HomeModel は ItemInfo のリスト（フォルダの中身は FolderInfo.contents）を DB の行と読み書きし、id・container・cell・span・rank は ItemInfo の欄に持つ。View の tag は ItemInfo になり、itemInfoOf、AppIconView.itemInfo、フォルダの中身の写し、DeepShortcutView.isPinnable の改変、DeepShortcutView の追加ボタンでフォルダを閉じる改変（上流の getOpenView に戻した）をなくした。Folder.willAccept を上流から互換クラスへ入れた。検索結果からのドラッグは上流の makeWorkspaceItem と同じく新しい ItemInfo を作る。エミュレータで、既存の保存データの読み込み、フォルダの開閉、フォルダからの取り出しと解除、検索結果からの配置、フォルダの作成、削除、アンインストールの取り消し、再起動後の保持を確認。
未再現の観察: アンインストールのダイアログを強制終了で閉じた直後の不規則なドラッグ（指を離す前に別のコマンドで動かした）で、同じアプリの View が2つになった。モデルには1つしか残らず、同じ ItemInfo が onDropExternal 経由で2回足された可能性がある（HomeWorkspace.onDrop は mDragInfo が null だと外からのドロップとして扱う。上流と同じ形）。同じ手順を何度か繰り返したが再現しなかった

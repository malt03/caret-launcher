---
id: Tex-3dnt
status: open
deps: []
links: [Tex-a8os, Tex-yh7w]
created: 2026-11-22T00:58:38Z
type: task
priority: 1
assignee: Koji Murata
parent: Tex-vs8t
---
# ホームを全部作り直さず、変わった部分だけを更新する（Launcher3 の部分バインド）

今はアプリの追加・更新・削除・停止、ショートカットの変化、アイコンの更新（updateIconCacheOnce の終わりを含む）のたびにホームの View を全部作り直す（LauncherActivity の appsListener / iconsListener → HomeLoader.rebuild）。そのたびにドラッグが取り消され、フォルダーやメニューが閉じる。Tex-i8xr のクラッシュの根で、Tex-a8os、Tex-536b、Tex-yh7w もここから出ている。

Launcher3 の形:
- 更新: PackageUpdatedTask / PackageTaskFactory → bindUpdatedWorkspaceItems → ModelCallbacks.bindWorkspaceItemsUpdated → LauncherBindableItemsContainer.updateContainerItems（開いているフォルダーも mapOverItems する。PopupContainer.dismissInvalidPopup も）
- 削除: bindWorkspaceComponentsRemoved → Workspace.removeItemsByMatcher（フォルダーは Folder.removeFolderContent）+ DragController.onAppsRemoved（ドラッグ中のアイテムが当てはまるときだけ取り消す。コピー済みだが呼ばれていない）
- アイコン: LauncherModel.onPackageIconsUpdated → CacheDataUpdatedTask（当てはまるアイテムだけ）
- ショートカット: ShortcutsChangedTask（変わったパッケージとユーザーのアイテムだけ、未インストールやアーカイブなら何もしない、そのパッケージだけ問い合わせ、固定されていないものだけ削除）
- 全部の作り直し（ModelCallbacks.startBinding、ドラッグの取り消しあり）はテーマやグリッドの変化での読み直しだけ

やること:
- util/LauncherBindableItemsContainer.kt をコピー（derived= でアプリペアとウィジェットの分岐を削る）し、互換の Workspace に mapOverItems を実装する
- Workspace.removeItemsByMatcher を移す
- AppRepository から変わったパッケージと種類（追加、更新、削除、使えない、停止）を渡し、HomeLoader でパッケージごとの PackageUpdatedTask をする
- AppIconLoader の変化の通知を「テーマが変わった」（全部作り直し）と「パッケージのアイコンが変わった」（部分更新）に分ける。形だけの変化は invalidate で済ませる
- HomeLoader.onShortcutsChanged を ShortcutsChangedTask.execute に近い形で移し直す
- HomeController.bindItemChanged をなくして updateContainerItems にする


---
id: Tex-3dnt
status: closed
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


## Notes

**2026-11-23T12:17:52Z**

2026-11-23: Tex-a8os の応急の直しが入った。アプリ・ショートカットの変化と rebuild の始めにドラッグを取り消し、HomeLoader.removeItems は中身を外したフォルダーだけを畳み、読み込み時の1つだけのフォルダーは Folder.bind（上流）で置き換わる。部分バインドにするときは、モデルでの畳み込みをやめて Folder.removeFolderContent → replaceFolderWithFinalItem の1本にし、全体の取り消しも外す

**2026-11-23T15:47:19Z**

2026-11-24: Tex-yh7w で HomeLoader.pruneMissingApps に WorkspaceItemProcessor / PackageUpdatedTask の付け替え（AppItemInfo.retarget）と使えないアプリの扱い（markUnavailable、FLAG_DISABLED_NOT_AVAILABLE）が入った。部分バインドにするときは、これをパッケージごとの作業（PackageUpdatedTask の OP_UNAVAILABLE / OP_UPDATE）に分け、2つの LauncherApps.Callback（AppRepository、ShortcutRepository）を ModelLauncherCallbacks の形にまとめる

**2026-11-23T17:28:13Z**

部分バインドにした。LauncherApps のコールバックを ModelLauncherCallbacks（data/、ref=）1つにまとめ、AppRepository がアプリの一覧とアイコンのキャッシュを更新してから PackageEvent をホームに渡す。ShortcutRepository は自分のコールバックを持たず、パッケージとショートカットを渡す。ホームは HomeModelTasks（PackageUpdatedTask・PackageTaskFactory の appsRemoved/appsUnavailable/appsSuspended/appsUnsuspended・CacheDataUpdatedTask・ShortcutsChangedTask・ModelTaskController の bindUpdatedWorkspaceItems/deleteAndBindComponentsRemoved を書き写した）で当てはまるアイテムだけを変えて ModelWriter で書き、HomeController.bindItemsUpdated（LauncherBindableItemsContainer.updateContainerItems を互換に移した。開いているフォルダも）と bindWorkspaceComponentsRemoved（Workspace.removeItemsByMatcher を互換の Workspace に足し、フォルダは Folder.removeFolderContent → replaceFolderWithFinalItem。DragController.onAppsRemoved、PopupContainer.dismissInvalidPopup も戻した）でバインドする。付け替え（起動画面が変わった）と使えないアプリ（markUnavailable）は PackageUpdatedTask / appsUnavailable に移った（読み込み時の pruneMissingApps は WorkspaceItemProcessor として残す）。ドラッグは、ドラッグ中のアイテムかドラッグ元のフォルダの中身が当てはまるときだけ、モデルを変える前に取り消す（全体の取り消しは外した）。AppIconLoader の通知はパッケージ（CacheDataUpdatedTask）と全体（テーマと形の変更、作り直し）に分け、updateIconCacheOnce の終わりも部分更新になった。HomeController.bindItemChanged はなくした。残り: 形だけの変化も上流（refreshAndReloadLauncher）と同じく全体を作り直す（invalidate では済ませていない）。読み込み・作り直しの途中に来た変化は従来どおりモデルで畳んで（HomeLoader.removeItems）作り直す。ItemClickHandler の persistRemoveItemsByMatcher も同じ。端末では未確認

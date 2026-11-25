---
id: Tex-ttho
status: closed
deps: []
links: []
created: 2026-11-24T13:35:59Z
type: task
priority: 2
assignee: Koji Murata
parent: Tex-op7u
tags: [launcher3, upstream, model]
---
# ⑦-2 ホームのモデルを裏のスレッドに戻し、LauncherModel とパッケージの変更の処理を上流のコピーにする

書き写しの最大の原因は、ホームのモデルをメインスレッドで持っていること（互換の ModelWriter.kt:39-44・BgDataModel.kt:47-51 は呼んだスレッドで変える。上流の ModelWriter.kt:214 は modelExecutor で動かす）。上流のタスクはメインスレッドでは動かせない（iconloaderlib の BaseIconCache.kt が assertWorkerThread）ので、アプリ側でスレッドを行き来している（AppIconLoader.kt:127-204、HomeLoader.kt:187-211、HomeModelTasks.kt:208-227・254-287）。

- ModelWriter・BgDataModel を上流のスレッドの扱いに戻し、印を減らす
- derived=: LauncherModel.kt（startLoader、LoaderTransaction、enqueueModelUpdateTask、onPackageIconsUpdated、updateAndBindWorkspaceItem）、ModelTaskController.kt、ModelLauncherCallbacks.kt、PackageUpdatedTask.java、PackageTaskFactory.kt、CacheDataUpdatedTask.java、ShortcutsChangedTask.kt、LauncherAppState.kt。LauncherModel の Dagger のコンストラクターは、derived の IconCache と同じく手で組み立てる
- 消える・縮む: ui/home/HomeModelTasks.kt、data/AppRepository.kt の大部分、data/ModelLauncherCallbacks.kt、ShortcutRepository の監視、AppIconLoader の非同期の包み
- 印: 個人のプロファイルだけ、promise・archived・web のショートカットなし、ウィジェットのモデルの更新なし、インストールのキューなし（Tex-iuyb までは）、段階的なダウンロードなし
- 先に直すこと: メインスレッドからモデルを読んでいるところ（HomeWidgets.kt:245、HomeController.kt:638-644）。モデルが変わる前にドラッグを取り消す直し（HomeController.kt:588-599、HomeModelTasks.kt:299-304、Tex-a8os）は、上流では裏でモデルを変えてから DragController.onAppsRemoved を呼ぶので、この順番を保てない。代わりの形を決める

リスク: これまで直したドラッグ・フォルダ・読み込みの競合（Tex-a8os、Tex-twu9、Tex-i8xr）が戻らないか。DB の形は変わらないのでデータが消えるリスクはない。規模: 大。


## Notes

**2026-11-24T23:41:21Z**

2026-11-25 設計の決定（⑦-2 と ⑦-3 は1つの移植としてまとめて行う。LoaderTask が裏のスレッドでモデルを作るため分けると二度手間）:
(1) 項目のクラスは上流のものにする。アプリは WorkspaceItemInfo（ITEM_TYPE_APPLICATION）、ウィジェットは LauncherAppWidgetInfo。AppItemInfo と WidgetItemInfo はなくす。homeKey は AppKey.of(intent.component, シリアル) を返す拡張関数、isAvailable は FLAG_DISABLED_NOT_AVAILABLE を見る拡張関数にする。
(2) LauncherModel は互換モジュールの derived（Kotlin）にする。アプリに1つ（LauncherAppState.getModel）で、Activity ごとではない。コールバックは HomeController が BgDataModel.Callbacks（ModelCallbacks の役）として実装する。
(3) BgDataModel・ModelWriter・ModelTaskController・ModelLauncherCallbacks・PackageUpdatedTask・PackageTaskFactory・CacheDataUpdatedTask・ShortcutsChangedTask・LoaderTask・LoaderCursor・WorkspaceItemProcessor・BaseLauncherBinder を上流から削ったコピーにする。全アプリ一覧の読み込み（AllAppsList）も LoaderTask.loadAllApps に移し、AppRepository は bindAllApplications を受ける側だけにする。
(4) GridSizeMigration は attemptMigrateDb の場所でモデルのスレッドから呼ぶ。
(5) 安全策: 上流の読み込みは解決できない行を消す（markDeleted/commitDeleted）ので、初回だけ favorites の表を同じ DB の favorites_backup_textlauncher に写してから読む（設定の印で1回だけ）。
(6) ドラッグの取り消し（Tex-a8os）: 上流と同じく裏でモデルを変え、ModelTaskController.deleteAndBindComponentsRemoved の後に UI 側で bindWorkspaceComponentsRemoved の頭でドラッグを取り消す（DragController.onAppsRemoved）。フォルダから出したアイテムを先に戻す扱いは、bindWorkspaceComponentsRemoved で取り消しを先に行うことで保つ。

**2026-11-24T23:47:50Z**

2026-11-25 進捗（未コミット、作業ツリーにある）: derived にしたもの = LauncherModel.kt（LauncherModel.java を削除）、model/ModelWriter.kt・IModelWriter.kt・ModelWriterFactory.kt・BgDataModel.kt（Callbacks と updateShortcutPinnedState を上流の形に）・ModelTaskController.kt・BaseLauncherBinder.java・ModelLauncherCallbacks.kt・tasks/PackageUpdatedTask.java・PackageTaskFactory.kt・CacheDataUpdatedTask.java・ShortcutsChangedTask.kt、AllAppsList に mFlags/getImmutableData。path= にしたもの = model/data/AppsListData.kt、ui/LauncherUiStateNotifier.kt・DefaultLauncherUiStateNotifier.kt、model/ModelCursorWrapper.kt、model/data/IconRequestInfo.java。Utilities.IS_DEBUG_DEVICE（false）と Flags の simplifiedLauncherModelBinding・bindModelUsingRepository・modelRepository（false）を足した。残り: LoaderCursor・WorkspaceItemProcessor・LoaderTask（LoaderTaskFactory を持つ）の derived、互換 WorkspaceItemInfo に足りない上流のメンバー、アプリ側（HomeController が Callbacks を実装、HomeModel/HomeLoader/HomeModelTasks/data/ModelLauncherCallbacks/PackageEvent の削除、AppRepository を bindAllApplications の受け手に、AppItemInfo/WidgetItemInfo の置き換え、GridSizeMigration を attemptMigrateDb の場所へ、初回の favorites の控え）。まだビルドしていない

**2026-11-25T00:10:55Z**

2026-11-25: 移した（Tex-eg3z と一緒）。derived: LauncherModel.kt、model/ModelWriterFactory・ModelTaskController・ModelLauncherCallbacks・BaseLauncherBinder・LoaderTask・LoaderCursor・WorkspaceItemProcessor、tasks/PackageUpdatedTask・PackageTaskFactory・CacheDataUpdatedTask・ShortcutsChangedTask、ModelWriter・IModelWriter・BgDataModel を上流の形に作り直した。path=: AppsListData・IconRequestInfo・ModelCursorWrapper・LoaderMemoryLogger・ui/LauncherUiStateNotifier・DefaultLauncherUiStateNotifier・IOUtils。アプリ側は HomeModel・HomeLoader・HomeModelTasks・data/ModelLauncherCallbacks・ShortcutRepository・AppItemInfo・WidgetItemInfo をなくし、HomeController が BgDataModel.Callbacks を実装、AppRepository は bindAllApplications の受け手、HomeLoaderHooks（LoaderTask.Hooks）がグリッドの移し替えとゴーストのウィジェットと最初の1回の favorites の控え。Tex-a8os の直しは bindWorkspaceComponentsRemoved の頭でのドラッグの取り消しと、replaceFolderWithFinalItem のビューの親の確認に置き換えた。エミュレータで読み込み・追加・移動・フォルダ作成・再起動・削除・一時停止と再開・androidTest を確かめた

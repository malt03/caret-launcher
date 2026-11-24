---
id: Tex-ttho
status: open
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


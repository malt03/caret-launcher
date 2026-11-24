---
id: Tex-eg3z
status: open
deps: [Tex-ttho]
links: []
created: 2026-11-24T13:35:59Z
type: task
priority: 2
assignee: Koji Murata
parent: Tex-op7u
tags: [launcher3, upstream, model]
---
# ⑦-3 読み込み処理（LoaderTask・LoaderCursor・WorkspaceItemProcessor）を上流のコピーにする

data/HomeModel.kt の loadBlocking と ui/home/HomeLoader.kt（455 行）の大部分を、上流の LoaderTask.java（833 行）・LoaderCursor.kt・WorkspaceItemProcessor.kt（846 行、残すのは 40% ほど）・BaseLauncherBinder の derived に置き換える。
- リリースの設定では bind_model_using_repository が無効（aconfig の既定）なので、LauncherModel.kt:385-390 の useModelRepositoryBinding() は false。上流の addCallbacksAndLoad／startLoader（200-243）の経路を写す
- 再バインドの世代は bindId と LoaderTransaction に、updateIconCacheOnce は loadAllSurfacesOrdered の 2・3 段目になる
- HomeController は BgDataModel.Callbacks（bindCompleteModelAsync など）を実装する。ModelCallbacks はほとんどがページとホットシートなので、startBinding・更新・削除の部分だけ derived にするか
- 印: screenId は 0、ホットシートなし、checkItemPlacement は GridSpec、復元・セッション・アプリのペア・ホーム画面のファイル・予測・フォルダの名前・既定のレイアウトなし
- グリッドの移し替えは attemptMigrateDb の場所に ref= のフックとして残す（GridSizeMigration は書き写しのまま）

リスク（大事）: 上流の読み込みは解決できないアイテムを DB から消す（LoaderCursor.markDeleted／commitDeleted、WorkspaceItemProcessor.kt:331-335 の「固定したショートカットが見つからない」など）。初回は favorites の表を控えておくか、消さずに記録だけにする。規模: 大。⑦-2 の後。


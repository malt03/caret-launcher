---
id: Tex-mci4
status: closed
deps: []
links: []
created: 2026-11-24T13:35:59Z
type: task
priority: 3
assignee: Koji Murata
parent: Tex-op7u
tags: [launcher3, upstream, model]
---
# ⑦-1 ModelDbController を上流のコピーにする

互換の model/ModelDbController.kt（143 行）は上流（409 行）の 25% ほどと DatabaseHelper の id の処理を持つスタンドイン。上流の ModelDbController.kt の derived にし、LauncherDatabase を渡すところを TextLauncher: 印にする。復元、既定のレイアウト、グリッドごとの DB の移し替えは削る。

上流の DatabaseHelper を今の DB ファイルに使わないこと: 上流の既定のグリッドの DB も launcher.db（res/xml/device_profiles.xml、LauncherFiles.LAUNCHER_DB）で、こちらの LauncherDatabase.kt:191 と同じ名前。DatabaseHelper.java:74 の SCHEMA_VERSION は 32/34 で、こちらの v3 のファイルを開くと onUpgrade（146-278）が 12 未満を扱わず createEmptyDB に落ち、ホームの配置が消える。DB の中身は変わらないのでデータが消えるリスクはない。規模: 小。いつでもできる。


## Notes

**2026-11-24T23:38:11Z**

2026-11-25: 移した。ModelDbController.kt と LauncherDbUtils.kt を derived、SQLiteTable.kt を path=。DatabaseHelper の id の数え方は ModelDbController の末尾の印付きの区画。query は上流と同じく getTable() 経由で db_name を extras に付ける。エミュレータで connectedDebugAndroidTest（DB の移行テスト）が通り、全アプリ一覧からホームに置いたアプリが再起動後も残ることを確かめた

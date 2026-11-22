---
id: Tex-r705
status: open
deps: [Tex-t2ea]
links: []
created: 2026-11-22T15:43:20Z
type: feature
priority: 3
assignee: Koji Murata
parent: Tex-ogmk
tags: [home, launcher3]
---
# Launcher3 のウィジェットピッカー（Compose 版）を移植する

Tex-h3ld の調査結果に基づく。今は OS 標準の選択画面（ACTION_APPWIDGET_PICK）を使っている。android-17.0.0_r1 のピッカーは Compose 版だけで、modules/widgetpicker（65 ファイル・約 9,800 行）と Launcher 側のつなぎ（23 ファイル・約 2,700 行）からなり、Dagger で組み立てる。移植には Compose（Material3）を依存に足し、Dagger を手で組み直し、プレビューの読み込みも持ってくる。ピッカーからドラッグして置く操作もここで入れる。


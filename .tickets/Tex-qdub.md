---
id: Tex-qdub
status: open
deps: []
links: []
created: 2026-11-22T00:31:27Z
type: feature
priority: 2
assignee: Koji Murata
tags: [home, gesture]
---
# ホームの下スワイプで通知シェードを開く

Pixel Launcher（Launcher3 Quickstep の StatusBarTouchController）と同じく、ホーム画面のどこからでも下にスワイプすると通知シェードを開く。

- Launcher3: NORMAL 状態で、縦の移動がタッチスロップを超え、横より大きいと下スワイプとみなし、以降のタッチを SystemUiProxy.onStatusBarTouchEvent で System UI に渡す（指に追従する）
- TextLauncher: SystemUiProxy は Recents を担うランチャーしか使えないので、StatusBarManager.expandNotificationsPanel()（隠しAPI、EXPAND_STATUS_BAR 権限）で開く。指には追従せず、検出後にアニメーションで開く。Android 17 で使えるかは未確認
- ホームの縦スクロールとの衝突: 一番上までスクロールしているときだけシェードを開く（スクロールできる余地があるときは従来どおりスクロール）
- 判定の条件は StatusBarTouchController.canInterceptTouch に合わせる（編集中、フォルダーやメニューが開いているとき、全アプリ一覧が出ているときは除く）


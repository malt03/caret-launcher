---
id: Tex-qdub
status: closed
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


## Notes

**2026-11-23T16:27:51Z**

StatusBarTouchController を互換クラス（derived=）として移し、ドラッグの次のタッチコントローラーにした。StatusBarManager.expandNotificationsPanel は android-17.0.0_r1 のソースで @UnsupportedAppUsage（maxTargetSdk なし）、frameworks/base/boot/hiddenapi の max-target のリストにもないので、アプリから呼べる（unsupported 扱い）。サービス側の確認は通常権限の EXPAND_STATUS_BAR だけ。判定した時点で一度呼んでシェードを開く（指には追従しない）。NORMAL 状態の判定は canShowOptions と同じで、ホームが一番上までスクロールしているとき、2パネルでは左の一覧の外から始めたときだけ。実機での確認はまだ（docs/spec.md 5.6）

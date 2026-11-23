---
id: Tex-iuyb
status: open
deps: []
links: [Tex-4cve]
created: 2026-11-23T06:16:03Z
type: feature
priority: 3
assignee: Koji Murata
parent: Tex-ogmk
tags: [home, launcher3]
---
# ウィジェットピッカーのショートカット作成とピン留めの要求

ピッカーの一覧に出るショートカットの作成画面（PendingAddShortcutInfo、ACTION_CREATE_SHORTCUT、REQUEST_CREATE_SHORTCUT）と、ほかのアプリからのピン留めの要求（LauncherApps.PinItemRequest、showWidgetsForPinRequest、PinRequestHelper）。Tex-r705 では、ショートカットの「追加」はホームの addToWorkspace が受けず何もしない。PinShortcutRequestActivityInfo は一覧の表示に要る分だけの互換クラス。


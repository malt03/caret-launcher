---
id: Tex-c7qj
status: open
deps: []
links: [Tex-4cve]
created: 2026-11-23T07:00:29Z
type: task
priority: 3
assignee: Koji Murata
parent: Tex-ogmk
tags: [home, launcher3]
---
# ウィジェットピッカーの互換層で省いた部分

ピッカーの移植（Tex-r705）で、次の部分は互換クラスで止めている。
- ユーザー（仕事用プロファイル）の変化：UserCache.getUserChanges は通知しない。ピッカーを開いている間にプロファイルが増減・休止しても一覧が変わらない（開き直せば変わる）
- InvariantDeviceProfile とデバイスプロファイルはホームが作るときに設定する。ホームより先にピッカーだけが作り直される場合（プロセスが終わったあとなど）は未設定のまま。起きるかどうか、起きたら落ちるかは未確認。Launcher3 は IDP を Dagger で端末の情報から作る
- 記録（StatsLogManager、LauncherWidgetPickerCuiReporter の送り先）は何もしない
- ぼかし（Flags.enableWidgetPickerBlur）はリリース設定で無効なので、上流と同じく使わない


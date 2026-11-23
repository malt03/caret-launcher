---
id: Tex-c7qj
status: closed
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


## Notes

**2026-11-23T16:43:43Z**

UserCache.getUserChanges: 上流と同じプロファイルのブロードキャスト（MANAGED_PROFILE_*、PROFILE_*）を UI_HELPER_EXECUTOR で受け、前の状態（CachedUserInfo に解除・休止の状態を足して比べる）と変わったユーザーを通知するようにした。ピッカーを開いたままの仕事用プロファイルの休止・追加・削除がタブに反映される。IDP: ホームより先にピッカーが作られると getDeviceProfile が null を返し、WidgetPickerActivity の mDeviceProfile や WidgetsRepositoryImpl の大きさの計算で落ちるはずだった（端末では未確認）。上流が端末の情報から IDP を作るのと同じく、アプリが起動時に渡す GridInitializer が未設定のときに GridSpec からグリッドとプロファイル（セルの大きさは最小のウィンドウの値）を作るようにした。ホームが作られれば従来どおりホームが設定し直す。記録: 上流もクイックステップのない構成は NoOpLoggerModule で何も記録せず、記録先（statsd、InteractionJankMonitor）はシステムのアプリ専用なので、何もしないままにした。ぼかしはリリース設定で無効のまま

---
id: Tex-00l4
status: open
deps: []
links: []
created: 2026-11-24T13:35:59Z
type: task
priority: 2
assignee: Koji Murata
parent: Tex-op7u
tags: [launcher3, upstream]
---
# ④ 状態の管理を上流の StateManager・LauncherState のコピーにし、編集中の縮小を上流の形にする

ui/statemanager/StateManager.kt（217 行、上流 743 行）と ui/LauncherState.kt（enum）を上流のコピーにする。android-17 の StatefulContainer（statemanager/StatefulContainer.java:38）は ActivityContext を継ぐインターフェースなので、互換の Launcher（Activity ではない）に実装させれば上流の StateManager<LauncherState, Launcher> が動く（getStateManager・collectStateHandlers・getScrimView はもうある）。

- derived=: statemanager/StateManager.java、LauncherState.java（NORMAL・SPRING_LOADED・ALL_APPS だけ）、src_no_quickstep の AllAppsState.java、states/SpringLoadedState.java。LauncherUiState は derived か setLauncherUiState を削る
- path=: BaseState、StatefulContainer、anim/AnimatorPlaybackController（415 行、自己完結）、PendingAnimation・AnimatedPropertySetter・PropertySetter・AnimatorListeners（今の ref= の PendingAnimation を置き換える）、src_no_quickstep の StateManagerProtoLogProxy
- 全アプリ一覧の進み具合の向きを上流と同じにする（ALL_APPS = 0）。今は逆にしていて、印があちこちに要る
- 印: 上流は StartAnimRunnable を post して onStateSetStart(state) を呼び、元の状態と animated を渡さない。LauncherActivity.kt:420-446 はどちらも使うので、印にするか getCurrentStableState() から読む
- 続けて: SpringLoadedListener.kt（185 行）を上流の WorkspaceStateTransitionAnimation の derived（StateHandler。縮小・移動・ページの背景・スクリム）にし、ドラッグで SPRING_LOADED に入るのを上流と同じ animated な goToState にする（今は LauncherActivity.kt:570 で animated=false にして外で動かしている）。印: isEditLayout、ページはグリッド全体

リスク: onStateSetStart でのフォーカスとキーボードのタイミング、ウィジェットのリサイズ枠が編集モードを保つこと。規模: 中。


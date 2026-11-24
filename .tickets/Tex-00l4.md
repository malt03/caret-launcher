---
id: Tex-00l4
status: closed
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


## Notes

**2026-11-24T14:38:31Z**

2026-11-24: 前半を済ませた。上流の StateManager（derived。アプリのスワイプが性質を自分で動かすので、setCurrentUserControlledAnimation(Runnable)・onUserControlledTransitionStart/End・hasUserControlledSwipe を印付きで足した）、LauncherState（NORMAL・SPRING_LOADED・ALL_APPS だけの derived）、SpringLoadedState（derived）、quickstep の AllAppsState（derived）。BaseState・StatefulContainer・AnimatorPlaybackController・PendingAnimation・AnimatedPropertySetter・PropertySetter・AnimatorListeners・SpringProperty・ScrimColors(Evaluator)・StateManagerProtoLogProxy（src_no_quickstep）は path=。互換の Launcher が StatefulContainer として StateManager を持ち、HomeActivityContext が onStateSetStart/End に移る前の状態とアニメーションの有無を添えてアクティビティに渡す。アプリの StateManager.kt と LauncherState.kt は削除。残り: SpringLoadedListener を上流の WorkspaceStateTransitionAnimation（Workspace の StateHandler）にすること。これは Workspace のコピー（②、Tex-ydua）と一緒にやる。エミュレータ（折りたたみ・スマホ）でドラッグ、全アプリ一覧のスワイプ・戻る・メニューからの開閉を確認

**2026-11-24T16:03:01Z**

2026-11-25: 残りを移した。SpringLoadedListener を削除し、WorkspaceStateTransitionAnimation を derived（ホットシート・ページのインジケーター・ページの透明度と横移動・ヒント・システム UI の幕を削る。壁紙の幕は DragLayer.getWorkspaceDragScrim のビュー）。Workspace が StateHandler（setState/setStateWithAnimation、onStart/EndStateTransition で updateAccessibilityFlags）。SPRING_LOADED への出入りは上流の goToState の場所に戻した（onDragStart、onDrop、onDropExternal、Folder.onDrop、DropTargetHandler.onDropAnimationComplete、LauncherDragController.exitDrag）。ピッカーからの保留中の追加だけは印付きで落としたアニメーションの後に NORMAL へ。SpringLoadedState はキーボードを下ろしたページの高さ（DeviceProfile.getCellLayoutHeight）で計算し、HomeWorkspace は pivot をその中央に固定。リサイズ枠の編集モードは NORMAL のまま（HomeController.setEditLayout → Host.onEditLayoutChanged）。全アプリ一覧の遷移は Workspace の縮小を持たず（スワイプ中だけ自分で動かす）、DepthController は SPRING_LOADED の奥行きも状態から、ホームのぼかしは LauncherState.shouldBlurWorkspace に従う。ついでに Tex-44r9 で入ったウィジェットピッカーでの a11y クラッシュ（BaseDragLayer.getContainer）を修正。

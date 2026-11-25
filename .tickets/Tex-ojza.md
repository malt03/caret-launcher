---
id: Tex-ojza
status: closed
deps: [Tex-00l4]
links: []
created: 2026-11-24T13:35:59Z
type: task
priority: 3
assignee: Koji Murata
parent: Tex-op7u
tags: [launcher3, upstream, drawer]
---
# ⑧-1 全アプリ一覧のスワイプと開閉を上流の AbstractStateChangeTouchController・AllAppsSwipeController・AllAppsTransitionController のコピーにする

ui/allapps/AllAppsSwipeController.kt（383 行、上流 147+456 行）をなくし、AllAppsTransitionController.kt（197 行）を縮める。
- derived=: touch/AbstractStateChangeTouchController.java、touch/AllAppsSwipeController.java、allapps/AllAppsTransitionController.java。動きは createAnimationToNewWorkspace から作る
- 書き写しになっている本当の理由: 閉じた検索欄がキーボードに付いて動くので、ジェスチャーの途中で移動の幅が変わること、48dp の溜め。印にするもの: 進み具合を検索欄の位置から取る（deltaProgress か updateProgress）、溜めと追いつき、しきい値まで mCurrentAnimation.dispatchOnStart() を遅らせる（そこで状態とキーボードが変わる）、canInterceptTouch の追加の確認、記録・トラックパッド・デスクトップを削る
- AllAppsTransitionController.setProgress は印付きで検索欄とシートを置き、getShiftRange は closedBarTop - openBarTop。スクリム・透明度・setupViews は削る。onBackProgressed は残す（一覧の予測型の戻る操作。LauncherActivity.kt:231-248 の手書きの戻るにはない）
- 上流からただで入るもの: FlingBlockCheck、ジェスチャーが反対の状態に入ったときの作り直し、開始時の進み具合の補正、止められたフリングのあとの長めの時間

リスク: 手触り（docs/spec.md 6.2）が今と同じになるか。実機で比べる必要がある。規模: 中〜大。④ の後。


## Notes

**2026-11-25T00:14:27Z**

2026-11-25: 移した（fork で作業、ブランチは本体がマージ）。derived=: touch/AbstractStateChangeTouchController.java、touch/AllAppsSwipeController.java、allapps/AllAppsTransitionController.java。path=: util/FlingBlockCheck.java。LauncherAnimUtils に newCancelListener・blockedFlingDurationFactor・TABLET_BOTTOM_SHEET_SUCCESS_TRANSITION_PROGRESS、Interpolators に INSTANT・FINAL_FRAME を足した。進み具合は上流の向き（0 が一覧）にし、アプリの ui/allapps/AllAppsTransitionHost が Host としてフィールドとシートを置く。StateManager の互換クラスから setCurrentUserControlledAnimation(Runnable)・hasUserControlledSwipe を消し、onUserControlledTransitionStart/End だけ残した。印: フィールドの位置からの進み具合、48dp の溜めと追いつき、dispatchOnStart を溜めを越えたとき（開くとき）か離したときまで遅らせる、戻るときは成功のリスナーだけに取り消しを伝える（dispatchOnCancel だとコントローラーが止まる）。Pixel9ProFold の閉じた画面で開く・溜めで戻る・越えて戻す・閉じる・メニュー・戻るを確かめた。動いている最中に触る操作と手触りは未確認

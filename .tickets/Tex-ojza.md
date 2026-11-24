---
id: Tex-ojza
status: open
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


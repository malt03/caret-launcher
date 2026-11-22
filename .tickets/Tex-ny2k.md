---
id: Tex-ny2k
status: open
deps: []
links: [Tex-rpfa]
created: 2026-11-22T00:57:39Z
type: bug
priority: 1
assignee: Koji Murata
parent: Tex-pa8s
---
# 全アプリ一覧のスワイプの不具合3件

1. 引っ掛かり（48dp）の途中で指を離しても onSettling / onSettled の副作用が走る。開くのをやめると prepareAllAppsClose で入力した検索語が消え（検索フィールドの上で縦に少しぶれただけで起きる）、閉じるのをやめると prepareAllAppsOpen で一覧内の検索語が消え、キーボードが下りる。Launcher3 の AbstractStateChangeTouchController.onSwipeInteractionCompleted は、状態が変わらないときは何もしない
2. 開閉のアニメーション中に触るとシートが途中で止まる。ACTION_DOWN でアニメーションを止める（終わりの処理も外れる）が、onDragEnd で finishedScrolling 済みなので検出器が SETTLING でなく、タップではドラッグが始まらない。Launcher3 は mCurrentAnimation がある間は必ず奪い、onSwipeInteractionCompleted の clearState まで SETTLING のまま、onDragStart でアニメーションを止める
3. 一覧の中で検索しているとき、閉じられるかの判定（LauncherActivity.canCloseFrom）が、裏に隠れた A〜Z の一覧で判定している。Launcher3 の ActivityAllAppsContainerView.shouldContainerScroll は getActiveRecyclerView（検索中は検索の一覧）で判定する

直し方: 1 は状態が変わらないときに副作用を飛ばす。2 は AbstractStateChangeTouchController の奪い方に合わせる（TouchController 化のチケットで直る）。3 は shouldContainerScroll を全アプリ一覧の入れ物に移す


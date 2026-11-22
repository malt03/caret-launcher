---
id: Tex-rpfa
status: open
deps: []
links: [Tex-ny2k, Tex-ypdf]
created: 2026-11-22T00:58:38Z
type: task
priority: 2
assignee: Koji Murata
parent: Tex-vs8t
---
# 全アプリ一覧の開閉を Launcher3 の形に分ける（TouchController、DepthController、状態の管理）

- 開閉のスワイプがドラッグレイヤーのタッチコントローラーの一つになっていない。LauncherRootLayout に touchInterceptor と allAppsTouchInterceptor の2つの横取りがあり、子のキャンセルも自前で書いている。canSwipe は getTopOpenView の確認を手で書き直している。Launcher3 は Launcher.createTouchControllers で [DragController, AllAppsSwipeController, …] を BaseDragLayer に渡し、findActiveController が優先度とキャンセルを扱う（互換の BaseDragLayer に setControllers はある）
- 奥行き・ぼかし・開き具合がレイアウトのクラス（LauncherRootLayout）にある: allAppsProgress、applyAllAppsProgress、springLoadedDepth、ぼかしのリスナー、mapDepthToBlur。window にも (context as? Activity) で触っている。Launcher3 は AllAppsTransitionController.setProgress が開き具合を当て、BaseDepthControllerImpl が stateDepth（MultiProperty を Float::max でまとめる）、ぼかしの可否、mapDepthToBlur、applyDepthAndBlur を持つ
- ALL_APPS_SHEET_DEPTH を PathInterpolator(0,0,.2,1) で手作りしている（Interpolators.DECELERATED_EASE。互換の Interpolators にない）。ホームの縮小が contentArea.scaleX と WORKSPACE_SCALE_PROPERTY_FACTORY の2通り
- 状態の持ち主が複数: 一覧が開いているか（isAllAppsOpen、root.isAllAppsShown、transition.progress >= 0.5）、編集中か（isEditMode、SpringLoadedListener.editing、root.isEditLayout）。LauncherActivity が検索バーの振る舞い（AppsSearchContainerLayout / AllAppsSearchBarController 相当）、スワイプの当たり判定、ハプティック、結果の切り替え、起動まで持っている

やること:
- AllAppsSwipeController に TouchController を実装させ（AbstractStateChangeTouchController の形）、overlay.setControllers(dragController, allAppsSwipe) で登録する。Tex-ny2k の 2 も直る
- DepthController を作り（一覧と編集中の奥行きを max でまとめる、ぼかしのリスナー、ウィンドウのぼかしと RenderEffect）、開き具合と applyAllAppsProgress を AllAppsTransitionController に移す。DECELERATED_EASE を互換の Interpolators に足す。LauncherRootLayout には測定・配置・キーボードの追従だけを残す
- 小さな状態の管理（NORMAL / ALL_APPS / SPRING_LOADED と goToState(state, animated, callback)。LauncherState / StateManager 相当）を作り、戻る・ホーム・メニュー・スワイプから使う。Tex-qdub（下スワイプ）の canInterceptTouch もここで判定できる
- 検索フィールドの振る舞いを SearchBarView かコントローラーに移す


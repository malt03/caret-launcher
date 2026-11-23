---
id: Tex-rpfa
status: closed
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


## Notes

**2026-11-23T17:01:44Z**

Launcher3 の形に分けた。状態は StateManager（ui/statemanager、NORMAL / SPRING_LOADED / ALL_APPS、goToState(state, animated, callback)、currentStableState）が持ち、戻る・ホームボタン・onStop・メニューの「アプリのリスト」・スワイプ・編集の始まりと終わり（onEditModeChanged）はここを通る。isAllAppsOpen と isEditMode はなくした。検索フィールドのフォーカスとキーボードは Launcher.onStateSetStart / onStateSetEnd に当たるアクティビティのフックで（上流と違い、移る前の状態とアニメーションするかを渡し、ジェスチャーでないアニメーションもすぐに状態を移す）。状態のハンドラーは AllAppsTransitionController（縦の位置。LauncherRootLayout から applyAllAppsProgress を移した。ホームの縮小は contentArea.scaleX をやめ WORKSPACE_SCALE_PROPERTY_FACTORY の SCALE_INDEX_WORKSPACE_STATE に）と DepthController（ui/statehandlers。stateDepth と編集中の奥行きを MultiPropertyFactory の max でまとめる、ぼかしのリスナー、mapDepthToBlur、ウィンドウのぼかしと RenderEffect）。ジェスチャーでないアニメーションは互換の PendingAnimation（上流の add / addFloat / setFloat / addEndListener / buildAnim を入れた）に各ハンドラーが setStateWithAnimation で加え、補間は QuickstepAtomicAnimationFactory と同じく applyNormalToAllAppsAnimConfig / applyAllAppsToNormalConfig（StateAnimationConfig は third_party にコピー、DECELERATED_EASE・DECELERATE_1_7・reverse を互換の Interpolators に追加）。AllAppsSwipeController は TouchController になり、Launcher.createTouchControllers と同じく [ドラッグ, 全アプリのスワイプ, 通知シェード] の順で overlay.setControllers に渡す（LauncherRootLayout の allAppsTouchInterceptor はなくした）。検索フィールドの mSearchQueryBuilder・preDispatchKeyEvent・clearSearchResult は SearchBarView（AppsSearchContainerLayout 相当）へ。上流からコピーしなかったもの: AnimatorPlaybackController と AbstractStateChangeTouchController の本体。キーボードが下りるにつれて閉じた位置が動き、48dp の引っ掛かりもあるので、スワイプは指の位置から進み具合を出して状態の性質を自分で動かす（setUserControlledProgress）。小さな違い: 検索語があるときに検索フィールドから上にスワイプした引っ掛かりの間、検索結果はもう縮まない（縮むのはホームのグリッドだけ。量は 1% 未満）。ジェスチャーでないアニメーションが途中から始まるとき、奥行きと縮小は上流どおり今の値から補間する。ビルドと単体テストのみ確認（端末では未確認）

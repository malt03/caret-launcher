---
id: Tex-17s1
status: open
deps: [Tex-hibk, Tex-9ipm, Tex-bqud, Tex-68f3]
links: []
created: 2026-11-20T11:16:26Z
type: task
priority: 2
assignee: Koji Murata
parent: Tex-pa8s
tags: [drawer]
---
# 全アプリ一覧の開閉ジェスチャーと手触り

検索フィールドごと引き上げて一覧を出す動きを作る。
- 開く: 検索フィールドの上スワイプ、ホームの一番下でさらに上へ引っ張る（スクロールしない配置ならどこでも）。フリングでは開かない
- 閉じる: 戻る操作、検索フィールドの下スワイプ、一覧の一番上でさらに下へ引っ張る、ホームボタン
- 手触り: 閾値まで抵抗 → 閾値でハプティックと一緒にクッと持ち上がる → 指に追従。手前で離すとバネで戻る。閉じるときも同じ
- キーボードは閾値を越えたときに下ろす（WindowInsetsAnimationCompat で同期）
- 開いた状態: フィールドは上端に張り付き、フォーカスを外す。キーボード監視も止める
- 閉じたら入力をクリアし、フィールドをキーボードの直上に戻してフォーカスとキーボードを戻す
- アプリを起動して戻ってきたら、一覧は閉じた状態
実装方針は付録（onInterceptTouchEvent、NestedScrollingParent3、SpringAnimation、ハプティック定数）
仕様: docs/spec.md 6.1、6.2、6.4、6.6、付録

## Launcher3 からの移植

- 縦スワイプの判定と横取りは touch/SingleAxisSwipeDetector.java、touch/BaseSwipeDetector.java をコピーして使う
- 端での引っ張りの感触は util/OverScroll.java、util/TranslateEdgeEffect.java をコピーして使う
- 開閉の組み立て方（進み具合とスクリム・ホームの連動）は allapps/AllAppsTransitionController.java を参考にする
- 速度による開く/戻るの判定、フリックの閾値は touch/AllAppsSwipeController.java、touch/AbstractStateChangeTouchController.java を参考にする
- バネは anim/SpringAnimationBuilder.java、anim/FlingSpringAnim.java の値に合わせる
- 引っ掛かりの部分は Launcher3 にないので自前で足す
- ハプティックは util/MSDLPlayerWrapper.java（msdllib）を使えるか調べる

## Acceptance Criteria

- [ ] 検索フィールドを上にスワイプ → 少し抵抗があり、閾値でハプティックと一緒に持ち上がって一覧が出る
- [ ] 閾値の手前で離す → 元に戻る
- [ ] ホームを一番下までスクロールしてさらに引っ張る → 同じように開く
- [ ] フリングで一番下に着いただけ → 開かない
- [ ] フィールドの下スワイプ、一覧の一番上での下スワイプ、戻る操作のどれでも閉じる
- [ ] 閉じた後、フォーカスが当たってキーボードが出ている
- [ ] 検索フィールドのタップ、横方向のカーソル移動、長押しの選択は今までどおり動く
- [ ] 一覧を開いている間はキーボードが閉じても出し直さない（Tex-bqud から移動）

## Notes

**2026-11-20T12:14:50Z**

移植の範囲は docs/launcher3-porting.md の表で確定（Tex-wgyx）

**2026-11-21**

Tex-hibk で、全アプリ一覧は開いた状態と閉じた状態の切り替えだけ入った（LauncherRootLayout.isAllAppsOpen、LauncherActivity.openAllApps / closeAllApps）。このチケットで:
- 開閉のアニメーションと進み具合（AllAppsTransitionController）、一覧の背景（上流のパネルとスクリム）を足す
- デバッグ用の EXTRA_SHOW_ALL_APPS（ホームの intent で一覧を開く）を消す

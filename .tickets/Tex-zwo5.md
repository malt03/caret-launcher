---
id: Tex-zwo5
status: open
deps: [Tex-00l4, Tex-q3gh, Tex-ojza]
links: []
created: 2026-11-24T13:35:59Z
type: task
priority: 4
assignee: Koji Murata
parent: Tex-op7u
tags: [launcher3, upstream]
---
# ⑧-2 メインの Activity を上流の Launcher.java のコピーにする（任意）

互換の Launcher（332 行、Activity ではない）を abstract class Launcher extends StatefulActivity<LauncherState> の derived にし、LauncherActivity を QuickstepLauncher のようにそのサブクラスにする（互換のモジュールはアプリのコードを参照できないので abstract のまま）。
- 一緒に: StatefulActivity、LauncherRootView の derived、ホームの Activity が本物の BaseActivity になり互換の addEventCallback（Launcher.java:303-319）と LauncherTracker（64-72）がいらなくなる。DragLayer をルートの View にし、BaseDragLayer の重ね合わせの工夫（112-133、LauncherRootLayout.kt:230-248）と「ドラッグレイヤーはキーボードの上まで」（LauncherRootLayout.kt:311-314・336）をインセットにする
- 縮む: LauncherActivity の生存期間と戻る操作の大部分、HomeActivityContext（サブクラスに入る）、LauncherContextWrapper
- 得られるもの: getOnBackAnimationCallback による予測型の戻る操作
- 止めているもの: 持ち主が逆（今は HomeController が Launcher を作る、HomeController.kt:183。home.host. の呼び出しが 13 か所ほど）、Kotlin と Java の分かれ目、onIdpChanged でなく onConfigurationChanged で recreate()。2,903 行（import 238）は大きく削って 600〜900 行ほど

規模: 大。④・⑤・⑧-1 が落ち着いてから、やるかどうかを決める。


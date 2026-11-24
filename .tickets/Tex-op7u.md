---
id: Tex-op7u
status: open
deps: []
links: [Tex-44r9, Tex-iuyb]
created: 2026-11-24T13:35:59Z
type: epic
priority: 2
assignee: Koji Murata
tags: [launcher3, upstream]
---
# 上流のコピーへの大きい置き換え（2026-11-24 の設計見直し）

アプリの約 9,400 行のうち半分ほどが Launcher3 の書き写し（ref=）で、上流の更新に追従しない。3つの層を上流（android-17.0.0_r1）と突き合わせ、大きい単位で derived= のコピーに置き換える順番を決めた。

層ごとの土台:
- ホームの表示層: ホームの CellLayout が画面につながっていない計算用の部品で、HomeGridView が代わりに描いている（compat CellLayout.java 78-88、docs/launcher3-porting.md の方針）。これをつなぐと Workspace・TalkBack・長押しとウィジェット追加の流れが上流のコピーで乗る
- 状態の管理: android-17 の StatefulContainer は ActivityContext を継ぐインターフェースなので、Activity でない互換の Launcher のままで上流の StateManager が動く
- モデル: ホームのモデル（BgDataModel・ModelWriter）をメインスレッドで持っているのが書き写しの最大の原因（iconloaderlib の BaseIconCache は assertWorkerThread）。上流と同じく MODEL_EXECUTOR に戻すと LauncherModel・タスク・LoaderTask をコピーにできる

順番: ①CellLayout をつなぐ → ②Workspace と LauncherDragController（Tex-ydua）→ ③TalkBack（Tex-44r9）→ ④状態の管理 → ⑤長押しとウィジェット追加（その上に Tex-iuyb）→ ⑥InvariantDeviceProfile → ⑦モデル（ModelDbController → 裏のスレッドと LauncherModel → LoaderTask）→ ⑧全アプリ一覧のスワイプ。各段の終わりにエミュレータで確かめ、実機の確認項目を docs/device-checklist.md に足す。

書き写しのまま残すもの（上流の形が合わず、コピーにしても削除の印ばかりになる）: 検索まわり、LauncherRootLayout（キーボードに合わせた配置）、AllAppsSheet・AllAppsView（検索欄がシートの外）、DepthController の背景のぼかし（上流は SurfaceControl・BlurUtils などアプリに使えない仕組み）、DeviceProfile の互換クラス（セルの大きさはキーボードを下ろしたホームの高さから決まる）、GridSizeMigration（上流はグリッドごとの DB ファイルとホットシート）、LauncherDatabase の検索の表、HomeItemViews・HomeFolder（設定だけの薄いサブクラス）、HomeItemInflater（小さい）、ホームの Activity（LauncherActivity。上流の Launcher.java のコピーにしない。2026-11-25 に決定、Tex-zwo5、理由は docs/launcher3-porting.md）。


---
id: Tex-hpdq
status: closed
deps: []
links: []
created: 2026-11-23T09:06:34Z
type: task
priority: 1
assignee: Koji Murata
parent: Tex-vs8t
tags: [launcher3, upstream]
---
# 上流の追跡を広げる：スタンドイン・書き写し・抜き出した値・フラグの確認

2026-11-23 のレビュー（Launcher3 への追従のための責務分担）の結果。scripts/update-upstream.sh が見ているのは path= と derived= だけで、次は上流が変わっても誰も気づかない。

- 互換のスタンドイン：80 ファイルのうち 75 が同じパスの上流ファイルを持ち、16_r4→17_r1 でそのうち 53 が変わった（CellLayout・BubbleTextView・Folder・FolderIcon など、上流のコードを大きく抱えるもの）
- アプリ側の書き写し：HomeWorkspace（Workspace のドラッグ）、DropTargetBar、HomeOptionsMenu、GridSizeMigration、GridSpec、HomeLoader、HomeDragController、AllAppsSwipeController、ItemLongClickListener、SpringLoadedListener、HomeWidgets（Launcher の追加フロー）
- 抜き出した値（res-caretlauncher/values*）：16_r4→17_r1 で、抜き出した名前のうち非ロケールの値 48、ロケールの文字列 511 が変わっていた。今は、v31 の値を基本の values に入れた色（18）や親を外した AppTheme* が印なしで違う。materialColor* や max_depth_blur_radius_enhanced の出どころ（quickstep/res、systemui dynamiccolors）はコメントにしかない
- フラグ：Flags.java の返り値は手で固定していて、リリース設定の継承（cp1a→bp4a→bp3a→…）を見直す仕組みがない（restoreArchivedAppIconsFromDb は bp3a で有効なのに false。Tex-bqu2）。コードに畳み込んだフラグ（fixWidgetSinglePtrResize、homeScreenEditImprovements、expandableLongPressMenu、allAppsSheetForHandheld、enableCursorDrivenWorkflows、generatedPreviews）は今はすべてリリースの値と合っているが、どこにも記録がない
- systemui の animationlib（Interpolators）、usertypelib（UserType）、各ライブラリと widgetpicker の Android.bp（手書きの build.gradle.kts の元）が追跡されていない。widgetpicker は 65 ファイルを1つずつ path= にしているので、上流の改名（16→17 で bottomsheet/BottomSheet*.kt → Sheet*.kt など）を拾えない

やること（UPSTREAM.md のブロックとスクリプトに足す）:
1. ref=<上流のパス> <こちらのパス>：上流の old→new の差分を表示する（こちらにある行に触れる部分だけ）。スタンドイン、アプリの書き写し、Android.bp に使う
2. values=<上流のファイル> <名前,…>：抜き出した値を、old・new・こちらで名前ごとに3方向比較する（修飾子つきのディレクトリも、上流に増えた修飾子も）。CaretLauncher: の許可リストを持つ
3. release_config=cp1a と flag=<名前> ENABLED|DISABLED：Flags.java とコードに畳み込んだフラグを、新しいタグの build/release の継承をたどった値と照合する
4. widgetpicker は path=modules/widgetpicker/src・res のようにディレクトリで載せる（Tex-yonv の上書きのバグを直してから）。build.gradle.kts のヘッダーの食い違い（「そのままコピー（上流からはコピーしていない…）」）も直す
5. 確認の道具（試作）：/private/tmp/claude-501/-Users-malt03-Documents-projects-CaretLauncher/e0ae5bc5-e83b-44bc-a4c7-77f6ffa2d629/scratchpad の resvals.py・flags.py（スクラッチなので消えることがある）


## Notes

**2026-11-23T10:30:13Z**

2026-11-23: 実装した。scripts/check-upstream.py（標準ライブラリだけ）を追加。UPSTREAM.md のブロックに ref=（launcher3 は互換 73 + アプリの書き写し + Android.bp/manifest、systemui は5）、values_root=（res、quickstep/res、systemui の dynamiccolors/res。全修飾子、android:featureFlag も見る）、release_repo=/release_config=cp1a/flags_file=/flag= を足し、widgetpicker を path= のディレクトリにした。--to <tag> で更新時の変化（フラグ、値、ref= の差分）を出し、update-upstream.sh がマージの後に呼ぶ。タグなしでは今のタグとの食い違いを出して 1 で終わる。今は launcher3・systemui とも食い違いなし（基本の values の色 16 を上流のフォールバック値に戻し、意図的な差に CaretLauncher: の印、config_widgetPickerSupportsBlurTokens を quickstep の true に、restoreArchivedAppIconsFromDb を true に＝Tex-bqu2）。docs/launcher3-porting.md の「更新の手順」と各 UPSTREAM.md に説明を書いた。互換クラスの上流コードを derived= に移すのは Tex-n0ut、アプリの書き写しは Tex-ydua

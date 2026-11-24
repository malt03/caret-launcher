---
id: Tex-mibn
status: closed
deps: []
links: []
created: 2026-11-24T13:35:59Z
type: task
priority: 2
assignee: Koji Murata
parent: Tex-op7u
tags: [launcher3, upstream, home]
---
# ① ホームの CellLayout を画面につなぎ、HomeGridView をなくす

ホームの CellLayout を上流と同じく画面の階層に置く。今は画面につながらない計算用の部品で、HomeGridView.kt（143 行）がその子（ShortcutAndWidgetContainer）を持って描いている。このため上流のコードのうち CellLayout が画面にあることを前提にする部分（AccessibleDragListenerAdapter、DragAndDropAccessibilityDelegate、ResizeManager.kt:267、互換の Workspace.java:68 の「つながっていない」印など）がそのまま使えない。

形: Workspace（FrameLayout。編集中に縮める）> NestedScrollView > CellLayout > ShortcutAndWidgetContainer。
- 上流の CellLayout.onMeasure は UNSPECIFIED で例外を出す（CellLayout.java:1022）。NestedScrollView は子をそれで測るので、HomeGridView.onMeasure（95-118: ページの高さ、セルの最小の高さ、使っている行までの高さ）を CellLayout.onMeasure の印付きのブロックに移す（setFixedSize か setCellDimensions）
- HomeGridView の addItemView・removeView は CellLayout.addViewToCellLayout・removeView に、onDraw・dispatchDraw は CellLayout 自身のものになる。CellLayout が自分の背景を描くので SpringLoadedListener.kt:36 の workspace.background = pageBackground の回避がいらなくなる
- 互換の CellLayout の「FrameLayout にした」「測らない・並べない」印を上流に戻す。フォルダのページ（FolderPagedView）も同じ CellLayout を使うので、フォルダが壊れないか確かめる
- docs/launcher3-porting.md の「ホームの CellLayout は画面につながない」方針を書き換える

リスク: 縦スクロールの中での大きさの計算、キーボードの上下に合わせた測り直し、編集中の縮小の中心と背景がスクロールに付いてくること、ウィジェットのリサイズ枠がウィジェットに付いていくこと、折りたたみの2パネル。規模: 中。


## Notes

**2026-11-24T14:11:42Z**

2026-11-24: 済み。互換の CellLayout を上流から作り直した（ViewGroup、上流のコンストラクター・onMeasure・onLayout・onDraw・dispatchDraw・removeView 系・状態の保存など。削ったのはホットシートの寸法、セルの位置の変換、アクセシブルなドラッグ、ホットシートへの移し替え）。ShortcutAndWidgetContainer はコンテナの種類を戻し、CellLayout が作る。CellLayoutContainer・ParcelableSparseArray・folder_page.xml は path=。互換の Workspace が CellLayoutContainer（1ページ）。HomeGridView をなくし、アプリの HomeScrollView（NestedScrollView）がセルの大きさ（setCellDimensions）と高さ（measureChildWithMargins で EXACTLY）を渡す。フォーカス枠は HomeScrollView が描く。ResizeManager.finish は上流の isAttachedToWindow に戻した。エミュレータで表示・ドラッグ・編集中の縮小（前の版と同じ）・フォルダを確認

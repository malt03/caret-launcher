---
id: Tex-qnof
status: open
deps: []
links: []
created: 2026-11-23T09:06:34Z
type: bug
priority: 3
assignee: Koji Murata
parent: Tex-ogmk
tags: [home, widget]
---
# ウィジェットの大きさの計算が2か所にあり、追加フローが PendingAddWidgetInfo を捨てて保存もしない

- 大きさ: HomeWidgetSizes.getWidgetSizePx（HomeWidgets.kt 73-82 行）がセルの大きさから計算し直している。逐語コピーの widget/util/WidgetSizes.java は互換の DeviceProfile（HomeController.kt 170 行で設定）を読む。ピッカーのプレビューとバインドの大きさが別の元から出る。WidgetSizes を使い、WidgetSizeHandler.kt を derived= で取り込む（互換の IDP の supportedProfiles を1つにする）。HomeWidgetSizes.initSpans は fromProviderInfo を呼ぶだけなので畳む
- 追加フロー: completeAddAppWidget（HomeWidgets.kt 303-316 行）は PendingAddWidgetInfo を捨て、initSpans と findCellForSpan をやり直す（addPendingWidget が決めて info に書いたセルとスパンを使わない）。上流（Launcher.java 1294-1351 行）は itemInfo の spanX・cellX を使い、sourceContainer も入れる。上流は mPendingRequestArgs を onSaveInstanceState で保存する（Launcher.java 1564 行）が、アプリの pendingAddWidgetId はメモリだけで、設定画面の間に LauncherActivity が recreate すると失われる


---
id: Tex-4cve
status: open
deps: []
links: []
created: 2026-11-23T06:16:03Z
type: feature
priority: 3
assignee: Koji Murata
parent: Tex-ogmk
tags: [home, launcher3]
---
# ウィジェットピッカーからドラッグしてホームに置く

Launcher3 は WidgetPickerDragItemListener（BaseItemDragListener、システムのドラッグ）でピッカーからホームにウィジェットを渡す。今の移植（Tex-r705）ではドラッグは何もしない（WidgetPickerComposeWrapperImpl でログを出して返す）。


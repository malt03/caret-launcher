---
id: Tex-h3ld
status: open
deps: []
links: []
created: 2026-11-20T11:48:45Z
type: task
priority: 2
assignee: Koji Murata
parent: Tex-ogmk
tags: [home, launcher3, spike]
---
# ウィジェットピッカーの方式調査

次の2つを比べて方式を決める。
- OS 標準の選択画面（AppWidgetManager.ACTION_APPWIDGET_PICK）: Android 17 のエミュレータで使えるか、見た目と使い勝手はどうか
- Launcher3 のウィジェットピッカー（widget/picker/、widgetpicker/）の移植: 依存の深さ
結果はこのチケットのノートに残す。

## Acceptance Criteria

- [ ] ACTION_APPWIDGET_PICK が Android 17 で動くかを確かめた
- [ ] Launcher3 版を移植する場合の範囲を見積もった
- [ ] どちらにするか決めた


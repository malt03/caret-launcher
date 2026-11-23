---
id: Tex-uus1
status: open
deps: []
links: []
created: 2026-11-23T06:16:03Z
type: bug
priority: 1
assignee: Koji Murata
parent: Tex-ogmk
tags: [home, widget]
---
# AppCompat の View の置き換えでウィジェットの表示に失敗する

時計アプリの Digital ウィジェットが「Couldn't add widget.」になる。RemoteViews の inflate で TextView が MaterialTextView に置き換わり、NumberFormatException で失敗する（AppWidgetHostView: Error inflating RemoteViews）。LauncherActivity が AppCompatActivity で、その LayoutInflater の Factory2（AppCompat/MaterialComponents の viewInflater）がウィジェットの inflate にも使われるため。Launcher3 の Activity は AppCompat を使わない。ピッカーの移植（Tex-r705）以前からある問題。


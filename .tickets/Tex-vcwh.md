---
id: Tex-vcwh
status: closed
deps: []
links: []
created: 2026-11-22T16:04:35Z
type: bug
priority: 2
assignee: Koji Murata
parent: Tex-ogmk
tags: [home]
---
# ウィジェットがセルの縦横比に合わず切れる

TextLauncher のホームはアプリ名を出さないぶん行が低い（docs/spec.md 5.1。最小 78dp 前後）ので、セルが Launcher3 より横長になる。Launcher3 で正方形に近い 2x2 のウィジェット（時計の Analog など）が、幅に合わせて描かれて上下が切れる。Pixel 9 Pro Fold エミュレータの内側で確認（2026-11-23）。対処の方針はユーザーに確認中。


## Notes

**2026-11-22T16:34:31Z**

原因はセルの縦横比ではなく、AppWidgetHostView の既定の余白だった（2026-11-23）。Launcher3 の NavigableAppWidgetHostView.setAppWidget はこの余白を付けない（「Launcher manages its own widget spacing」）が、互換クラスに入れていなかった。余白（左右 29px、上 10px、下 49px）のぶん中身の枠が低くなり、ウィジェットは知らされた大きさで正方形を描いて上下が切れていた。移植して直した。Pixel Launcher で同じ時計を横長（4x2 相当）にしても切れず、高さに合わせて描かれることも確認した。

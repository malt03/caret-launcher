---
id: Tex-vcwh
status: open
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


---
id: Tex-6feu
status: open
deps: []
links: []
created: 2026-11-22T00:57:38Z
type: bug
priority: 0
assignee: Koji Murata
parent: Tex-ogmk
---
# 画面の回転でホームのアイテムが消える

行数を今のウィンドウの高さから出しているため、横向き（自動回転オン）や分割画面で行数が減ると、HomeController の onPageHeightChanged → HomeLoader.onGridSizeChanged → rebuild → checkItemPlacement で下の行のアイテムが削除され保存される。回転を戻しても戻らない。da6fdce で入った。

- マニフェスト: screenOrientation=unspecified、configChanges に orientation|screenSize
- Launcher3: スマホでは RotationHelper でホームの回転を止める（ホームの回転を許す設定がなければ NOSENSOR 相当）。行数は InvariantDeviceProfile.numRows で、向きやウィンドウの大きさでは変わらない。グリッドが本当に変わったときは GridSizeMigration で移す。checkItemPlacement は LoaderTask の読み込みの中でだけ走る

直し方:
- RotationHelper と同じく縦向きに固定する
- 行数は自然な向きの大きさから出して保存し（InvariantDeviceProfile 相当）、checkItemPlacement は読み込みのときに保存した行数でだけ行う。一時的な測り直しで削除しない
- docs/spec.md 5.1 の「行数が変わったとき」の扱いを合わせて直す


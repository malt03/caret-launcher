---
id: Tex-6feu
status: closed
deps: []
links: [Tex-3k5k]
created: 2026-11-22T00:57:38Z
type: bug
priority: 0
assignee: Koji Murata
parent: Tex-ogmk
---
# 画面の回転でホームのアイテムが消える

行数を今のウィンドウの高さから出しているため、横向き（自動回転オン）や分割画面で行数が減ると、HomeController の onPageHeightChanged → HomeLoader.onGridSizeChanged → rebuild → checkItemPlacement で下の行のアイテムが削除され保存される。回転を戻しても戻らない。da6fdce で入った。

- マニフェスト: screenOrientation=unspecified、configChanges に orientation|screenSize
- Launcher3: スマホでは RotationHelper でホームの回転を止める（android-17.0.0_r1 にはホームの回転を許す設定はなく、大画面でなければ NOSENSOR）。行数は InvariantDeviceProfile.numRows で、向きやウィンドウの大きさでは変わらない。グリッドが本当に変わったときは GridSizeMigration で移す。checkItemPlacement は LoaderTask の読み込みの中でだけ走る

直し方:
- RotationHelper と同じく縦向きに固定する
- 行数は自然な向きの大きさから出して保存し（InvariantDeviceProfile 相当）、checkItemPlacement は読み込みのときに保存した行数でだけ行う。一時的な測り直しで削除しない
- docs/spec.md 5.1 の「行数が変わったとき」の扱いを合わせて直す


## Notes

**2026-11-22T06:42:27Z**

直した。RotationHelper を互換クラスに移し、スマホでは NOSENSOR、ドラッグ中は LOCK。行数は GridSpec が InvariantDeviceProfile と同じく WindowManagerProxy.estimateWindowBounds の各向きの一番小さい大きさから決め、ウィンドウでは変えない（低いウィンドウではセルを最小の高さにして縦スクロール）。グリッドが変わったら GridSizeMigrationLogic の移し替え（行が増えたら下にずらす、それ以外は詰め直し、入りきらないものは削除）。保存したグリッドは com.android.launcher3.prefs の migration_src_workspace_size。残り: 折りたたみ端末では今のディスプレイしか見えないので、畳む・開くでグリッドが変わり詰め直しになる（Launcher3 はシステムの API で両方のディスプレイを見て1つのグリッドにする）

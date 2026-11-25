---
id: Tex-lj5u
status: closed
deps: []
links: []
created: 2026-11-23T09:06:34Z
type: bug
priority: 2
assignee: Koji Murata
parent: Tex-ogmk
tags: [home, widget]
---
# グリッドの移し替えがウィジェットの最小スパンを使わず、外したウィジェットの id を消さない

GridSizeMigration.kt 76 行目のコメントは「ウィジェットはない」のままで、ウィジェットを全部の大きさで置き、入らなければ外す。上流は DbReader.kt（160-177 行）でウィジェットに提供元の最小スパン（不明なら 2x2）を与え、solveGridPlacement・findPlacementForEntry はその大きさで置く（GridSizeMigrationLogic.kt 521-532、557 行）。

また、ここで外したウィジェットと HomeLoader.checkItemPlacement（91-111 行）で外したウィジェットは deleteAppWidgetId されない。上流は読み込みのあとに ModelDbController.removeGhostWidgets（LoaderTask.java 625 行）で、ホストにあって DB にない id を消す。

直し方: 最小スパンを使う。外したウィジェットの id を消す。読み込みのあとにゴーストウィジェットを掃除する


## Notes

**2026-11-23T16:09:17Z**

GridSizeMigration に DbReader.loadAllEntries の最小スパンを移した（WidgetManagerHelper.getLauncherAppWidgetInfo を互換クラスに足し、LauncherAppWidgetProviderInfo.getMinSpans。リサイズしない向きはスパンのまま、提供元がわからなければ 2x2）。solveGridPlacement・findPlacementForEntry は上流どおり最小スパンで置き、スパンもそれにする。initSpans は互換の DeviceProfile のセルで数えるが、移し替えはグリッドを測る前に走りうるので、HomeController が GridSpec の「ディスプレイの一番小さいウィンドウでのセル」（homeCellWidthPx・homeCellHeightPx、新設）を最初に入れておく。外したウィジェットの id は、上流と同じく読み込みのあとの removeGhostWidgets（HomeWidgetHost に移した）で消す。呼ぶのは最初の読み込みの終わり（HomeWidgetAddFlow.onLoaded、HomeLoader は変えていない）で、移し替えと checkItemPlacement の両方の分が消える。ホームは Activity ごとに読み込むので、追加の途中の id（pendingAddWidgetId と読み込みを待っている結果の id）は残す（CaretLauncher: の印）。mPendingActivityResult は上流と同じ ActivityResultInfo にした

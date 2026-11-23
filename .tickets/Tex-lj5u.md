---
id: Tex-lj5u
status: open
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


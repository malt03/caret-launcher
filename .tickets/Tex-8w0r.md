---
id: Tex-8w0r
status: open
deps: []
links: []
created: 2026-11-22T00:57:38Z
type: bug
priority: 1
assignee: Koji Murata
parent: Tex-ogmk
---
# 満杯のときに追加するとページの外に置かれ、次の読み込みで消える

長押しメニューの「＋」（HomeController.addToWorkspace → findSpaceOnWorkspace）は空きがないと (0, countY) に置き、HomeGridView.addItemView が一時的にグリッドを広げる。次の測定で戻るので、ページの外（Tex-i8xr のクラッシュと同じ状態）に残り、次の読み込みで checkItemPlacement に消される。ドロップでも HomeWorkspace の空きがない分岐で Workspace.onNoCellFound に当たる処理がない。

- Launcher3: LauncherAccessibilityDelegate.findSpaceOnWorkspace は新しいページを足し、足せなければ addToWorkspace が false を返す。ドロップは Workspace.onNoCellFound で「空きがありません」（R.string.out_of_space）のトーストを出す

直し方: 空きがなければ out_of_space を出して追加しない。acceptDrop / onDrop に onNoCellFound 相当を入れる。測定のあとは addItemView でグリッドを広げない。関連する古いコメント（「グリッドは下に伸びる」「行の上限がないのでトーストなし」）も直す


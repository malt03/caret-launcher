---
id: Tex-8w0r
status: closed
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


## Notes

**2026-11-23T07:00:29Z**

ウィジェット（ピッカーからの追加）は、空きがなければ addToWorkspace が false を返して追加しない（上流と同じく何も出さない）。設定画面のあとで空きがなくなっていれば out_of_space を出す。残りはアイコンの「＋」とドロップ。

**2026-11-23T14:35:14Z**

2026-11-23: 直した。HomeController.findSpaceOnWorkspace は空きがなければ false を返し、addToWorkspace は out_of_space のトーストを出して追加しない（ページの外に置かない）。HomeWorkspace.acceptDrop は空きがないとき Workspace.onNoCellFound と同じくトーストを出す。古いコメント（下に伸びる、行の上限がない）も直した。エミュレータで、全セルを埋めた状態で「＋」とウィジェットの上へのドロップの両方でトーストが出て、DB が増えないことを確かめた（アイコンの上に落とすとフォルダーになるのは上流と同じ）

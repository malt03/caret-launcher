---
id: Tex-twu9
status: open
deps: []
links: []
created: 2026-11-22T00:57:38Z
type: bug
priority: 0
assignee: Koji Murata
parent: Tex-ogmk
---
# ホームの読み込み前のドロップや追加で DB が上書きされる

HomeModel.save() に読み込み済みかどうかの確認がない（loaded は HomeLoader が持つ）。起動直後、loadBlocking が終わる前に検索結果からドロップしたり「＋」で追加したりすると、空に近いメモリの内容で home_items を全部書き換え、updatePinnedStateBlocking(空) で全ショートカットの固定を外す。その後 setLoaded でメモリが読み込んだ内容に戻り、追加したアイテムは失われる。

- Launcher3: Launcher.isWorkspaceLocked（mWorkspaceLoading）の間は ItemLongClickListener.canStartDrag がドラッグを止め、LauncherModel.enqueueModelUpdateTask は isModelLoaded まで捨てる

直し方: 読み込み済みのフラグを HomeModel に移し（isModelLoaded 相当）、それまで save() をしない。ItemLongClickListener に canStartDrag 相当を入れ、addToWorkspace も止める


## Notes

**2026-11-23T07:00:29Z**

ピッカーからの追加（addToWorkspace → addAppWidgetFromDrop → addWidget）も model.save() を通る。ピッカーはホームの長押しメニューから開くので読み込み前には起きにくいが、直すときは同じ確認に含める。

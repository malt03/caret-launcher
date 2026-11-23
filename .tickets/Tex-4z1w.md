---
id: Tex-4z1w
status: closed
deps: []
links: [Tex-ijxl]
created: 2026-11-22T00:58:38Z
type: bug
priority: 2
assignee: Koji Murata
parent: Tex-ogmk
---
# ドラッグ元の情報（mDragInfo）が残り、あとのドロップで二重に足されることがある

HomeWorkspace.onDragEnd が mDragInfo を消さない。アンインストールの落とし先の DeferredOnComplete は、ランチャーに戻ったときにその時点の mDragInfo で onDropCompleted を呼ぶ。残った mDragInfo は、グリッドの外から始めた次のドラッグで古いセルの占有を外し、古い大きさを使う。遅れて来た失敗の呼び出しが今のドラッグの mDragInfo を消すと、そのドロップは onDropExternal に回って同じアイテムを二重に足す（Tex-ijxl の候補）。

- Launcher3: Workspace.onDragEnd で mDragInfo = null。onDropCompleted は getViewByItemId(d.originalDragInfo.id) で View を探す。アンインストールが成功したアイテムはパッケージの削除で消える

直し方: onDragEnd で mDragInfo を消し、onDropCompleted は d.originalDragInfo で View を探す


## Notes

**2026-11-23T16:00:28Z**

HomeWorkspace.onDragEnd で mDragInfo を消し（Workspace.onDragEnd と同じ）、onDropCompleted は d.originalDragInfo.id でグリッドの View を探して表示に戻すようにした（getViewByItemId）。ドラッグが終わったあとに来たアンインストールの失敗では、上流は占有を外したままにするが、こちらはその View のセルを占有に戻す（TextLauncher: の印）。あわせて Launcher.onPause と同じく、一時停止でドラッグを取り消す（HomeController.onPause）。これで、アンインストールの遅れた結果（onResume）が別のドラッグの最中に来ることがなくなる。docs/spec.md 5.4 の「終わるとき」に追記

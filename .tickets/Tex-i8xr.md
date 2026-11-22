---
id: Tex-i8xr
status: closed
deps: []
links: []
created: 2026-11-22T00:23:31Z
type: bug
priority: 1
assignee: Koji Murata
---
# ホーム編集中のクラッシュ2件（範囲外のアイテム、ドラッグ中の再バインド）

Pixel 6a で、ホーム編集中にアプリを動かしたときと置いたときにクラッシュする。
- ViewCluster.edgeContainsValue の ArrayIndexOutOfBoundsException: 前のバージョンでページの外に置いたアイテムが、行数の固定で範囲外になった
- DragLayer.animateViewIntoPosition の NullPointerException: ドラッグ中にアプリの変更などでホームが作り直され、ドラッグ中のビューがグリッドから外れた


## Notes

**2026-11-22T00:23:31Z**

- 範囲外: LoaderCursor.checkItemPlacement を HomeLoader に移植。範囲外と重なったアイテムを読み込み時に削除する。行数が決まる前は確認せず、行数が変わったら（初回の測定も）読み込み直す（InvariantDeviceProfile の変更で読み込み直すのと同じ）
- 再バインド: ModelCallbacks.startBinding と同じく、作り直す前にフローティングビュー（rebind-safe 以外）を閉じてドラッグを取り消す
- エミュレータで、DB の cell_y を範囲外にしたアイテムが削除されること、ドラッグ中にパッケージを無効化してもクラッシュせずドラッグが取り消されることを確認

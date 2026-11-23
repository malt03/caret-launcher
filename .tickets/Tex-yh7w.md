---
id: Tex-yh7w
status: closed
deps: []
links: [Tex-3dnt]
created: 2026-11-22T00:57:38Z
type: bug
priority: 1
assignee: Koji Murata
parent: Tex-ogmk
---
# 一時的に使えないアプリや起動画面の変わったアプリがホームから削除される

HomeLoader.pruneMissingApps は「今のアプリ一覧にない」ものを削除する。AppRepository は onPackagesUnavailable（replacing も無視）でも読み直すので、SD カード上のアプリが一時的に使えなくなるとホームとフォルダーから永久に消える。起動画面（activity-alias の切り替えによる別アイコン機能、更新での名前変更）が変わったアプリも、付け替えずに削除される。

- Launcher3: ModelLauncherCallbacks.onPackagesUnavailable は replacing=true を無視し、PackageTaskFactory.appsUnavailable は FLAG_DISABLED_NOT_AVAILABLE を付けるだけで残す。保存した起動画面が無くなったら、そのパッケージの最初の起動画面に付け替える（PackageUpdatedTask、WorkspaceItemProcessor.processAppOrDeepShortcut）。削除するのはパッケージが消えたとき（onPackageRemoved）か、起動画面が一つもないとき

直し方: これらの扱いを移植する。2つある LauncherApps.Callback（AppRepository、ShortcutRepository）を ModelLauncherCallbacks の互換クラス1つにまとめ、上流と同じく作業に振り分ける（部分更新のチケットと合わせる）


## Notes

**2026-11-23T14:35:14Z**

2026-11-23: 直した（部分バインドへの整理は Tex-3dnt に残す）。AppRepository は onPackagesUnavailable の replacing=true を無視。HomeLoader.pruneMissingApps は WorkspaceItemProcessor / PackageUpdatedTask と同じく、保存した起動画面がアプリ一覧にないアイテムを: パッケージが有効ならその最初の起動画面に付け替えて保存（AppItemInfo.retarget）、SD カード上（ApplicationInfoWrapper.isOnSdCard を足した）なら FLAG_DISABLED_NOT_AVAILABLE で残し、アイコンはキャッシュから（AppIconLoader.loadUnavailable、IconCache.getTitleAndIcon を withUsePackageIcon で）、それ以外は削除。使えるようになれば bind でフラグが外れる。エミュレータで、DB の起動画面を存在しない別名に書き換えて起動し、今の起動画面に付け替わって同じセルに残ることを確かめた。SD カードの経路はエミュレータで再現できず未確認

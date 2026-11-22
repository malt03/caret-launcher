---
id: Tex-yh7w
status: open
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


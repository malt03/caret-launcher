---
id: Tex-o5bb
status: closed
deps: []
links: []
created: 2026-11-22T00:58:38Z
type: task
priority: 3
assignee: Koji Murata
parent: Tex-vs8t
---
# ホームの DB を favorites の形に寄せ、アイテムごとに書く

home_items は独自の種類の値（0〜3）と CONTAINER_HOME=-1 で、メモリ上の Launcher3 の ITEM_TYPE_*（0/2/4/6）や CONTAINER_DESKTOP（-100）と変換している。title の列がないので、問い合わせに失敗したショートカットは保存したアイコンは出るが名前と読み上げがない（上流の ContentWriter は TITLE を保存し、LoaderCursor.loadSimpleWorkspaceItem が使う）。save() は変化のたびに表を全部書き直し、ショートカットのアイコンを毎回 PNG にする（上流の ModelWriter はアイテムごと）。

また save() は ioExecutor で iconCache.iconFactory と isDefaultIcon（BaseIconCache.defaultIcon）を使う。ドキュメントでは IconCache は MODEL_EXECUTOR で動かすことになっている。テーマの変化と再バインドの間に保存すると、古い既定のアイコンをショートカットのアイコンとして保存する。ioExecutor はショートカットの IPC も運ぶので DB の書き込みが待たされる。

やること: 版3のマイグレーションで itemType と container を Launcher3 の値で持ち、title を足す。アイコンの平坦化と既定かどうかの判定は MODEL_EXECUTOR で（またはビットマップを当てたときに判定を記録）。ioExecutor は SQLite だけにする。のちにアイテムごとの更新にする（Tex-2es1 の ModelWriter と合わせる）


## Notes

**2026-11-23T17:01:56Z**

版3のマイグレーションで home_items を Launcher3 の favorites の表（LauncherSettings.Favorites の列。itemType・container は上流の値、アプリはインテント、ショートカットは ShortcutKey のインテント、profileId、title、icon、options、appWidgetSource）に移し、home_items を消した（LauncherDatabaseTest に版2からの移行のテストを足した。実機・エミュレータでは未実行）。値は上流と同じく ItemInfo.onAddToDatabase（ContentWriter をそのままコピー、WorkspaceItemInfo・FolderInfo・LauncherAppWidgetInfo の互換クラスに上流の onAddToDatabase を戻した）で作り、ContentWriter.getValues（アイコンの平坦化と既定かどうかの判定）・データベースの読み書き・ショートカットの問い合わせと固定の更新は MODEL_EXECUTOR で行う。ioExecutor は検索のデータベースだけ。ModelDbController は互換クラス（insert・update・delete・query・generateNewItemId）。読み込みは保存した title を使う（問い合わせに失敗したショートカットにも名前と読み上げが出る）。アイテムごとの書き込みは Tex-2es1 の ModelWriter で行う

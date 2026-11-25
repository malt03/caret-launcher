---
id: Tex-000h
status: closed
deps: []
links: []
created: 2026-11-21T12:05:49Z
type: task
priority: 2
assignee: Koji Murata
parent: Tex-mdus
tags: [icons]
---
# アイコンを Launcher3 の IconCache（ディスクのキャッシュ）で持つ

アイコンは AppIconLoader がメモリ（LruCache）だけで持っているので、再起動後にアイコンが手元にない。上流は IconCache が SQLite のデータベースにアイコンを残す。このため次の差が出ている（Tex-ditl で見つけた）。
- ショートカットを問い合わせられないとき、上流は覚えているアイコンを出すが、CaretLauncher は既定のアイコンになる
- 起動直後は全アイコンを作り直す。上流はデータベースから読む（強制終了後にホームが数秒空だったのは、これが一因かもしれない。未確認）

取り込みの見込み（2026-11-21 の調査）:
- キャッシュの本体（BaseIconCache、SQLite、CachingLogic、LauncherActivityCachingLogic、IconCacheUpdateHandler、IconLoadRequest、CacheLookupFlag）は iconloaderlib として上流のままコピー済みで、使っていないだけ
- icons/CacheableShortcutInfo.kt（ショートカットのアイコンの CachingLogic）は依存が小さく、そのままコピーできそう（PackageUserKey などを足す）
- icons/IconCache.kt（639 行）は Dagger、InvariantDeviceProfile、ホームのファイル、インストール中のセッション、インスタントアプリ、ウィジェット、UserCache に依存するので、ItemClickHandler と同じく削って derived= の互換クラスにする
- icons/LauncherIcons.kt（アイコンを作る BaseIconFactory のプール）は Dagger・InvariantDeviceProfile・UserCache への依存を代わりのクラスで受けるか、互換クラスにする
- モデル側は、LoaderTask と同じく IconCacheUpdateHandler でインストール済みのアプリのキャッシュを更新し、getTitlesAndIconsInBulk / getShortcutIcon / getTitleAndIcon でアイコンを引く
- AppIconLoader の LruCache と独自のアイコン作成を IconCache に置き換える。アイコンのテーマと形の切り替え（IconSettings / ThemeManager）でキャッシュが正しく作り直されることを確かめる

## Acceptance Criteria

- [x] IconCache（derived=）と CacheableShortcutInfo などが上流から取り込まれ、アイコンがデータベースに残る
- [x] 再起動後、ショートカットを問い合わせられなくても、そのショートカット自身のアイコンが出る
- [x] アイコンのテーマと形を切り替えると、すべてのアイコンが新しいものになる
- [x] アプリの追加・更新・削除でアイコンが正しく変わる
- [x] AppIconLoader の独自のキャッシュとアイコン作成がなくなっている

## 結果（2026-11-21）

- IconCache は derived= の互換クラス、CacheableShortcutInfo と CancellableTask はそのままコピー、LauncherIcons は互換クラスにした。スレッドは上流と同じ MODEL_EXECUTOR（長押しメニューの問い合わせと同じ）
- AppIconLoader は IconCache を引くだけになった。ホームは DESKTOP_ICON_FLAG（テーマ付き）、検索結果は DEFAULT_LOOKUP_FLAG。BaseIconCache は普通の参照では DB に書かないので、LoaderTask と同じく最初の表示のあとに IconCacheUpdateHandler で DB を更新する
- ホームの DB（home_items）に icon 列を足し、ショートカットのアイコンを保存する（既定のアイコンは保存しない）。列を足す前に保存したショートカットは、アイコンが読めたあとに一度保存し直す
- エミュレータで確認:
  - 既定のホームでないとき（ショートカットを問い合わせられないとき）も、ショートカットが自分のアイコンで出る
  - テーマのオン・オフと形の切り替えで、ホームのアイコンがすべて変わる
  - パッケージの無効化・有効化で、キャッシュの行が消えて戻る
  - 強制終了後のホームは 1.5 秒の時点でアイコン付きで出る（以前は 5〜7 秒空だった）
- アプリのアンインストール・インストールは、エミュレータのシステムアプリを消せないため見ていない（同じ経路の無効化・有効化で確認）

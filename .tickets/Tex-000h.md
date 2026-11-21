---
id: Tex-000h
status: open
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
- ショートカットを問い合わせられないとき、上流は覚えているアイコンを出すが、TextLauncher は既定のアイコンになる
- 起動直後は全アイコンを作り直す。上流はデータベースから読む（強制終了後にホームが数秒空だったのは、これが一因かもしれない。未確認）

取り込みの見込み（2026-11-21 の調査）:
- キャッシュの本体（BaseIconCache、SQLite、CachingLogic、LauncherActivityCachingLogic、IconCacheUpdateHandler、IconLoadRequest、CacheLookupFlag）は iconloaderlib として上流のままコピー済みで、使っていないだけ
- icons/CacheableShortcutInfo.kt（ショートカットのアイコンの CachingLogic）は依存が小さく、そのままコピーできそう（PackageUserKey などを足す）
- icons/IconCache.kt（639 行）は Dagger、InvariantDeviceProfile、ホームのファイル、インストール中のセッション、インスタントアプリ、ウィジェット、UserCache に依存するので、ItemClickHandler と同じく削って derived= の互換クラスにする
- icons/LauncherIcons.kt（アイコンを作る BaseIconFactory のプール）は Dagger・InvariantDeviceProfile・UserCache への依存を代わりのクラスで受けるか、互換クラスにする
- モデル側は、LoaderTask と同じく IconCacheUpdateHandler でインストール済みのアプリのキャッシュを更新し、getTitlesAndIconsInBulk / getShortcutIcon / getTitleAndIcon でアイコンを引く
- AppIconLoader の LruCache と独自のアイコン作成を IconCache に置き換える。アイコンのテーマと形の切り替え（IconSettings / ThemeManager）でキャッシュが正しく作り直されることを確かめる

## Acceptance Criteria

- [ ] IconCache（derived=）と CacheableShortcutInfo などが上流から取り込まれ、アイコンがデータベースに残る
- [ ] 再起動後、ショートカットを問い合わせられなくても、そのショートカット自身のアイコンが出る
- [ ] アイコンのテーマと形を切り替えると、すべてのアイコンが新しいものになる
- [ ] アプリの追加・更新・削除でアイコンが正しく変わる
- [ ] AppIconLoader の独自のキャッシュとアイコン作成がなくなっている


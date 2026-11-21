---
id: Tex-ditl
status: closed
deps: []
links: []
created: 2026-11-21T11:44:41Z
type: task
priority: 2
assignee: Koji Murata
parent: Tex-ogmk
tags: [refactor, home]
---
# ホームのショートカットと無効状態を上流と同じ持ち方にする

ホームのショートカットの持ち方と無効状態の扱いが Launcher3 と違い、機能の差が出ている。Android の制約ではなく作りの差なので、上流に合わせる（2026-11-21 の検討）。

出ている差:
1. アプリ側が無効にしたショートカット（作成元による無効、版が古い）: 上流は灰色で出し、タップで disabledMessage をトーストに出す。TextLauncher は WorkspaceItemInfo の互換クラスで無効フラグを省いているので普通に出し、タップは起動に失敗して何も起きないはず（コードからの推測、未確認）
2. ショートカットがまだ見つからない・問い合わせに失敗したとき: 上流は保存済みのアイコンで表示を続け、ロック中のユーザーなどは無効として灰色で出す。TextLauncher は ShortcutItemInfo が ShortcutInfo の実体を持たないと View を作らないので、その間ホームから見えなくなる
3. 停止中・使用制限中のアプリ（FLAG_DISABLED_SUSPENDED など）: 上流は灰色で出し、タップで理由を出す。TextLauncher は区別しない

やること:
- ホームのショートカットを上流と同じく WorkspaceItemInfo とキー（intent から割り出す ShortcutKey）で持つ。ShortcutInfo は読み込み時（WorkspaceItemProcessor）と変更時（ShortcutsChangedTask）に問い合わせ、見つかるまでは保存済みのアイコンで表示する。WorkspaceItemInfo の互換クラスに足した shortcutInfo の欄（Tex-nguc）と、メニューの項目を ShortcutItemInfo に変換する処理をなくす
- WorkspaceItemInfo / ItemInfoWithIcon の無効フラグ（FLAG_DISABLED_BY_PUBLISHER、FLAG_DISABLED_VERSION_LOWER、FLAG_DISABLED_SUSPENDED、FLAG_DISABLED_LOCKED_USER など）と updateFromDeepShortcutInfo の無効の扱い、disabledMessage を上流どおりに戻す
- touch/ItemClickHandler を移植し、ホーム・フォルダ・メニューのタップをそれで処理する（無効な項目のタップを上流と同じにする）。全アプリ一覧でも使う

## Acceptance Criteria

- [ ] ホームのショートカットが WorkspaceItemInfo とキーで持たれ、WorkspaceItemInfo の互換クラスに TextLauncher の欄がない
- [ ] 無効にされたショートカットが灰色で出て、タップで理由が出る
- [ ] ショートカットの問い合わせに失敗しても、ホームの表示が消えない
- [ ] 停止中のアプリが灰色で出て、タップで理由が出る
- [ ] ItemClickHandler が third_party のコピー（または derived=）になっている
- [ ] メニューからの追加・ドラッグ、起動、フォルダ、再起動後の保持が今と同じ


## Notes

**2026-11-21T11:57:58Z**

ホームのショートカットを上流と同じく WorkspaceItemInfo（ITEM_TYPE_DEEP_SHORTCUT）のまま持ち、ShortcutKey.fromItemInfo で見分けるようにした（ShortcutItemInfo と、WorkspaceItemInfo に足した shortcutInfo の欄、メニューの項目の変換をなくした）。保存の形式は変えていない。ショートカットの実体は読み込み時と変更時に問い合わせ、updateFromDeepShortcutInfo で題名・無効の状態・アイコンを更新する。問い合わせられないときは、覚えているアイコンか既定のアイコン（IconCache.getDefaultIcon）で表示を続ける。ItemInfoWithIcon / WorkspaceItemInfo の無効フラグと disabledMessage（上流でも設定されない）を上流どおりに戻した。touch/ItemClickHandler を、アプリペア・ウィジェット・インストール中・プライベートスペースを削って derived= の互換クラスにし、ホーム・フォルダ・メニューのタップをそれで処理する（起動は Launcher.startActivitySafely をアプリが実装）。停止中のアプリは AppItemInfo.bind で FLAG_DISABLED_SUSPENDED を立て、ホームと検索結果で灰色にする。ShortcutKey・ItemInfoMatcher・IntSet・IntArray は上流のままコピーした。
エミュレータでの確認: テスト用のアプリ（スクラッチで作った APK。確認後にアンインストール）のショートカットをホームに置き、アプリ側で無効にすると灰色になり、タップで「ショートカットは使用できません」が出る。有効に戻すと元に戻る。pm suspend したアプリはホームと検索結果で灰色になり、タップでシステムの「このアプリは使用できません」が出る。デフォルトのホームでない状態（ショートカットを問い合わせられない）で開くと、ショートカットは既定のアイコンで表示が残る。メニューの行のタップ、+ での追加、行のドラッグ、フォルダ、再起動後の保持も確認。
残り: アイコンのディスクのキャッシュがないので、問い合わせられないときに出るのは覚えているアイコンではなく既定のアイコンになる（上流は IconCache の DB に残っている）。別のチケットにする

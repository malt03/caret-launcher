---
id: Tex-ditl
status: open
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


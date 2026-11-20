---
id: Tex-1eos
status: open
deps: [Tex-l0eq, Tex-4hiv]
links: []
created: 2026-11-20T11:16:26Z
type: task
priority: 3
assignee: Koji Murata
parent: Tex-mdus
tags: [icons]
---
# テーマアイコン・単色の自動生成・形状の切り抜き

最新の AOSP Launcher3 と iconloaderlib（MonochromeIconFactory など）の実装に従う。
- テーマアイコンがオンなら、単色アイコンをシステムのパレットで塗る。単色アイコンがないアプリは自動生成する（機能フラグで既定オフでも有効にする）
- 選んだ形状で、すべてのアイコンを切り抜く
- 適用範囲: ホーム、フォルダのプレビューと中身、検索結果、全アプリ一覧
- 初期値: テーマアイコンはオン、形状は円
仕様: docs/spec.md 7.3

## Acceptance Criteria

- [ ] テーマアイコンがオンで、単色アイコンを持つアプリがパレットの色で表示される
- [ ] 単色アイコンを持たないアプリも単色で表示される
- [ ] 形状を変えると全箇所のアイコンが切り抜かれる


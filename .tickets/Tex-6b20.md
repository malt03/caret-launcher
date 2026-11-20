---
id: Tex-6b20
status: open
deps: []
links: []
created: 2026-11-20T11:16:26Z
type: task
priority: 1
assignee: Koji Murata
parent: Tex-izod
tags: [infra]
---
# ランチャーの骨組み（HOME Activity）

ホームアプリとして選択・起動できる最小構成を作る。
- ホームの Activity に MAIN + HOME + DEFAULT の intent-filter
- launchMode=singleTask、windowSoftInputMode=stateAlwaysVisible|adjustResize
- UI は View ベース（付録の実装メモが View の API 前提のため）
仕様: docs/spec.md 付録

## Acceptance Criteria

- [ ] OS の設定でこのアプリを既定のホームアプリに選べる
- [ ] ホームボタンでこのアプリの画面が出る
- [ ] ホーム画面でホームボタンを押しても Activity が増えない（onNewIntent が呼ばれる）


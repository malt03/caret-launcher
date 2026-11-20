---
id: Tex-t2ea
status: open
deps: [Tex-9ipm, Tex-wgyx, Tex-h3ld]
links: []
created: 2026-11-20T11:16:26Z
type: task
priority: 2
assignee: Koji Murata
parent: Tex-ogmk
tags: [home]
---
# ウィジェットのホストと追加

AppWidgetHost でウィジェットを表示・追加する。
- ウィジェット一覧（Launcher3 のウィジェットピッカー相当）
- バインド権限の要求、設定 Activity の起動
- 追加時のサイズとリサイズは Launcher3 に合わせる
- ウィジェット内部のスクロールは考慮しない
仕様: docs/spec.md 5.2

## Launcher3 からの移植

- リサイズ枠は AppWidgetResizeFrame.kt と widget/resize/ から移植する（範囲は Tex-wgyx で決める）
- ウィジェットピッカーの方式は Tex-h3ld の結果で決める

## Acceptance Criteria

- [ ] ウィジェット一覧から選んでホームに追加できる
- [ ] 設定が必要なウィジェットは設定画面を経て追加される
- [ ] ランチャーを再起動してもウィジェットが残る


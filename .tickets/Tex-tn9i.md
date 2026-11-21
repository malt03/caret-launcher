---
id: Tex-tn9i
status: open
deps: []
links: []
created: 2026-11-21T09:18:13Z
type: task
priority: 2
assignee: Koji Murata
parent: Tex-neft
tags: [refactor]
---
# 互換クラスに入った TextLauncher の方針をアプリ側へ出す

互換クラスは上流の動きを保ち、TextLauncher の方針はアプリ側のサブクラスで上書きする。
- Folder.java:44-63: 「キーで開いたときだけフォーカスを取る」（検索フィールドのフォーカスを守るため）。上流の setFocusableInTouchMode / requestFocus に戻し、OpenFolder で上書きする
- ActivityContext.java:16: hideKeyboard の説明が spec.md 5.4 を参照している。説明を中立にする（何もしない方針は HomeController.kt:107 にある）
- CellLayout.setGridSize（390 行目）: ホームが行方向に伸びる前提で占有を保つ。アプリ側のサブクラスに移すか、TextLauncher の変更だと明記する

## Acceptance Criteria

- [ ] 互換クラスに spec.md を根拠にした振る舞いの分岐がない（使わない部分を省く説明は可）
- [ ] フォルダのキー操作とタッチでのフォーカスの動き（docs/spec.md 2.6）が今と同じ


---
id: Tex-ebnm
status: open
deps: [Tex-9ipm, Tex-wgyx]
links: []
created: 2026-11-20T11:16:26Z
type: task
priority: 2
assignee: Koji Murata
parent: Tex-ogmk
tags: [home]
---
# アプリとフォルダの表示

ホームにアプリとフォルダを表示する。
- アプリは1セル、タップで起動（学習は記録しない）
- フォルダは名前なし。アイコンは中のアプリのプレビュー（Launcher3 と同様）
- タップでフォルダを開く。中のアプリもアプリ名なし
仕様: docs/spec.md 5.2

## Launcher3 からの移植

- フォルダのアイコンのプレビューと開閉のアニメーションは、Launcher3 の folder/（FolderIcon、Folder、PreviewBackground など）から移植する（範囲は Tex-wgyx で決める）

## Acceptance Criteria

- [ ] ホームのアプリをタップすると起動する
- [ ] フォルダのアイコンに中のアプリのプレビューが出る
- [ ] フォルダをタップすると中身が開き、そこから起動できる


## Notes

**2026-11-20T12:14:50Z**

移植の範囲は docs/launcher3-porting.md の表で確定（Tex-wgyx）

---
id: Tex-4hiv
status: closed
deps: []
links: []
created: 2026-11-20T11:16:26Z
type: task
priority: 1
assignee: Koji Murata
parent: Tex-izod
tags: [infra]
---
# データ保存の基盤

以下を永続化する保存層を用意する（Room など）。
- アプリごとの検索文字列（複数）
- 学習レコード: (正規化済み入力, アプリ) → (score, lastUpdated)
- ホームの配置（アプリ、フォルダとその中身、ウィジェットと位置・サイズ）
- テーマアイコンのオン/オフ、アイコン形状
アプリは ComponentName + UserHandle で識別する。
仕様: docs/spec.md 3.1、4、5、7.3

## Acceptance Criteria

- [ ] 各データの読み書きの単体テストがある
- [ ] アプリを再起動してもデータが残る


## Notes

**2026-11-20T11:49:02Z**

data/LauncherDatabase.kt（SQLite）と data/SearchStore.kt を実装。テストは未作成

**2026-11-20T12:13:01Z**

androidTest/LauncherDatabaseTest（6件）がエミュレータで通過

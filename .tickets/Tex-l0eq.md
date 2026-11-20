---
id: Tex-l0eq
status: in_progress
deps: [Tex-6b20]
links: []
created: 2026-11-20T11:16:26Z
type: task
priority: 1
assignee: Koji Murata
parent: Tex-izod
tags: [infra]
---
# アプリ一覧の取得と変更の監視

LauncherApps.getActivityList で個人用プロファイルのアプリを取得し、ComponentName + UserHandle で識別するモデルを作る。
- <queries> に MAIN/LAUNCHER の intent を宣言
- LauncherApps.Callback で追加・削除・更新を反映
- アイコンはひとまず通常のアイコン（テーマアイコン対応は別チケット）
仕様: docs/spec.md 2.1、付録

## Acceptance Criteria

- [ ] ランチャーに表示されるアプリが漏れなく取得できる
- [ ] アプリをインストール・アンインストールすると一覧に反映される
- [ ] 仕事用プロファイルと Private Space のアプリは含まれない


## Notes

**2026-11-20T11:49:02Z**

data/AppEntry.kt と data/AppRepository.kt を実装。画面がないため動作は未確認

**2026-11-20T12:06:10Z**

アプリ一覧の取得はエミュレータで確認。インストール・アンインストールの反映は未確認

---
id: Tex-68f3
status: closed
deps: [Tex-6b20]
links: []
created: 2026-11-20T11:48:44Z
type: task
priority: 1
assignee: Koji Murata
parent: Tex-izod
tags: [infra, launcher3]
---
# Launcher3 取り込みの土台

Launcher3 からコードを持ってくるための土台を作る。
- third_party/launcher3/ と third_party/systemui/ を作り、元のリポジトリと同じパス構成・パッケージ名で置けるようにする
- third_party 以下を Gradle のライブラリモジュールとしてビルドする
- 互換層（Launcher、DeviceProfile、LauncherPrefs、機能フラグなどの代わり）を置く場所を決める
- LICENSE と NOTICE を置き、UPSTREAM.md の書式を決める
- 更新用のスクリプト（前回タグ・新タグ・手元の3者で git merge-file する）を用意する
方針: docs/launcher3-porting.md

## Acceptance Criteria

- [ ] third_party 以下のモジュールを含めてビルドが通る
- [ ] UPSTREAM.md に、取り込んだファイルの元パスとタグが記録されている
- [ ] 更新スクリプトを、同じタグ同士で実行すると差分なしで終わる


## Notes

**2026-11-20T12:06:10Z**

third_party/（launcher3、systemui/iconloaderlib）、compat/launcher3、scripts/update-upstream.sh を追加。同じタグで更新スクリプトを流して差分なしを確認。usertypelib は @Parcelize が AGP 9 の組み込み Kotlin で効かないため compat の UserType で代用

---
id: Tex-lczw
status: closed
deps: [Tex-68f3]
links: []
created: 2026-11-20T11:48:45Z
type: task
priority: 2
assignee: Koji Murata
parent: Tex-mdus
tags: [icons, launcher3]
---
# iconloaderlib の取り込み

frameworks/libs/systemui の iconloaderlib（android-17.0.0_r1）を third_party/systemui/ に取り込み、ビルドできるようにする。
- 依存する機能フラグやシステムのクラスは互換層で受け止める
- テーマアイコン、単色アイコンの生成（MonochromeIconFactory）、形状での切り抜き、キャッシュをこのライブラリで行う
方針: docs/launcher3-porting.md

## Acceptance Criteria

- [ ] iconloaderlib を含めてビルドが通る
- [ ] UPSTREAM.md に取り込んだファイルが記録されている
- [ ] 通常のアイコン、テーマアイコン、単色アイコンを持たないアプリのアイコンを生成できる


## Notes

**2026-11-20T12:06:10Z**

iconloaderlib の取り込みとビルドは Tex-68f3 で済み。AppIconLoader で BaseIconFactory を使って通常のアイコンを生成している。テーマアイコンと形状はまだ

**2026-11-20T12:11:12Z**

AppIconLoader で BaseIconFactory（drawFullBleedIcons、MonoIconThemeController(shouldForceThemeIcon=true)）を使い、通常・テーマアイコンを生成。エミュレータで確認

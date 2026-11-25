---
id: Tex-oixt
status: closed
deps: []
links: []
created: 2026-11-21T09:18:12Z
type: task
priority: 1
assignee: Koji Murata
parent: Tex-neft
tags: [refactor]
---
# 互換クラスに Apache ライセンスのヘッダーと変更の記載を付ける

compat/ の 61 ファイルのどれにもライセンスヘッダーがない。CellLayout・BubbleTextView・popup の各ファイル・DeepShortcutView・LauncherAnimUtils などは 80〜100% が上流のコードで、docs/launcher3-porting.md の「ライセンス」（ヘッダーを残す、変更したことを記載する）に反している。
上流のコードを含むファイルには上流のヘッダーと「CaretLauncher で変更した」旨を付ける。上流に対応するファイルがない自前の互換クラスの扱いも決めて、方針に書く。

## Acceptance Criteria

- [ ] 上流のコードを含む互換クラスすべてに Apache 2.0 のヘッダーと変更の記載がある
- [ ] 自前の互換クラスの扱いが docs/launcher3-porting.md に書いてある


## Notes

**2026-11-21T09:29:53Z**

上流に同じ名前のファイルがある互換クラス 55 個に、上流のヘッダーと変更の記載を付けた。上流にヘッダーがない SystemShortcut と ShortcutKey は年なしの標準ヘッダーを付けた。上流にソースがない 6 個（BuildConfig、2 つの Flags、ActivityComponent、UserType、DragLayerAccess）は付けない。決まりを docs/launcher3-porting.md の「ライセンス」に書いた

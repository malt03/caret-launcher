---
id: Tex-bqu2
status: open
deps: []
links: []
created: 2026-11-22T00:57:39Z
type: bug
priority: 1
assignee: Koji Murata
parent: Tex-mdus
---
# Flags.restoreArchivedAppIconsFromDb の値がリリースの設定と違う

compat/launcher3/.../Flags.java は false（「bp4a / cp1a で未設定」）だが、cp1a → bp4a → bp3a と辿ると build/release の aconfig/bp3a/com.android.launcher3/restore_archived_app_icons_from_db_flag_values.textproto が ENABLED。IconCache.getShortcutIcon の withSkipAddToMemCache が BaseIconCache で効くかに関わる（今はショートカットのアイコンをメモリのキャッシュに入れている）。

直し方: true にしてコメントを bp3a に直す。ほかのフラグは確認済みで正しい


---
id: Tex-uji5
status: closed
deps: []
links: []
created: 2026-11-21T09:18:12Z
type: task
priority: 3
assignee: Koji Murata
parent: Tex-neft
tags: [refactor]
---
# compat の 2 モジュールの分け方を決めて揃える

compat/launcher3（別モジュール）に置く必要があるのは、iconloaderlib が使う Flags と UserType だけ。Utilities は :launcher3 からしか使われていないので compat/launcher3-internal に移すか、分け方の決まりを docs に書く。

## Acceptance Criteria

- [ ] 分け方の決まりが docs/launcher3-porting.md に書いてあり、全ファイルがそれに従っている


## Notes

**2026-11-21T09:28:38Z**

Utilities は iconloaderlib から使われていないので compat/launcher3-internal へ移した。分け方（iconloaderlib からも使うものだけ compat/launcher3）を docs/launcher3-porting.md の「置き場所と記録」に書いた

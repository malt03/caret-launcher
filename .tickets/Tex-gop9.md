---
id: Tex-gop9
status: open
deps: [Tex-cklf]
links: []
created: 2026-11-21T09:18:13Z
type: task
priority: 2
assignee: Koji Murata
parent: Tex-neft
tags: [refactor]
---
# ShortcutAndWidgetContainer の互換クラスを 1 つにする

ShortcutAndWidgetContainer.java（実行時に使われていない）と CellLayout.Children（CellLayout.java:42-60）が、同じクラスの代わりとして 2 つある。ShortcutAndWidgetContainer を残して上流の名前（getChildAtCell ではなく getChildAt(int x, int y)）にし、HomeGridView がそれを実装し、CellLayout.getShortcutsAndWidgets() がそれを返すようにする。

## Acceptance Criteria

- [ ] CellLayout.Children がなく、ShortcutAndWidgetContainer の名前とシグネチャが上流と同じ
- [ ] ホームの並び替えとドロップが今と同じ


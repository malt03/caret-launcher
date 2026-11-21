---
id: Tex-gop9
status: closed
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


## Notes

**2026-11-21T09:40:24Z**

CellLayout.Children をなくし、ShortcutAndWidgetContainer の互換クラスを ViewGroup にして上流のメソッド（setCellDimensions、getChildAt(cellX, cellY)、setupLp、measureChild、layoutChild、onLayout、shouldDelayChildPressedState、requestChildFocus、cancelLongPress）を移した。HomeGridView がそれを継承し、CellLayout.getShortcutsAndWidgets() が返す。canCreateFolder は上流どおり CellLayout に戻した。グリッドの padding を CellLayout の padding として扱う差分だけが layoutChild と DragLayer.animateViewIntoPosition に残る（TextLauncher: 印）。エミュレータでフォルダの作成、押しのけての並び替え、キーフォーカスを確認

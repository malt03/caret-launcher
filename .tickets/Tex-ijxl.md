---
id: Tex-ijxl
status: open
deps: []
links: []
created: 2026-11-21T10:20:30Z
type: bug
priority: 3
assignee: Koji Murata
parent: Tex-ogmk
tags: [home]
---
# ドラッグのあとに同じアイテムの View が2つ残ることがある（未再現）

Tex-f33r の確認中に一度だけ起きた。アンインストールのダイアログを開いたまま強制終了とホームで閉じた直後、長押しから指を離す前に別の adb コマンドで動かすという不規則な操作で、同じアプリの View がホームに2つ残った。再起動後は1つだけで、片方を削除しても再起動後にもう片方が残った。同じ ItemInfo が HomeWorkspace.onDropExternal 経由で二重に足された可能性がある（HomeWorkspace.onDrop は mDragInfo が null だと外からのドロップとして扱う。上流の Workspace と同じ形）。同じ手順を4回試したが再現しなかった

## Acceptance Criteria

- [ ] 再現手順が分かり、直すか、起きないことを確かめる


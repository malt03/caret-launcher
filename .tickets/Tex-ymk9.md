---
id: Tex-ymk9
status: open
deps: []
links: []
created: 2026-11-23T13:49:24Z
type: bug
priority: 2
assignee: Koji Murata
parent: Tex-ogmk
---
# フォルダーの中身が列×行を超えると、アイコンが重なる

2026-11-23 のフォルダー内のドラッグの移植で分かった（前からある）。FolderGridOrganizer の最大の数を超えると、上流の FolderPagedView.arrangeChildren は2ページ目を作るが、PagedView の互換クラスは1ページしか扱わず、全ページを同じ位置に描くのでアイコンが重なる（移植前の手書きの版でも位置が折り返して重なっていた）。Launcher3 と同じくページ送り（PagedView、ページのインジケーター、ページをまたぐ並べ替えとスクロールのアラーム）を移すか、TextLauncher の決まり（docs/spec.md 5.2）として行を増やすかを決める


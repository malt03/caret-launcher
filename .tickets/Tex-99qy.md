---
id: Tex-99qy
status: open
deps: [Tex-226d]
links: [Tex-dpqn]
created: 2026-11-21T16:03:47Z
type: bug
priority: 2
assignee: Koji Murata
parent: Tex-pa8s
tags: [drawer, search]
---
# 全アプリ一覧と検索結果のアプリ名の後ろに影がにじむ

実機のライトモードで、全アプリ一覧のアプリ名の後ろに、文字がにじんだような色が乗っている。

## 原因の見込み

- 一覧と検索結果のセル（ResultIconView）は、自前で文字に影（setShadowLayer、4dp、#88000000）を付けている。Launcher3 の一覧のアイコン（all_apps_icon.xml、style BaseIcon.AllApps）は影なし（shadowRadius 0）で、文字色は materialColorOnSurface。明るいシートの上では、この影がにじんで見える
- 真っ白に見えるシートとは原因が別（シートの方は、ぼかしがなく単色を塗っているため。Tex-dpqn で直す）

## 直し方

- 一覧のセルは上流の BaseIcon.AllApps に合わせて影をなくす
- 検索結果は壁紙の上に出るので、文字色と影は Tex-226d で決める。一覧と検索結果でセルの見た目を分ける

## Acceptance Criteria

- [ ] 一覧のアプリ名に影がなく、Launcher3 と同じ見た目になる
- [ ] 検索結果は、壁紙の上で読める色と影になる（文字色のチケットと合わせて）


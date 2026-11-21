---
id: Tex-dpqn
status: open
deps: []
links: [Tex-99qy]
created: 2026-11-21T16:03:47Z
type: feature
priority: 1
assignee: Koji Murata
parent: Tex-pa8s
tags: [drawer]
---
# 全アプリ一覧を Pixel Launcher と同じハンドル付きのシートにし、背景をぼかす

Pixel Launcher では、下からスワイプすると、背景にぼかしがかかりながら、画面外からハンドル付きのシートとして全アプリ一覧が出てくる。同じ動きにする。検索フィールドはシートの外になり、開き切った状態では『画面の一番上にフィールド、そのすぐ下にハンドル付きのシート』という見た目にする。

## 今の状態と原因（2026-11-22 時点）

- 今のシートは、Launcher3 で背景のぼかしが使えないときの色（system_accent2_200／800）を塗っているだけで、ぼかしは入っていない。実機（ライトモード）で真っ白に見えるのはこのため
- フィールドはシートに載っていて、シートと一緒に上がる

## Launcher3 からの移植（調べる点）

- 背景のぼかし: ActivityContext.isAllAppsBackgroundBlurEnabled、ActivityAllAppsContainerView の getBackgroundColor（blur_shade_panel_fg／bg を重ねた色）、ウィンドウのぼかし（BlurUtils、CrossWindowBlurListeners、Flags.allAppsBlur は bp3a で有効）
- シートのハンドル: ActivityAllAppsContainerView の bottom_sheet_handle_area と、その描画
- シートが画面外から出てくる動き（AllAppsTransitionController の進み具合とシートの位置、ALL_APPS_SHEET_DEPTH）
- Pixel Launcher にしかない部分がないか、エミュレータの Pixel Launcher と並べて確かめる

仕様: docs/spec.md 6（6.1〜6.4 の見た目を直す）

## Acceptance Criteria

- [ ] 下からスワイプすると、背景がぼけながらハンドル付きのシートが画面外から出てくる
- [ ] 開き切ると、画面の一番上にフィールド、そのすぐ下にハンドル付きのシートがある
- [ ] ライトモードでも、シートの後ろの壁紙やホームがぼけて透けて見える（真っ白にならない）
- [ ] 引っ掛かり、ハプティック、閉じる操作は今までどおり


---
id: Tex-dpqn
status: closed
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

- [x] 下からスワイプすると、背景がぼけながらハンドル付きのシートが画面外から出てくる
- [x] 開き切ると、画面の一番上にフィールド、そのすぐ下にハンドル付きのシートがある
- [x] ライトモードでも、シートの後ろの壁紙やホームがぼけて透けて見える（真っ白にならない）
- [x] 引っ掛かり、ハプティック、閉じる操作は今までどおり

## 結果（2026-11-22）

- シート: ActivityAllAppsContainerView の mBottomSheetBackground と all_apps_bottom_sheet_background.xml のハンドル（36dp の領域、32×4dp、角 2dp、bottomSheetDragHandleColor）を AllAppsSheet にした。上の角だけ丸い（dialogCornerRadius）。一覧はハンドルの下
- 位置: シートは画面の下から、開くにつれて検索フィールドのすぐ下まで上がる。フィールドはシートの外で、キーボードの上から画面の上端へ動く
- ぼかし: BaseDepthControllerImpl と同じく、奥行きを mapDepthToBlur で max_depth_blur_radius_enhanced（30dp）までのぼかしにする。全アプリ一覧の奥行きは BottomSheetProfile.getBottomSheetDepth（config_wallpaperMaxScale 1.1、workspace_content_scale 0.97 から 0.3）で、ALL_APPS_SHEET_DEPTH（DECELERATED_EASE）で進む。上流はランチャーの surface の背景のぼかしだが、アプリからはできないので、壁紙はウィンドウの FLAG_BLUR_BEHIND と blurBehindRadius（Window.setBackgroundBlurRadius は透明なウィンドウでないと効かなかった）、ホームは RenderEffect でぼかす。ホームは 0.97 倍に縮む（フェードはしない）
- 色: ぼかせるときは blur_shade_panel_fg を blur_shade_panel_bg に重ねた半透明、ぼかせないとき（CrossWindowBlurListeners で見張る）は system_accent2_200／800
- 編集中の奥行き（SpringLoadedState.DEPTH_15_PERCENT）も同じ仕組みで壁紙をぼかす（Tex-qkyu の残り）
- エミュレータで確認: 下からのスワイプで、背景がぼけながらハンドル付きのシートが下から出てくる。開き切ると上端にフィールド、その下にシート。ハンドルの下スワイプで閉じる。編集中は壁紙がぼける
- 実機（Pixel 6a）では未確認

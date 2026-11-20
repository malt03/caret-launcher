---
id: Tex-dk54
status: closed
deps: [Tex-1eos, Tex-w8ui]
links: []
created: 2026-11-20T11:16:26Z
type: task
priority: 3
assignee: Koji Murata
parent: Tex-mdus
tags: [icons]
---
# 「壁紙とスタイル」連携 Provider

テーマアイコンとアイコン形状を「壁紙とスタイル」から操作できるようにする。
- ホームの Activity に meta-data com.android.launcher3.themedicon.option と com.android.launcher3.grid.control
- Provider は BIND_WALLPAPER で保護
- icon_themed（読み出し・書き込み）、shape_options、shape を実装。グリッド関連は null
- 変更時は notifyChange し、アイコンの表示をすぐ更新する
仕様: docs/spec.md 7.3、付録

## Launcher3 からの移植

- 取り決めは android-17.0.0_r1 のソースで確認済み。内容は docs/spec.md の付録にある
- 権限の確認は Launcher3 の LauncherCustomizationProvider.kt と同じく、呼び出し元の BIND_WALLPAPER をコードで確かめる
- /workspace_items_label_hidden と /list_options は null、/default_grid は 0 を返す

## Acceptance Criteria

- [ ] 「壁紙とスタイル」でテーマアイコンを切り替えると、ランチャーにすぐ反映される
- [ ] 「壁紙とスタイル」で形状を変えると、ランチャーにすぐ反映される
- [ ] ランチャーを再起動しても設定が残る


## Notes

**2026-11-20T12:11:12Z**

provider/CustomizationProvider を実装。エミュレータ（Android 17、com.google.android.apps.wallpaper）の「壁紙とスタイル」で、アイコンのスタイル（デフォルト/ミニマル）とシェイプ5種が表示され、変更がランチャーに反映された。ホームのプレビューは未実装（null を返す）。「名前」タブも表示されるが、こちらは応答していない

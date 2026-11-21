---
id: Tex-a8mb
status: closed
deps: []
links: []
created: 2026-11-21T16:46:29Z
type: bug
priority: 1
assignee: Koji Murata
parent: Tex-pa8s
tags: [drawer]
---
# 壁紙のぼかしが少しずつかからず、急にかかったり外れたりする

実機（Pixel 6a）での指摘（2026-11-22）: 全アプリ一覧を引き上げるとき、Pixel Launcher では壁紙に徐々にぼかしがかかるが、TextLauncher では急にかかったり外れたりする。ホーム（アプリのアイコン）のぼかしはなめらか。

## Acceptance Criteria

- [x] 引き上げる指に合わせて、壁紙のぼかしが少しずつ強くなる
- [x] ダークテーマでも、シートが Pixel Launcher と同じ暗い色になる

## 結果（2026-11-22）

- 原因: 壁紙のぼかしを、ウィンドウの属性（FLAG_BLUR_BEHIND と blurBehindRadius）をフレームごとに書き換えて付けていた。属性の変更はウィンドウマネージャーを通して非同期に反映されるので、指の動きに追いつかず、飛び飛びに変わっていた
- 直し方: Window.setBackgroundBlurRadius に替えた。ウィンドウの描画と一緒にフレームごとに反映される（Launcher3 が自分の surface に付ける背景のぼかしと同じもの）。半透明のウィンドウでしか効かないので、テーマを windowIsTranslucent にした
- あわせて: ダークテーマでシートが明るいままで、アプリ名が読みにくかった。AOSP の blur_shade_panel の色にはダークテーマの値がない。Pixel Launcher はこれをフレームワークの shade_panel_fg／shade_panel_bg（ダークテーマで変わる、非公開の色）に差し替えていたので、同じ定義をアプリに書いた
- エミュレータで確認: 引き上げる途中でぼかしが段階的に強くなる。ダークテーマのシートが Pixel Launcher と同じ暗い色になる。アプリの起動と戻り、編集中の縮小とぼかしは今までどおり

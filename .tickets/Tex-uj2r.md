---
id: Tex-uj2r
status: closed
deps: []
links: []
created: 2026-11-25T05:10:33Z
type: task
priority: 1
assignee: Koji Murata
parent: Tex-tdmk
---
# アプリアイコンを作る

今のアイコンは Android Studio の雛形（緑のドロイド君）で、テーマアイコン用の単色版も同じ雛形を使っている。キャレット（‸ や点滅するカーソル）をかたどったアダプティブアイコンを作る。
- 前景・背景・単色（monochrome）の3つ。単色はテーマアイコンで、壁紙の色で塗られる
- minSdk 37 なので、mipmap-*dpi の webp と ic_launcher_round（roundIcon）は要らない。mipmap-anydpi の adaptive-icon だけにする
- 出る場所：ホームアプリの選択画面、設定のアプリ一覧・デフォルトのアプリ、通知アクセスの設定、Play ストア（512px の画像は別に要る）
- 形の案はユーザーと決める


## Notes

**2026-11-25T05:28:21Z**

2026-11-25: B 案（I 型の入力カーソル。背景 #1F2229、カーソル #8AB4F8、単色版は同じ形）に決め、mipmap-anydpi の adaptive-icon（前景・背景・単色）だけにした。雛形の webp と ic_launcher_round・roundIcon は消した。エミュレータのアプリ情報で表示を確認。テーマアイコン（単色版）の見た目は端末では見ていない（このランチャー自身は一覧に出ない）。Play ストア用の 512px の画像はまだ作っていない

**2026-11-25T05:29:33Z**

2026-11-25: Play ストア用の 512px の画像を store/play-icon-512.png（32ビット PNG、角は丸めない全面）に作った。元は store/play-icon.svg（アダプティブアイコンの見える部分、108 の中央 72 を 512 に）

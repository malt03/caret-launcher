---
id: Tex-uj2r
status: open
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


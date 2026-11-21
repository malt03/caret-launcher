---
id: Tex-1eos
status: closed
deps: [Tex-l0eq, Tex-4hiv, Tex-lczw]
links: []
created: 2026-11-20T11:16:26Z
type: task
priority: 3
assignee: Koji Murata
parent: Tex-mdus
tags: [icons]
---
# テーマアイコン・単色の自動生成・形状の切り抜き

最新の AOSP Launcher3 と iconloaderlib（MonochromeIconFactory など）の実装に従う。
- テーマアイコンがオンなら、単色アイコンをシステムのパレットで塗る。単色アイコンがないアプリは自動生成する（機能フラグで既定オフでも有効にする）
- 選んだ形状で、すべてのアイコンを切り抜く
- 適用範囲: ホーム、フォルダのプレビューと中身、検索結果、全アプリ一覧
- 初期値: テーマアイコンはオン、形状は円
仕様: docs/spec.md 7.3

## Launcher3 からの移植

- アイコン処理は iconloaderlib（Tex-lczw で取り込む）を使う
- 形状の定義は Launcher3 の shapes/ShapesProvider.kt（android-17.0.0_r1）から移植する。circle、square、four_sided_cookie、seven_sided_cookie、arch の5つ

## Acceptance Criteria

- [x] テーマアイコンがオンで、単色アイコンを持つアプリがパレットの色で表示される
- [x] 単色アイコンを持たないアプリも単色で表示される
- [x] 形状を変えると全箇所のアイコンが切り抜かれる


## Notes

**2026-11-20T12:11:12Z**

ShapesProvider.kt / IconShapeModel.kt を Launcher3 からコピー。形状での切り抜きとテーマアイコン（単色アイコンの自動生成込み）を検索結果で確認。ホーム・フォルダ・全アプリ一覧はそれぞれの画面ができてから

**2026-11-21T05:11:29Z**

形状（seven_sided_cookie）がホーム・フォルダのプレビュー・開いたフォルダ・検索結果に反映されることをエミュレータで確認。全アプリ一覧での確認は Tex-hibk に移動

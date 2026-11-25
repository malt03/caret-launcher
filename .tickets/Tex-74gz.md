---
id: Tex-74gz
status: open
deps: [Tex-5b6c, Tex-uj2r, Tex-h2no, Tex-6731, Tex-qcnm]
links: []
created: 2026-11-25T05:10:33Z
type: task
priority: 1
assignee: Koji Murata
parent: Tex-tdmk
---
# リリースビルドの通しの確認

リリースビルド（R8 で最適化）でだけ起きる不具合がないかを、実機で通して確かめる。以前、リリースビルドだけが起動時に IllegalAccessError で落ちたことがある（Tex-u1pd）。
- 署名したリリースビルドを Pixel 6a に入れ、docs/spec.md の主な操作（検索と起動、ホームの編集・フォルダ・ウィジェット、全アプリ一覧、テキスト共有、通知ドット、「壁紙とスタイル」のプレビュー、ほかのアプリからのピン留め）を一通り試す
- 最初の表示のもたつき（Tex-s1vy で見た、初回のリサイズ枠のコマ落ち）が気になるなら、ベースラインプロファイルを考える


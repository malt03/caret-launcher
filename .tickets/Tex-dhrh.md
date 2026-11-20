---
id: Tex-dhrh
status: in_progress
deps: [Tex-6b20]
links: []
created: 2026-11-20T11:16:26Z
type: task
priority: 3
assignee: Koji Murata
parent: Tex-mdus
tags: [icons]
---
# 壁紙の表示とダイナミックカラー

- テーマで windowShowWallpaper を有効にし、背景を透明にして壁紙を見せる
- ランチャー自身の UI（検索フィールド、強調表示、メニューなど）をシステムの dynamic color に合わせる
仕様: docs/spec.md 7.1、7.2

## Acceptance Criteria

- [ ] ホームの背景に壁紙が見える
- [ ] 「壁紙とスタイル」で色を変えると、ランチャーの UI の色も変わる
- [ ] ダークモードの切り替えに追従する


## Notes

**2026-11-20T12:11:12Z**

壁紙の表示とダイナミックカラーは実装済み。ダークモード切り替えへの追従は未確認

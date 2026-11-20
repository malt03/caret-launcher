---
id: Tex-z822
status: closed
deps: [Tex-4hiv]
links: []
created: 2026-11-20T11:16:26Z
type: task
priority: 1
assignee: Koji Murata
parent: Tex-epcb
tags: [search]
---
# 起動の学習スコア

半減期 H で減衰するスコアを実装する。
- 起動時: score = score × 0.5^((now − lastUpdated)/H) + 1、lastUpdated = now
- 並べ替え時: score × 0.5^((now − lastUpdated)/H)
- H の初期値は 14 日
- 計算後のスコアが 0.05 を下回ったレコードは削除
- 未入力状態のホーム配置からの起動は記録しない
仕様: docs/spec.md 3章

## Acceptance Criteria

- [ ] 減衰と加算の単体テストが通る（時刻は注入できるようにする）
- [ ] しきい値を下回ったレコードが削除される


## Notes

**2026-11-20T11:49:02Z**

search/LearningScore.kt、data/SearchStore.kt と LearningScoreTest（6件）で完了。保存は SearchStore 経由

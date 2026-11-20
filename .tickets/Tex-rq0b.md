---
id: Tex-rq0b
status: closed
deps: [Tex-dit1, Tex-z822]
links: []
created: 2026-11-20T11:16:26Z
type: task
priority: 1
assignee: Koji Murata
parent: Tex-epcb
tags: [search]
---
# 照合と並び順

アプリ名と検索文字列に対して、完全一致・前方一致・部分一致を判定し、次の順に並べる。
1. 完全一致（グループ内は学習スコアの降順）
2. 学習あり（完全一致以外で、同じ入力のスコアが 0 より大きい。スコアの降順）
3. 前方一致（アプリ名順）
4. 部分一致（アプリ名順）
学習スコアは、入力と完全に同じ文字列の記録だけを使う。
仕様: docs/spec.md 2.3

## Acceptance Criteria

- [ ] 各グループの判定と並び順の単体テストが通る
- [ ] カタカナのアプリ名がひらがなの入力で一致する


## Notes

**2026-11-20T11:49:02Z**

search/Ranker.kt と RankerTest（9件）で完了

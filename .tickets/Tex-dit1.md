---
id: Tex-dit1
status: closed
deps: []
links: []
created: 2026-11-20T11:16:26Z
type: task
priority: 1
assignee: Koji Murata
parent: Tex-epcb
tags: [search]
---
# 文字列の正規化

入力文字列と照合文字列の両方にかける正規化を実装する。
1. NFKC 正規化
2. 小文字化
3. カタカナ（U+30A1〜U+30F6）をひらがなに変換
仕様: docs/spec.md 2.2

## Acceptance Criteria

- [ ] 全角英数字、半角カナ、大文字、カタカナの単体テストが通る
- [ ] 長音記号「ー」はそのまま残る


## Notes

**2026-11-20T11:49:02Z**

search/Normalizer.kt と NormalizerTest（7件）で完了

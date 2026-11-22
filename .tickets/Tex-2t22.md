---
id: Tex-2t22
status: open
deps: []
links: []
created: 2026-11-22T00:59:03Z
type: task
priority: 3
assignee: Koji Murata
parent: Tex-epcb
---
# 検索の並び順と A〜Z の一覧の並び順の比べ方が違う

search/Ranker.kt の第3・4グループは Collator.getInstance(Locale.JAPANESE) で並べ、英字・数字を先にする決まりがない。A〜Z の一覧はコピーした LabelComparator（既定のロケールの Collator、英字・数字が先）。docs/spec.md 6.5 は一覧の中の検索を 2.3 の順に、6.4 は AlphabeticalAppsList と同じに、と書いているので2つの一覧で順が食い違う。

決めること: Ranker で com.android.launcher3.util.LabelComparator を使うか、日本語の Collator を意図どおりとして仕様に書くか


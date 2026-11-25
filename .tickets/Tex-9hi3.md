---
id: Tex-9hi3
status: open
deps: [Tex-uk3u]
links: []
created: 2026-11-25T02:16:22Z
type: feature
priority: 3
assignee: Koji Murata
tags: [search]
---
# 検索結果に「◯◯に聞く」のセルを足す（LLM アプリ）

検索欄の文字を、ユーザーが選んだアプリ（ChatGPT、Claude、Gemini など）に PROCESS_TEXT か ACTION_SEND で渡すセルを出す。docs/text-handoff-research.md の 4.4 の2（案B ＋ 案C3、初期値は C2 の表）。

## Design

- 始める前に、Pixel 6a で ChatGPT と Claude に PROCESS_TEXT と共有で渡したときの動き（新しい会話になるか、入力欄に入るだけか）を実際に起動して確かめる（ユーザーの了承を得てから）
- 渡し先はアプリの長押しメニューの「入力した文字を渡す」でオン・オフする。出せるのは PROCESS_TEXT か SEND text/plain を受けるアプリだけ
- 渡し方は PROCESS_TEXT（EXTRA_PROCESS_TEXT_READONLY=true）を優先し、なければ SEND。component を指定するときも action と type を相手のフィルターに合わせる（Safer Intents）
- <queries> に SEND text/plain と PROCESS_TEXT text/plain を足す


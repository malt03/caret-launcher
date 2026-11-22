---
id: Tex-yonv
status: open
deps: []
links: []
created: 2026-11-22T00:59:03Z
type: bug
priority: 2
assignee: Koji Murata
parent: Tex-vs8t
---
# scripts/update-upstream.sh のバグ

- 71行目で、衝突を確かめる（73〜76行目）前に tag= を書き換える。衝突したあとに実行し直すと、新しいタグを base にしてしまう
- 51〜52行目（elif [ -f "$theirs" ]）で、上流に新しく増えたパスがあると、こちらの既存のファイルを確かめずに上書きする
- derived= のループ（62〜69行目）に [ -f "$base" ] の確認がない（上流が .java を .kt にしたとき困る）
- path= と derived= のパスはリポジトリの直下からしか解決できないが、cd "$(git rev-parse --show-toplevel)" していない


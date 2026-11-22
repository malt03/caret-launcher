---
id: Tex-3k5k
status: open
deps: []
links: [Tex-6feu]
created: 2026-11-22T06:42:33Z
type: bug
priority: 3
assignee: Koji Murata
parent: Tex-ogmk
---
# 折りたたみ端末で畳む・開くとホームが詰め直される

GridSpec は今のディスプレイの大きさしか見えないので、折りたたみ端末で外側と内側のディスプレイを切り替えるとグリッドが変わり、GridSizeMigration で詰め直し（入りきらないものは削除）になる。Launcher3 は SystemWindowManagerProxy.estimateInternalDisplayBounds で内部ディスプレイをすべて見て、TYPE_MULTI_DISPLAY の1つのグリッドにする（システムの API）。アプリから両方のディスプレイの大きさを知る方法があるか調べ、なければグリッドごとに配置を持つ（Launcher3 のグリッドごとの DB）ことを検討する。実機で確かめていない。


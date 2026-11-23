---
id: Tex-9x44
status: closed
deps: []
links: []
created: 2026-11-22T00:58:38Z
type: bug
priority: 2
assignee: Koji Murata
parent: Tex-ogmk
---
# 古い Activity のホームの読み込みが、作り直したあとも走る

HomeModel はアプリ全体で1つだが、HomeLoader.start() は Activity ごとに読み直す。壁紙の色のヒント、uiMode、言語の変化で Activity が作り直されると（docs/spec.md 7.2）、古い HomeLoader の途中の rebuild のコールバックが世代の確認を通り抜け、古いグリッドの行数で checkItemPlacement をし、保存し、終わった HomeController に bindItems する。

- Launcher3: 読み込んだモデルは Activity の作り直しを越えて残り、バインドし直すだけ（addCallbacksAndLoad / rebindCallbacks）。終わるときにコールバックを外す

直し方: destroy() で世代を進めて終わった印を立て、投げた処理はそれを確かめる。できれば、モデルは読み込んだまま残してバインドし直すだけにする


## Notes

**2026-11-23T16:01:05Z**

HomeLoader に終わった印（destroyed）を足し、destroy() で世代を進めるようにした。投げた読み込み・rebuild・ショートカットの更新のコールバックは、終わった後は何もしない（checkItemPlacement・保存・bindItems をしない）。ショートカットの詳細の保存だけはモデルがアプリ全体で1つなので残す。あわせて LauncherModel.addCallbacksAndLoad と同じく、モデルが読み込み済みなら作り直した Activity はデータベースを読み直さず、そのモデルをバインドし直す（グリッドの移行と消えたアプリの整理はそのつど行う）

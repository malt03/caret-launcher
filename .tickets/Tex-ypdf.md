---
id: Tex-ypdf
status: open
deps: []
links: [Tex-rpfa]
created: 2026-11-22T00:58:38Z
type: bug
priority: 2
assignee: Koji Murata
parent: Tex-pa8s
---
# 全アプリ一覧のメニューから「ホームに追加」しても一覧が閉じない

HomeController.addToWorkspace は「Launcher3 は先に NORMAL に戻る」と書いて host.showHome() を呼ぶが、showHome は検索語を消すだけ。全アプリ一覧で開いたメニューからショートカットを追加すると、シートの裏に置かれてバウンスが見えない。

- Launcher3: LauncherAccessibilityDelegate.addToWorkspace は goToState(NORMAL, true, forSuccessCallback(...)) のあとでバインドする

直し方: showHome(onDone) で一覧をアニメーションで閉じ、終わったらバインドとバウンスをする（状態管理のチケットと合わせる）


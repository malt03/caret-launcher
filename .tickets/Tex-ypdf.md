---
id: Tex-ypdf
status: closed
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


## Notes

**2026-11-23T16:00:08Z**

Host.showHome に onDone を足し（LauncherAccessibilityDelegate.addToWorkspace の goToState(NORMAL, true, forSuccessCallback(...)) にあたる）、LauncherActivity は入力を消したうえで closeAllApps(animate = true) で一覧を閉じ、閉じ終わってから onDone を呼ぶ（一覧が出ていなければすぐ呼ぶ）。HomeController.addToWorkspace は、置き場所を決めたあとの追加・保存・バウンスを onDone に移しただけ。上流と同じく、閉じるアニメーションが途中で止められたとき（ホームボタン、スワイプで掴んだとき）は追加しない。addPendingWidget と addWidget の showHome() は引数なしのままで、一覧が開いていれば閉じるようになる。ビルドと単体テストのみ確認（端末では未確認）

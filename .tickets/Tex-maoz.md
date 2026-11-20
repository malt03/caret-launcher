---
id: Tex-maoz
status: closed
deps: [Tex-6b20, Tex-68f3]
links: []
created: 2026-11-20T11:16:26Z
type: task
priority: 1
assignee: Koji Murata
parent: Tex-epcb
tags: [search]
---
# 検索フィールドと常時キーボード

検索フィールドをキーボードの直上に置き、常にフォーカスとキーボードを保つ。
- 再表示のフック: onResume、onNewIntent、onWindowFocusChanged(true)
- ランチャーが前面に戻るたび、アプリを起動したときに入力をクリア
- フォーカスが当たっていることが UI で分かるようにする
- IME アクションは actionGo
仕様: docs/spec.md 1.1〜1.3

## Launcher3 からの移植

- キーボードの出入りへの追従は anim/KeyboardInsetAnimationCallback.java をコピーして使う
- 検索フィールドの振る舞い（入力の監視、入力のクリア）は allapps/search/AllAppsSearchBarController.java を参考にする

## Acceptance Criteria

- [ ] アプリから戻る → キーボードが出ていて、入力は空
- [ ] ホーム画面でホームボタン → キーボードが出ている
- [ ] 画面をロックして解除 → キーボードが出ている
- [ ] 通知シェードを下ろして閉じる → キーボードが出ている
- [ ] 検索フィールドがキーボードの直上にある


## Notes

**2026-11-20T12:06:10Z**

KeyboardInsetAnimationCallback（Launcher3 からコピー）でキーボードに追従。アプリから戻る、ホームボタン、ロック解除、通知シェードの後にフォーカスとキーボードが戻ることをエミュレータで確認。アニメーションが途中で切れた場合に備え、アニメーション外では毎フレーム位置を合わせ直している

---
id: Tex-ebnm
status: closed
deps: [Tex-9ipm, Tex-wgyx]
links: []
created: 2026-11-20T11:16:26Z
type: task
priority: 2
assignee: Koji Murata
parent: Tex-ogmk
tags: [home]
---
# アプリとフォルダの表示

ホームにアプリとフォルダを表示する。
- アプリは1セル、タップで起動（学習は記録しない）
- フォルダは名前なし。アイコンは中のアプリのプレビュー（Launcher3 と同様）
- タップでフォルダを開く。中のアプリもアプリ名なし
仕様: docs/spec.md 5.2

## Launcher3 からの移植

- フォルダのアイコンのプレビューと開閉のアニメーションは、Launcher3 の folder/（FolderIcon、Folder、PreviewBackground など）から移植する（範囲は Tex-wgyx で決める）

## Acceptance Criteria

- [x] ホームのアプリをタップすると起動する
- [x] フォルダのアイコンに中のアプリのプレビューが出る
- [x] フォルダをタップすると中身が開き、そこから起動できる


## Notes

**2026-11-20T12:14:50Z**

移植の範囲は docs/launcher3-porting.md の表で確定（Tex-wgyx）

**2026-11-20T12:32:36Z**

アプリとフォルダの表示、タップで起動、フォルダを開く（FolderPopup）を実装。フォルダのプレビューは Launcher3 の ClippedFolderIconLayoutRule をコピーして使用。開閉アニメーションは簡易版で、FolderAnimationManager に倣う作業が残っている

**2026-11-20T12:57:09Z**

フォルダを開いたときの背景の暗転と縮小を Launcher3（FolderSpringAnimatorSet.addWorkspaceAnimators）に合わせて追加。SpringAnimationBuilder をコピーして使用。フォルダ本体の開閉アニメーションは未移植

**2026-11-20T13:01:35Z**

フォルダの開閉アニメーションを Launcher3 の expressive 版（FolderAnimationSpringBuilderManager / FolderSpringAnimatorSet、FolderAnimationData・ClipRevealData・IconAnimationData の計算）に倣って実装。値（バネ、色、遅延、時間）は android-17.0.0_r1 と同じ。開閉・起動・ホームへの復帰をエミュレータで確認

**2026-11-20T13:46:43Z**

フォルダの開閉アニメーションを Launcher3 からのコピーに置き換え（FolderAnimationSpringBuilderManager など folder/ 一式、PreviewBackground、ShapeDelegate）。Folder・FolderIcon・BubbleTextView・Launcher などは互換クラスにし、OpenFolder・FolderIconView・AppIconView が継承する。閉じ終わりのずれがないこと、受け皿の円とフォルダ作成をエミュレータで確認

**2026-11-21T05:11:29Z**

前提の Tex-9ipm の確認が済んだので閉じる

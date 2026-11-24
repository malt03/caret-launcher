---
id: Tex-ymk9
status: closed
deps: []
links: []
created: 2026-11-23T13:49:24Z
type: bug
priority: 2
assignee: Koji Murata
parent: Tex-ogmk
---
# フォルダーの中身が列×行を超えると、アイコンが重なる

2026-11-23 のフォルダー内のドラッグの移植で分かった（前からある）。FolderGridOrganizer の最大の数を超えると、上流の FolderPagedView.arrangeChildren は2ページ目を作るが、PagedView の互換クラスは1ページしか扱わず、全ページを同じ位置に描くのでアイコンが重なる（移植前の手書きの版でも位置が折り返して重なっていた）。Launcher3 と同じくページ送り（PagedView、ページのインジケーター、ページをまたぐ並べ替えとスクロールのアラーム）を移すか、TextLauncher の決まり（docs/spec.md 5.2）として行を増やすかを決める


## Notes

**2026-11-23T16:39:42Z**

Launcher3 に合わせてフォルダのページ送りを移植した（ユーザーの「Launcher3 に従う」の決まりに沿って、行を増やす案ではなくこちら）。PagedView を ref= の1ページの互換クラスから上流のほぼ丸ごとの derived= に替え（Studio ビルドだけのアニメーション倍率を削除）、FolderPagedView と Folder で削っていたページ送り・スクロールのヒントとアラーム（onDragOver の mOnScrollHintAlarm / mScrollPauseAlarm）・ページのインジケーターと矢印・FLAG_MULTI_PAGE_ANIMATION・onMeasure・updateTextViewFocus を上流から戻した。PagedOrientationHandler・DefaultPagedViewHandler・EdgeEffectCompat・PageIndicator・PageIndicatorDots・PaginationArrow と矢印のドロワブルはそのままコピー。フッターは user_folder_icon_normalized.xml から名前の欄を除いた res-textlauncher/layout/folder_footer.xml。名前がないので、ページが1つのときはフッターの高さを取らない（TextLauncher の決め）。後ろのホームのアイテムが隣のページより先にフォーカスを取らないよう、HomeFolder.focusSearch でフォーカスの探索をフォルダの中に閉じた。FLAG_MULTI_PAGE_ANIMATION は DB に保存しない（起動し直すとインジケーターのアニメーションをもう一度見せる）。実機では未確認

**2026-11-24T13:39:12Z**

2026-11-24: 実機でページ送りが問題なく動くことを確認（ユーザー）。1ページのフォルダで下の段を出さない判断もこのままにする

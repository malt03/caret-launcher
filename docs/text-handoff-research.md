# 検索欄のテキストをほかのアプリに渡す：調査と案

検索欄に打った文字を、Web 検索や LLM アプリ（Gemini、ChatGPT、Claude、Perplexity など）に渡せるようにするための調査。2026-11-25 時点。実装はまだしていない。

- 確かめた環境
  - エミュレータ（AVD `Small_Phone`、Android 17 / API 37、Google Play 入り）：Google アプリ、Gemini、Chrome、Pixel Launcher。ChatGPT・Claude・Perplexity は入っていない
  - Pixel 6a（Android 17 / API 37）：Google アプリ 17.76.14、Gemini 1.0.995038376、Chrome 157、ChatGPT 1.2026.314、Claude 1.261120.19。Perplexity は入っていない。**adb で問い合わせただけで、何も起動していない**
  - Launcher3 は `android-17.0.0_r1` のソース（`scripts/check-upstream.py` が取ってくるもの）
- 印の意味：**実機**（Pixel 6a の adb で確認）、**エミュ**（エミュレータで起動して確認）、**公開**（公開情報。出典つき）、**不明**

---

## 1. Android の受け口

### 1.1 一覧

| 受け口 | テキストの渡し方 | 受け取るアプリ（Pixel 6a） | 既定のアプリ | 備考 |
|---|---|---|---|---|
| `Intent.ACTION_WEB_SEARCH` | `SearchManager.QUERY`（`"query"`） | Google（`GoogleSearch`）、Chrome（`SearchActivity`） | 6a では Google に決まっている（`resolve-activity` が Google を返す）。エミュレータでは決まっておらず、「アプリケーションを選択（1回のみ／常時）」が出た | Google はすぐ検索結果を出す。Chrome は検索窓に入れて候補を出すだけ（**エミュ**） |
| `Intent.ACTION_SEARCH` | `SearchManager.QUERY` | アプリ内検索用。Google アプリは受けない。Chrome は受けるが `DEFAULT` カテゴリーがないので、明示しないと届かない | ― | アプリごとの検索画面を開くもの。「Web 検索」には向かない |
| `SearchManager.INTENT_ACTION_GLOBAL_SEARCH` | `SearchManager.QUERY` | Google（`GoogleAppGlobalSearchImplicitGatewayInternal`） | `SearchManager.getGlobalSearchActivity()`（公開 API）が返す | Launcher3 の `OSEManager` が既定の検索アプリを決めるのに使う（3章） |
| `Intent.ACTION_SEND`（`text/plain`） | `Intent.EXTRA_TEXT` | Gemini、ChatGPT、Claude、Chrome、そのほか共有を受けるアプリ全部（6a で 30 件以上） | 既定はない（共有シートで選ぶもの） | Gemini は入力欄に入るだけで送信はしない（**エミュ**） |
| `Intent.ACTION_PROCESS_TEXT`（`text/plain`） | `Intent.EXTRA_PROCESS_TEXT`、`EXTRA_PROCESS_TEXT_READONLY` | ChatGPT（`TextProcessorActivity`）、Claude（`DeepLinkActivity`）、Google 翻訳、Meta AI、「選択して読み上げ」 | 既定はない（テキスト選択メニューに並ぶもの） | Google アプリにも Gemini 用の受け口（`ProcessTextGatewayActivity`）があるが、6a では無効（`disabledComponents`）。エミュレータでは有効で、Gemini の入力欄に入った（送信はしない、**エミュ**） |
| `Intent.ACTION_ASSIST` | 標準のテキストの引数はない | Google、ChatGPT（`AssistantProxyActivity`）、Claude（`AssistantOverlayActivity`）、Meta AI、Yahoo!乗換案内 | 既定のアシスタント（`ROLE_ASSISTANT`。6a では Google） | アシスタントを開くだけ。テキストは渡せない |
| `Intent.ACTION_VOICE_COMMAND` | なし | Google のみ | ― | 音声入力。テキストは渡せない |
| `VoiceInteractionService`（`ROLE_ASSISTANT`） | ― | Google、ChatGPT、Claude、Meta AI が持つ（どれもアシスタントになれる） | 6a では Google | セッションを開けるのはシステムだけ。ランチャーからは使えない |
| URL（`ACTION_VIEW`） | URL のクエリ | 下の 1.3 | App Links の確認が済んだアプリ、なければブラウザ | テキストを URL に入れるので、どこまで効くかはアプリ次第 |

### 1.2 パッケージの可視性（`<queries>`）

TextLauncher の manifest の `<queries>` は今 `MAIN`/`LAUNCHER` と `APPWIDGET_UPDATE` だけ。

- 暗黙の Intent を `startActivity` するだけなら、`<queries>` は要らない（システムが解決する）
- 受け取るアプリを調べて行に出す（`queryIntentActivities`／`resolveActivity`）には、その Intent を `<queries>` に書く。今回使いそうなもの:
  - `<intent><action android:name="android.intent.action.WEB_SEARCH" /></intent>`
  - `<intent><action android:name="android.intent.action.SEND" /><data android:mimeType="text/plain" /></intent>`
  - `<intent><action android:name="android.intent.action.PROCESS_TEXT" /><data android:mimeType="text/plain" /></intent>`
  - URL で開くアプリを調べるなら、`<intent><action android:name="android.intent.action.VIEW" /><category android:name="android.intent.category.BROWSABLE" /><data android:scheme="https" /></intent>`
- 全アプリの `MAIN`/`LAUNCHER` はもう見えているので、アイコンとラベルは取れる。ただし「そのアプリが SEND を受けるか」は上の宣言がないと分からない

### 1.3 アプリごとの受け口（manifest で確かめたもの）

| アプリ | 共有（SEND） | PROCESS_TEXT | URL | アシスタント |
|---|---|---|---|---|
| Google（`com.google.android.googlequicksearchbox`） | 画像と「保存」だけ（テキストの検索はしない） | 6a では無効 | `https://www.google.com/search?q=…` は Chrome が開く（Google アプリの App Links にはない） | `ROLE_ASSISTANT` の持ち主 |
| Gemini（`com.google.android.apps.bard`） | あり（入力欄に入る、**エミュ**） | なし（Google アプリ側にあるが 6a では無効） | `https://gemini.google.com/app` は Google アプリの `MainAssistantDeeplinkAnimated` が受ける（App Links 確認済み）。adb から `?q=` を付けて開くと「Invalid MA deeplink Uri」とログを出して閉じた（**エミュ**）。ランチャーから開いたときにどうなるかは**不明** | Google アプリとして |
| ChatGPT（`com.openai.chatgpt`） | あり（`MainActivity`） | あり（`TextProcessorActivity`） | `https://chatgpt.com/`（パス `/` を受ける。App Links 確認済み）、`chatgpt://`。`?q=` を見るかは manifest からは分からない | ASSIST と `VoiceInteractionService` あり |
| Claude（`com.anthropic.claude`） | あり（`DeepLinkActivity`） | あり（`DeepLinkActivity`） | `https://claude.ai/new`（App Links 確認済み）、`claude://`。`?q=` を見るかは manifest からは分からない | ASSIST・VOICE_ASSIST と `VoiceInteractionService` あり |
| Perplexity | 6a・エミュレータとも入っていないので**不明**。6a では `https://www.perplexity.ai/search?q=` は Chrome が開く | | | |
| Chrome | あり | なし | http/https、`googlechrome://` | ― |

公開情報で補うと次のとおり（**公開**）。

- **ACTION_WEB_SEARCH**：`Intent` の javadoc では、`QUERY` が http/https の URL ならそのページを開き、それ以外なら Web 検索する。Google アプリはすぐ検索する（**エミュ**）。Chrome は検索語を入れた検索画面を開くだけ（**エミュ**、Chromium の `SearchActivityUtils.getIntentQuery`）。Firefox は既定の検索エンジンで新しいタブを開いてすぐ検索し、DuckDuckGo は `SelectedTextSearchActivity` が受ける（どちらもソースで確認）
- **Gemini**：`gemini.google.com/app?q=` に Google の公式の説明はない。URL から会話を始められないことを理由に作られた拡張機能がある（[gemini-url-prompt](https://github.com/elliot79313/gemini-url-prompt)）ので、効かない見込みが高い。テキスト選択の「Gemini に聞く」は Google アプリの PROCESS_TEXT（[Android Authority](https://www.androidauthority.com/ask-gemini-text-select-3531976/)）で、端末によって有効・無効が違う（6a では無効）
- **ChatGPT**：Web の `chatgpt.com/?q=` は入力欄に入る。以前は自動で送信されていたが、悪用の報告（[Tenable TRA-2025-22](https://www.tenable.com/security/research/tra-2025-22)）を受けて自動送信は止められた。アプリで開いたときの動きは**不明**。既定のアシスタントにできる（[9to5Google](https://9to5google.com/2025/03/14/chatgpt-default-assistant-android/)）
- **Claude**：`claude://claude.ai/new?q=` はデスクトップ版の公式の機能で、入力欄に入るだけ（[Claude ヘルプ](https://support.claude.com/en/articles/14729294-open-claude-desktop-with-a-link)）。モバイル版で公式に載っているのは Claude Code 用の `claude://code/new?q=`・`https://claude.ai/code/new?q=` だけ（[Claude ヘルプ](https://support.claude.com/en/articles/14898120-open-the-claude-mobile-app-with-a-link)）。共有と PROCESS_TEXT で渡したテキストは今の入力欄に足されるだけで、送信されない（[anthropics/claude-code#27739](https://github.com/anthropics/claude-code/issues/27739)）
- **Perplexity**：`perplexity.ai/search?q=` には公式の説明がない。既定のアシスタントにできる（[9to5Google](https://9to5google.com/2025/01/24/perplexity-assistant-android/)）

**まとめ**：

- Web 検索は、`ACTION_WEB_SEARCH` なら標準のやり方で、既定のアプリが検索まで進める
- LLM アプリは、共有・PROCESS_TEXT・URL のどれで渡しても「入力欄に入るだけ」で、送信はユーザーが押す。公式に約束されているのは Claude のデスクトップ版の URL だけで、ほかは非公式で、変わりうる
- PROCESS_TEXT と共有は、Android の標準の受け口で、アプリの内部の仕組みに頼らない

---

## 2. Android 17 での制限

- **Android 17 で新しく増えた制限はない**（**公開**、[Android 17 の変更点（ターゲット）](https://developer.android.com/about/versions/17/behavior-changes-17)・[全アプリ](https://developer.android.com/about/versions/17/behavior-changes-all)）。Intent の照合、`ACTION_WEB_SEARCH`、`SearchManager`、App Links について新しい変更は書かれていない
  - バックグラウンドからの起動の強化は `IntentSender` が対象。前面のランチャーが、ユーザーの操作で `startActivity` する場合は関係ない
  - 共有（`ACTION_SEND`）での URI の権限の暗黙の付与は、Android 18 でなくなる予定。テキストだけを渡すなら関係ない
- **Safer Intents**（Android 16 で入った、受け手側が選ぶもの。将来は既定になる予定、[Android 16 の変更点](https://developer.android.com/about/versions/16/behavior-changes-16)）
  - 内容：明示的な Intent も相手の intent-filter に合わなければ届かない。action のない Intent は合わない
  - 対策：component を指定して送るときも、action・type・category を相手のフィルターに合わせておく
- **パッケージの可視性**：1.2 のとおり
- **既定のアプリを知る方法**
  - Web 検索：`resolveActivity(ACTION_WEB_SEARCH, MATCH_DEFAULT_ONLY)` で分かる。既定が決まっていないと選択画面（`ResolverActivity`）が返る
  - 全体の検索：`SearchManager.getGlobalSearchActivity()` は公開 API
  - `RoleManager.ROLE_SEARCH_ENGINE`：API 37.2 で入った（**公開**、[RoleManager](https://developer.android.com/reference/android/app/role/RoleManager)）。ただし持ち主を調べる `getRoleHolders` はシステム用。ほかのアプリが持ち主を知る方法は**不明**
  - `Settings.Secure` の `selected_search_engine`：GMS が書く、文書にないキー。上流の `OSEManager` が読む。`Settings` に定義がないキーは、SettingsProvider がアプリにも読ませる作りなので読めそうだが、アプリから試してはいない（**不明**）
- **アシスタント（`ROLE_ASSISTANT`）にテキストを渡す方法はない**
  - `ACTION_ASSIST` で開けるが、文字列を渡す標準の extra はない（`EXTRA_ASSIST_INPUT_HINT_KEYBOARD` は「キーボードで入力しそう」というヒントだけ）
  - `SearchManager.launchAssist` はシステム用。`VoiceInteractionService` のセッションはシステムが開く

---

## 3. Launcher3 と Pixel Launcher の扱い

### 3.1 AOSP Launcher3（android-17.0.0_r1）

- 全アプリ一覧の検索欄（`AllAppsSearchBarController.onEditorAction`）は、Enter で `launchHighlightedItem()`（1位のアプリ）を起動するだけ。アプリが見つからないときの Web 検索はない
- 検索結果の並び（`DefaultSearchAdapterProvider`）もアプリだけ。昔の Launcher3 にあった「Play ストアで検索」の行（`VIEW_TYPE_SEARCH_MARKET`）は今はない。`ApiWrapper.getMarketSearchIntent` は、インストール中のアイコンをタップしたときにストアを開くためのもの
- ホットシートの検索バー（QSB）は `qsb/` にある。Android 17 で入った仕組みで、打った文字は扱わない
  - `OSEManager`（On-device Search Engine）：既定の検索アプリを、`Settings.Secure` の `selected_search_engine`（EU の検索エンジン選択画面で選ばれたもの）から決める。なければ `SearchManager.getGlobalSearchActivity()` のパッケージ、それもなければ `config.xml` の `fallback_search_package_name`（AOSP では空）。そのアプリが `ACTION_SEARCH` を受けるかも調べる
  - `OseWidgetManager`／`OseWidgetView`：その検索アプリのウィジェットをホットシートに置く。置けないときの代わりのボタン「Search」は、`ACTION_SEARCH` をそのアプリに送るか、そのアプリを起動するか、ブラウザで空のページを開く。テキストは渡さない
  - 6a では `selected_search_engine` も `getGlobalSearchActivity()` も Google アプリ
- **上流に寄せられるところ**：「どのアプリを既定の検索先とみなすか」の決め方（`OSEManager.reloadOse` の順番）だけ。打った文字を渡す処理は上流にないので、自前で書くことになる。`selected_search_engine` をサードパーティのアプリが読めるかは 2章

### 3.2 Pixel Launcher

Pixel Launcher の検索は Google の非公開の実装で、AOSP にはない。公開情報（**公開**）では次のとおり。

- 2025年12月から、ホーム下部の検索バーをタップすると Google アプリの全画面の検索が開く（[9to5Google](https://9to5google.com/2025/12/16/pixel-launcher-search-google-app/)）。全アプリ一覧の上の検索欄は、引き続き Pixel Launcher 自身の検索
- Android 17 Beta 1 で、検索バーは Google のウィジェットそのものになった。隠し設定で、DuckDuckGo・Firefox などの検索アプリを選べる（[Android Authority](https://www.androidauthority.com/android-17-beta-1-pixel-launcher-search-bar-redesign-3641175/)）。上流の `OSEManager`／`OseWidgetView` がこれに当たる
- 2026年4月から、表示が「Ask Google」になり、AI モードを選べる（[9to5Google](https://9to5google.com/2026/04/29/android-search-ask-google-bar/)）
- 全アプリ一覧の検索欄に、アプリにない語を打ったときの行と Enter の動きを書いた資料は見つからなかった（**不明**）

ほかのランチャーのやり方（ソースで確認）も参考になる。

- Kvaesitso：Web 検索は `ACTION_WEB_SEARCH`。ユーザーが足す検索先は `${1}` を入れた URL のテンプレートで、`ACTION_VIEW` で開く。アプリ内の検索は `ACTION_SEARCH` を受けるアプリを集めて送る。共有は `ACTION_SEND` を選択画面で送る
- KISS：URL のテンプレートが空なら `ACTION_WEB_SEARCH`、あれば `%s` を置き換えて開く。「Enter で Web を検索」の設定がある
- Olauncher：`ACTION_WEB_SEARCH`
- ChatGPT・Claude・Gemini に直接テキストを渡すランチャーは見つからなかった

---

## 4. 案

### 4.1 今の仕様との関係

今の `docs/spec.md` は、Web 検索を入れない方針で書かれている。どの案でも、まず次を書き換える必要がある。

- 2.1：「Web 検索…は検索対象にしない」
- 2.4：「候補が0件…のときは何もしない」
- 8章の表：「アプリ以外の検索（ショートカット、連絡先、Web など）：入れない方針」

### 4.2 案

**案A：アプリが見つからないとき、Enter で Web 検索する**

- `ACTION_WEB_SEARCH` に `SearchManager.QUERY` を付け、既定のアプリに送る（6a なら Google がすぐ検索する）
- 長所
  - 画面を変えない。いちばん小さい変更
  - Kvaesitso・KISS・Olauncher と同じやり方
  - アプリ名を決め打ちしない
- 短所
  - 何が起きるか画面に出ないので、打ち間違いがそのまま検索に飛ぶ
  - 既定が決まっていない端末では、Enter のたびに選択画面が出る（エミュレータがそうだった）
  - アプリが1件でも見つかると Web 検索できない

**案B：検索結果のグリッドに「操作」のセルを足す**

- アプリの後ろに、アプリと同じ見た目のセルを並べる
  - 「Web で検索」：アイコンは既定の Web 検索アプリのもの。選択画面になるときは虫眼鏡
  - 「◯◯に聞く」：アイコンはそのアプリのもの
- 検索結果は下から並ぶ（2.5）ので、アプリがあれば操作のセルはその上に来る。アプリが0件なら、操作のセルが1位（一番下の左）になり、Enter で動く。案A を含む形
- 長所
  - 何が起きるか見える
  - 2.5 のグリッド、2.6 のフォーカス枠・矢印キー、タップをそのまま使える
  - アプリがあっても、タップか矢印キーで Web 検索や LLM を選べる
- 短所
  - セルにラベルを入れる幅が狭い（「Web で検索」くらいしか入らない）
  - アプリ以外のセルが入るので、検索結果のアダプターと長押し（ドラッグしない、など）に場合分けが要る
  - 上流の Launcher3 にはない部分なので、自前のコードになる

**案C：渡し先の選び方（案B の「◯◯に聞く」のセルに何を出すか）**

- C1：PROCESS_TEXT を受けるアプリを自動で並べる
  - 長所：アプリ名を決め打ちしない。テキスト選択メニューと同じ顔ぶれになる
  - 短所：LLM でないもの（Google 翻訳、「選択して読み上げ」）も入る。Gemini は 6a では受けないので出てこない
- C2：知っているアプリと渡し方の表を持つ
  - 例：Gemini は共有、ChatGPT と Claude は PROCESS_TEXT か共有、Perplexity は URL
  - 長所：アプリごとに一番よい渡し方を選べる
  - 短所：表の手入れが要る。非公式の受け口は変わりうる
- C3：ユーザーが選ぶ
  - アプリの長押しメニューに「入力した文字を渡す」を足し、オンにしたアプリだけを「◯◯に聞く」に出す
  - 出せるのは、共有か PROCESS_TEXT を受けるアプリだけ
  - 設定画面を置かない方針（8章）とも合い、「検索文字列を編集」と同じ入口になる
  - 渡し方は、PROCESS_TEXT を受けるならそれ、なければ共有にする。PROCESS_TEXT は「この文字を処理して」という意味の受け口で、受け口もアプリの中で小さく分かれているため
  - 長所：決め打ちがなく、ユーザーが使うものだけが出る
  - 短所：最初は何も出ないので、機能に気付きにくい。初期値として、入っているアプリのうち C2 の表にあるものをオンにしておく、などの工夫が要る

**案D：検索文字列で渡し先を決める**

- 4章の検索文字列（たとえば ChatGPT に `gpt` と登録）を頭に付けて「`gpt 東京の天気`」と打つと、ChatGPT に「東京の天気」を渡すセルを出す
- 長所：キーボードだけで素早く選べる。今ある検索文字列の仕組みを使える
- 短所：入力の区切り（空白）の決まりが要る。2.3 の並び順に割り込むので、仕様が複雑になる。案B と C ができてから考えればよい

**採らないもの**

- `ACTION_ASSIST`／`VoiceInteractionService`：テキストを渡せない
- LLM アプリの `?q=` の URL：Claude のデスクトップ版のほかは公式でなく、効かないもの（Gemini）や、効き方が変わったもの（ChatGPT）がある。PROCESS_TEXT と共有で同じことができる
- `ACTION_SEARCH`：アプリの中の検索用で、Google アプリは受けない

### 4.3 LLM アプリの共通の制限

- どの渡し方でも、LLM アプリは入力欄に入れるだけで、送信はしない。ユーザーがもう一度送信を押す。ランチャー側からは変えられない
- 共有で渡すと、アプリによっては「今開いている会話の入力欄に足す」になり、新しい会話にならない（Claude の報告）

### 4.4 提案：どこから進めるか

1. **案B で、まず「Web で検索」のセル1つだけを入れる（案A を含む）**（Tex-uk3u）
   - `ACTION_WEB_SEARCH` は標準の受け口で、既定のアプリ（6a では Google）がすぐ検索まで進め、アプリ名の決め打ちが要らない
   - グリッドに操作のセルを足す土台ができるので、LLM の行はその上に足せる
   - 仕様の変更：2.1、2.4、2.5、8章。2.6 の枠は、1位が操作のセルのときも付ける。全アプリ一覧の中の検索（6.5）にも同じセルを出す。起動の学習（3章）は、操作のセルでは記録しない（入力がほぼ毎回違うため）
   - `<queries>` に `WEB_SEARCH` を足す
2. **次に、案B ＋ 案C3（初期値は C2 の表）で「◯◯に聞く」を足す**（Tex-9hi3）
   - その前に、6a で ChatGPT と Claude に PROCESS_TEXT・共有で渡したときの動き（新しい会話になるか、入力欄に入るだけか）を実際に起動して確かめる。今回は adb で問い合わせただけ
3. 案D は、2 が終わってから必要か考える

---

## 5. 決めたこと（2026-11-25）

4.4 の提案から次のように変えて、実装した（`docs/spec.md` 2.7）。

- 渡し方は共有（`ACTION_SEND` の `text/plain`）だけにする。Web 検索も、検索アプリやブラウザに共有すれば足りるので、`ACTION_WEB_SEARCH` のセル（案A・案B の「Web で検索」、Tex-uk3u）は作らない
- 検索結果のグリッドには混ぜない。選んだアプリ（3つまで）のアイコンを、検索フィールドの右端にボタンとして並べる
- 選ぶ入口は、設定画面ではなく、空いている領域の長押しメニューの「テキスト共有」（Launcher3 の「ホームの設定」の位置）。ダイアログで、並べ替え（ハンドルのドラッグ）と外す（×）ができる
- アプリの一覧は、はじめ OS のアプリ選択画面（`ACTION_PICK_ACTIVITY`。Pixel 6a・エミュレータとも `com.android.settings/.ActivityPicker`）に任せたが、見た目が古いままなので、同じ見た目のダイアログで自前で出すことにした（`queryIntentActivities`）。共有シートと「アプリケーションを選択」の画面は、選んだアプリを起動してしまい、選ぶだけには使えない
- アイコンとラベルは、そのアプリが共有の受け口に付けたもの（`ResolveInfo`）。`<queries>` に `SEND` の `text/plain` を足した


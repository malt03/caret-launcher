# Feature graphic tagline

Research done on 2026-11-26. The Google Play feature graphic (rendered by `scripts/feature-graphic.py`) has a two-line tagline under the app name. Each listing language's tagline was written by a subagent playing a native copywriter of that language.

## Source

The developer's Japanese tagline:

- 「常時キーボードを表示」 ("the keyboard is always shown")
- 「数文字打って、アプリを起動」 ("type a few letters and launch the app")

## Method

- One subagent (Claude) per language, asked in that language to write as a native copywriter of it. de-DE was asked first, as a long language, to check the layout; the others followed.
- It read only its own language's short and full descriptions, so that the tagline uses the page's words. It was not shown the other languages' answers.
- Each line should fit without being set smaller: about 36 characters in Latin, Greek and Cyrillic scripts, 30 in Thai, Devanagari, Arabic and Hebrew, and 18 in Chinese and Korean. Arabic and Hebrew were told that the layout is mirrored.
- It answered with the two lines, a literal Japanese translation, the reason, and one or two other candidates.

Each language's question and answer, as written in that language, are in its own file ([de-DE.md](de-DE.md) and so on). The taglines in use are in `store/feature-graphic/<lang>.json`.

## Results

| Language | Line 1 | Line 2 |
|---|---|---|
| ja-JP | 常時キーボードを表示 | 数文字打って、アプリを起動 |
| en-US | The keyboard is always up | Type a few letters to open an app |
| de-DE | Die Tastatur ist immer eingeblendet | Ein paar Buchstaben öffnen die App |
| fr-FR | Le clavier reste toujours affiché | Quelques lettres, et l’appli s’ouvre |
| fr-CA | Le clavier reste toujours affiché | Quelques lettres et l'appli s'ouvre |
| es-ES | El teclado, siempre a la vista | Teclea unas letras y abre la app |
| es-419 | El teclado siempre a la vista | Escribe unas letras y abre la app |
| es-US | El teclado siempre abierto | Escribe unas letras y abre la app |
| pt-BR | O teclado sempre na tela | Digite poucas letras e o app abre |
| pt-PT | Teclado sempre aberto | Escreva umas letras e abra a app |
| it-IT | Tastiera sempre aperta | Digita poche lettere e apri l'app |
| nl-NL | Het toetsenbord altijd in beeld | Typ een paar letters en open je app |
| da-DK | Tastaturet er altid fremme | Skriv et par bogstaver og åbn appen |
| sv-SE | Tangentbordet är alltid framme | Skriv några bokstäver, öppna appen |
| no-NO | Tastaturet er alltid fremme | Skriv noen bokstaver, så åpnes appen |
| fi-FI | Näppäimistö aina auki | Muutama kirjain avaa sovelluksen |
| pl-PL | Klawiatura zawsze na ekranie | Wpisz kilka liter i otwórz aplikację |
| cs-CZ | Klávesnice je vždy na obrazovce | Pár písmen a aplikace se otevře |
| sk | Klávesnica vždy na obrazovke | Napíšte pár písmen a apka sa otvorí |
| hu-HU | A billentyűzet mindig kint van | Pár betű, és nyílik az alkalmazás |
| ro | Tastatura e mereu afișată | Câteva litere și deschizi aplicația |
| el-GR | Πληκτρολόγιο πάντα ανοιχτό | Λίγα γράμματα και η εφαρμογή ανοίγει |
| tr-TR | Klavye hep açık | Birkaç harf yaz, uygulama açılsın |
| ru-RU | Клавиатура всегда на экране | Пара букв — и приложение откроется |
| uk | Клавіатура завжди відкрита | Кілька літер — і застосунок відкрито |
| vi | Bàn phím luôn hiện sẵn | Gõ vài chữ là mở được ứng dụng |
| id | Keyboard selalu tampil | Ketik beberapa huruf, buka aplikasi |
| th | คีย์บอร์ดขึ้นพร้อมตลอดเวลา | พิมพ์ไม่กี่ตัว ก็เปิดแอปได้ |
| hi-IN | कीबोर्ड हमेशा खुला | कुछ अक्षर टाइप करें, ऐप खोलें |
| ar | لوحة المفاتيح ظاهرة دائمًا | اكتب بضعة أحرف لتفتح تطبيقك |
| iw-IL | המקלדת תמיד פתוחה | כמה אותיות, והאפליקציה נפתחת |
| ko-KR | 늘 떠 있는 키보드 | 몇 글자만 입력하면 앱이 열립니다 |
| zh-CN | 键盘常驻 | 打几个字，就能打开应用 |
| zh-TW | 鍵盤永遠開著 | 打幾個字，就能開啟應用程式 |
| zh-HK | 鍵盤常駐畫面 | 輸入幾個字，即可開啟應用程式 |

When everything was first rendered, the script shrank four texts slightly: the second tagline line of el-GR and ru-RU (25px to 24.5px), and the photo app names of el-GR ("Φωτογραφίες", to 14.5px) and ro ("Google Foto", to 17px).

## Findings

- Most languages took their wording from their own descriptions, so the graphic and the page say the same thing.
- Many dropped the imperative "type" from line 2 ("a few letters, and the app opens"), because it did not fit and the search field in the graphic already shows the typing.
- Several chose the local word for a keyboard being shown ("up", "fremme", "aperta", "offen", "開著") over a literal "displayed", which they found sounds like a settings label.

## Caveats

- Every answer came from the same model, not from real native speakers.

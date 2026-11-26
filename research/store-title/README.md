# Store title subtitles

Research done on 2026-11-26. The Google Play title of each listing language is "Caret Launcher - " and a subtitle ("Caret 桌面 - " in zh-CN, matching the app name there; see [app-name](../app-name/README.md)). Each language's subtitle was written by a subagent playing a native speaker of it.

## Requirements

- The title is at most 30 characters, so the subtitle is at most 13 (19 in zh-CN).
- It includes the word that users who know their phones well enough to change their launcher use most for this kind of app, and are likely to search for. A loose translation of the Japanese subtitle is best, but a forced translation is not wanted: each language writes its own best subtitle.
- If that word is "launcher", which the name already has, it need not be repeated. Then the subtitle uses the next most common word, or words that tell what the app does. (In Japanese, the katakana ランチャー is a different search word from "Launcher", so it is repeated.)
- It follows Google Play's rules: no bare keyword lists, no promotional or ranking words, no emoji, no all caps.

The Japanese title, decided by the developer, is 「Caret Launcher - 文字を打って開くランチャー」 ("a launcher you open apps with by typing").

## Method

- One subagent (Claude) per language, asked in that language to answer as a native speaker of it.
- It read only its own language's short and full descriptions. It was not shown the other languages' answers or the current titles.
- It answered with the subtitle, the full title, the word it included and why, a literal Japanese translation, and one or two other candidates.
- The character counts were checked afterwards in Unicode code points (NFC).

The question, in English (each language got a translation of it; this is the en-US one):

> You are a native speaker of American English, and you know smartphone apps and how people find apps on Google Play well.
>
> We are writing the title for the English (US) Google Play page of "Caret Launcher", an Android launcher (home screen app). The app's description is in these two files. Read only these two: (its short-description.txt and full-description.txt)
>
> The title will be "Caret Launcher - " followed by a subtitle.
>
> Rules for the subtitle:
> - At most 13 characters, spaces included. The whole title can be at most 30 characters, and "Caret Launcher - " takes 17.
> - Include the word that English-speaking users who know their phones well enough to change their launcher most often use for this kind of app, and would likely search for.
> - "Caret Launcher" already contains "Launcher". If that is also the most common word in your language, you do not need to repeat it. In that case, use the next most common word, or words that tell what the app does.
> - The Japanese subtitle is 「文字を打って開くランチャー」 ("a launcher you open apps with by typing"). A subtitle close to that meaning is best if it fits the rules, but do not force a translation. Write the best subtitle for English.
> - Follow Google Play's rules: no bare keyword lists, no promotional or ranking words such as "best", "#1" or "free", no emoji, and no all caps.
>
> Answer in this form:
> - Subtitle: (the subtitle and its number of characters)
> - Full title: Caret Launcher - … (number of characters)
> - Word included: (the word, and why you think it is the most used and most searched)
> - Literal translation into Japanese:
> - Other candidates: (1 or 2, each with its number of characters and why you did not choose it)
>
> Do not read any other file, do not use any other tool, and do not write any file.

Each language's question and answer, as written in that language, are in its own file ([en-US.md](en-US.md) and so on).

## Results

The titles in use. "Kind" is what the subtitle carries: an action (what the app does), or the local word for a launcher.

| Language | Subtitle | Title length | Literal meaning | Kind |
|---|---|---|---|---|
| ja-JP | 文字を打って開くランチャー | 30 | a launcher you open apps with by typing | local word for launcher |
| en-US | Type to Open | 29 | type to open | action |
| de-DE | App eintippen | 30 | type in the app | action |
| fr-FR | Tapez, ouvrez | 30 | type, open | action |
| fr-CA | Tapez, ouvrez | 30 | type, open | action |
| es-ES | Teclea y abre | 30 | type and open | action |
| es-419 | Teclea y abre | 30 | type and open | action |
| es-US | Teclea y abre | 30 | type and open | action |
| pt-BR | Digite e abra | 30 | type and open | action |
| pt-PT | Escreva, abra | 30 | write, open | action (replaced) |
| it-IT | Digita e apri | 30 | type and open | action |
| nl-NL | Typ en open | 28 | type and open | action |
| da-DK | Skriv og åbn | 29 | write and open | action |
| sv-SE | Skriv & öppna | 30 | write & open | action |
| no-NO | Skriv og åpne | 30 | write and open | action |
| fi-FI | Hae ja avaa | 28 | search and open | action (replaced) |
| pl-PL | Pisz i otwórz | 30 | write and open | action |
| cs-CZ | Napiš a spusť | 30 | write and launch | action |
| sk | Píš a otváraj | 30 | write and open | action |
| hu-HU | Gépelj, nyiss | 30 | type, open | action (replaced) |
| ro | Caută tastând | 30 | search by typing | action (replaced) |
| el-GR | Γράψε, άνοιξε | 30 | write, open | action (replaced) |
| tr-TR | Yazarak aç | 27 | open by typing | action |
| uk | пошук набором | 30 | search by typing | action |
| vi | Gõ để mở app | 29 | type to open apps | action |
| id | Ketik & buka | 29 | type & open | action |
| hi-IN | टाइप कर खोलें | 30 | type and open | action |
| th | พิมพ์เปิดแอป | 29 | type to open apps | action |
| ar | مشغل بالكتابة | 30 | a launcher used by typing | local word for launcher (replaced) |
| ko-KR | 입력해서 앱을 여는 런처 | 30 | a launcher that opens apps by typing | local word for launcher |
| ru-RU | лаунчер-поиск | 30 | launcher and search | local word for launcher |
| iw-IL | לאנצ'ר בהקלדה | 30 | a launcher by typing | local word for launcher |
| zh-CN | 打字就能打开应用的启动器 | 23 | a launcher that opens apps just by typing | local word for launcher |
| zh-TW | 打字開啟App的桌面啟動器 | 30 | a home screen launcher that opens apps by typing | local word for launcher |
| zh-HK | 打字開App的啟動器 | 27 | a launcher that opens apps by typing | local word for launcher |

Replaced subtitles, and why:

- pt-PT "Ecrã inicial", fi-FI "Aloitusnäyttö", hu-HU "Kezdőképernyő" and el-GR "Αρχική οθόνη" were the local word for "home screen". Most other languages rejected that word because it says the same thing as "Launcher" again and not what the app does, so these four use their action candidate instead. pt-PT's candidate "Escreve e abre" was 14 characters (the subagent miscounted), so it was asked again and gave "Escreva, abra".
- ar "لانشر نصي" ("text launcher") could read as a launcher made only of text. It uses its other candidate.
- ro "Lansator text" had the same problem. It uses its other candidate "Caută tastând". Its subagent noted that "search" may make the app look like a search app.

## Findings

- In most languages written in the Latin script, "launcher" is the word people use and search, so the subtitle spends its 13 characters on what the app does, close to the Japanese meaning.
- Where the local script differs from the Latin one, several languages added the launcher word in their own script (런처, лаунчер, לאנצ'ר, the Arabic مشغل, 启动器 / 啟動器), because search treats it as a different word from "Launcher". This is the same reasoning as the Japanese ランチャー.
- Some languages avoided "keyboard" in the subtitle, because people who search for it usually want a keyboard app.
- pl-PL, cs-CZ and sk use the informal imperative in the subtitle while their descriptions are formal, because the formal form does not fit in 13 characters. They called this normal for short slogans.

## Caveats

- Every answer came from the same model, not from real native speakers, and its claims about what people search for are not backed by search data.
- Subagents miscounted characters at least once, so the counts above were checked separately.

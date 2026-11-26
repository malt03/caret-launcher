# Translating the app name

Research done on 2026-11-26. It asks whether the app name shown on the device (`app_name`) should be translated in each language or stay "Caret Launcher" in English. Each language was asked of a subagent playing a native speaker of that language.

## Decision

- Simplified Chinese (zh-CN) gets "Caret 桌面" (`app/src/main/res/values-zh-rCN/strings.xml`).
- Every other language keeps "Caret Launcher".

## Background

When the store listing grew to 33 more languages (55c35cf), each language was meant to decide its own title. Almost every language kept "Caret Launcher" instead, because of how the request was written:

- It first showed the Japanese example: "In Japanese the title stays Caret Launcher, because the English name sounds natural there."
- It asked that the brand stay recognizable.
- The languages written later saw the files of the earlier ones and followed them.
- It asked only about the store title, not about the name on the device.

So the question was asked again, about the name on the device, with nothing to follow.

## Method

- One subagent (Claude) per language, asked in that language to answer as a native speaker of it.
- It was not shown the other languages' answers, the Japanese example, or the names of other launchers.
- It answered from its own sense, without tools and without reading files.
- It first made one name in its own language, in any form (a translation, a partial translation, a transliteration, an added description), and then compared that name with the English one. Asked to compare first, a model leans toward keeping a brand name in English.
- The languages were asked in this order:
  1. Languages that seemed culturally far from English: th, hi-IN, ar, vi, ko-KR, iw-IL and id, with de-DE as a baseline.
  2. The three Chinese ones: zh-CN, zh-HK and zh-TW.
  3. The rest, including ja-JP. The developer finds the Japanese transliteration "キャレットランチャー" odd, so the Japanese answer checks whether the answers match a native speaker's sense.
- zh-CN alone was later asked which of three names, with "Caret 桌面" added, looks the highest quality.

The question, in English (each language got a translation of it):

> You are a native speaker of [language], and you know smartphone apps well.
>
> There is a launcher (home screen app) for Android called "Caret Launcher". The keyboard is always shown at the bottom of the screen, and typing a few letters finds an app and opens it. Above the keyboard is an ordinary home screen.
>
> We are deciding how to show this app's name on devices set to [language]. The app name appears in the device settings, in the "Default home app" list, in the app info and in the app list.
>
> 1. First, if you gave this app a [language] name, what would it be? Give the one name in the form that seems most natural to you. The form is free: a translation, a partial translation, a transliteration of the sound, or an added description.
> 2. Compare these two:
>    - A: "Caret Launcher" (in English, as it is)
>    - B: the name from question 1
>
>    Compare two points:
>    - Which feels more natural to people who use [language]?
>    - Which makes the app look higher quality?
> 3. In the end, which do you recommend as this app's name: A or B?
>
> Answer in this form:
> - Name from question 1: (the name and its number of characters)
> - Naturalness: A/B (reason)
> - Impression of quality: A/B (reason)
> - Recommended name: A/B (reason)

Each language's question and answer, as written in that language, are in its own file ([zh-CN.md](zh-CN.md) and so on).

## Results

A is the English name; B is the name made in the language.

| Language | Name B | Naturalness | Quality | Recommended |
|---|---|---|---|---|
| zh-CN | 光标桌面 | **B** | A (slightly) | **B** |
| zh-TW | Caret 啟動器 | B (slightly) | A | A |
| ko-KR | 캐럿 런처 | B (slightly) | A | A |
| ja-JP | キャレット ランチャー | B (slightly) | A | A |
| ro | Lansator Caret | B (slightly) | A | A |
| uk | Лаунчер Caret | B (slightly) | A | A |
| hu-HU | Caret Indító | B (slightly) | A | A |
| zh-HK | Caret 啟動器 | A | A | A |
| ru-RU | Каретка Лаунчер | A | A | A |
| th | แคเร็ต ลอนเชอร์ | A | A | A |
| hi-IN | कैरेट लॉन्चर | A | A | A |
| ar | مشغّل كاريت | A | A | A |
| iw-IL | קארט לאנצ'ר | A | A | A |
| vi | Trình khởi chạy Caret | A | A | A |
| id | Peluncur Caret | A | A | A |
| el-GR | Caret Εκκίνηση | A | A | A |
| tr-TR | Caret Başlatıcı | A | A | A |
| de-DE | Caret Startbildschirm | A | A | A |
| fr-FR | Lanceur Caret | A | A | A |
| fr-CA | Lanceur Caret | A | A | A |
| es-ES | Lanzador Caret | A | A | A |
| es-419 | Lanzador Caret | A | A | A |
| es-US | Lanzador Caret | A | A | A |
| pt-BR | Caret Tela Inicial | A | A | A |
| pt-PT | Iniciador Caret | A | A | A |
| it-IT | Caret Avvio Rapido | A | A | A |
| nl-NL | Caret Startscherm | A | A | A |
| da-DK | Caret Startskærm | A | A | A |
| sv-SE | Caret Startskärm | A | A | A |
| no-NO | Caret startskjerm | A | A | A |
| fi-FI | Caret-käynnistin | A | A | A |
| pl-PL | Caret – ekran główny | A | A | A |
| cs-CZ | Spouštěč Caret | A | A | A |
| sk | Spúšťač Caret | A | A | A |

In zh-CN's follow-up, which compared the impression of quality of A "Caret Launcher", B "光标桌面" and C "Caret 桌面", the order was C > A > B.

## Findings

- In all 34 languages, the English name made the better impression of quality. Nearly every language said a half-translated or transliterated name looks machine-translated or like a cheap copy.
- Seven languages found their own name slightly more natural. All of them but zh-CN called the difference small and still recommended the English name.
- ja-JP also recommended the English name, which matches the developer's sense.
- zh-CN alone differed. In mainland China a launcher is commonly called 桌面 ("Nova 桌面", "微软桌面", and the settings' "默认桌面"), and most people do not know the word "Launcher". In the other languages, "Launcher" is a word people understand as it is.
- A transliteration reads as another word in some languages: a carat of gold or gems in ko-KR, th and hi-IN, a cart in iw-IL, and a typewriter carriage for the Russian "каретка".
- Where only "Launcher" was translated, the Nordic languages, Dutch, German and Brazilian Portuguese chose their word for "home screen". Some noted that this repeats the heading of the settings list.
- Many languages said the store description, not the name, should tell what the app does.

## Caveats

- Every answer came from the same model, not from real native speakers. Without being given any example, almost every language cited Nova Launcher. The answers may share the model's own habits, so a unanimous result weighs less than 34 independent opinions.
- Where the languages disagree, or where a language matters most, check with a real native speaker if possible.

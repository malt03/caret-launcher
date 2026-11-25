# 写真アプリの検索文字列の例（言語別）

ストアの掲載情報（詳しい説明）と、言語別のスクリーンショット（検索と検索文字列の画面）で使う、写真アプリ（Google フォト）に登録する検索文字列の例。

- 日本語は「しゃしん」と「photo」（漢字は直接打てないので読みを登録する、という例として説得力がある）
- 英語は日本語ほど良い例がなかったので「gallery」（他社の端末のギャラリーアプリの名前）と「fotos」（スペイン語）
- 各言語は、その言語の話者として答えるサブエージェントに、その言語で質問して決めた（2026-11-26）。条件は、その言語に設定した端末でのアプリ名の書き出しではないこと、実際に登録しそうで短いこと、少なくとも一つは標準のキーボードでそのまま打てること
- 検索はアクセント記号を区別しない処理をしていない（docs/spec.md 2.2）。「galería」「álbum」のように記号の付いた語は、打つときも付ける必要がある
- APP_NAME は回答者の認識で、実機では確かめていない（スクリーンショットを撮るときに確かめる）
- KEYWORDS の 1 つ目を検索画面で入力し、2 つとも検索文字列の画面に表示する想定

## en-US
KEYWORDS: gallery | fotos
APP_NAME: Photos

## ja-JP
KEYWORDS: しゃしん | photo
APP_NAME: フォト

## de-DE
KEYWORDS: Bilder | Galerie
APP_NAME: Fotos
REASON: Im Alltag sagt man in Deutschland meistens „Bilder“, nicht „Fotos“, etwa in „Schick mir mal die Bilder“. Deshalb würde man genau dieses Wort eintragen, und mit dem App-Namen „Fotos“ hat es keinen gemeinsamen Anfang. „Galerie“ heißt die Foto-App auf vielen Android-Handys, zum Beispiel bei Samsung und Xiaomi. Wer umgestiegen ist, tippt dieses Wort aus Gewohnheit. Beide Wörter lassen sich auf der deutschen Standardtastatur ohne Umlaute oder Sonderzeichen tippen.

## fr-FR
KEYWORDS: galerie | album
APP_NAME: Photos
REASON: Sur la plupart des téléphones Android vendus en France (Samsung en tête), l'appli photos d'origine s'appelle « Galerie ». C'est donc le mot qu'un francophone tape d'instinct quand il cherche ses photos. « Album » est le mot courant pour une collection de photos, et il fait partie du vocabulaire de Google Photos lui-même. Les deux mots sont courts, ils se tapent directement sur un clavier AZERTY sans accent, et aucun n'est le début de « Photos ».

## fr-CA
KEYWORDS: galerie | pics
APP_NAME: Photos
REASON: Sur un téléphone en français canadien, Google Photos s'affiche simplement sous le nom « Photos ». Les deux exemples évitent donc ce début de nom. « Galerie » est le nom que l'application de photos porte en français sur bien des téléphones, comme les Samsung, très répandus au Canada. Quelqu'un qui change d'appareil le tape donc par réflexe. « Pics » reflète l'habitude bilingue : au Québec comme ailleurs au Canada, on dit couramment « envoie-moi les pics ». Les deux mots sont courts, s'écrivent sans accent sur le clavier standard et sonnent comme ce qu'une vraie personne enregistrerait.

## es-ES
KEYWORDS: galería | carrete
APP_NAME: Fotos (en el cajón de aplicaciones aparece como «Fotos»; en Play Store, «Google Fotos»)
REASON: «Galería» es como llama a las fotos media España, porque es el nombre de la app en Samsung, Xiaomi y otros móviles; quien cambia de móvil la busca por ese nombre. «Carrete» es palabra de toda la vida en España («lo tengo en el carrete», «míralo en el carrete»), sigue viva por ser el nombre del álbum en el iPhone en español, y es justo lo que alguien teclearía sin pensarlo. Ninguna empieza como «Fotos». «Carrete» se escribe sin tildes en cualquier teclado, y «galería» también funciona tecleada como «galeria».

## es-419
KEYWORDS: galería | photos
APP_NAME: Fotos
REASON: En Latinoamérica la mayoría usa celulares Samsung, Motorola o Xiaomi, donde la app de imágenes se llama «Galería», así que mucha gente busca sus fotos con esa palabra, aunque use Google Fotos. «photos» es el nombre en inglés que se ve en la Play Store y en internet, y se escribe directo con el teclado estándar, sin acentos. Las dos son cortas y reales, y ninguna es el comienzo de «Fotos», que la búsqueda ya encuentra sola. Nota: en la práctica mucha gente escribiría «galeria» sin acento. Si la búsqueda no ignora los acentos, conviene poner esa forma en el ejemplo.

## es-US
KEYWORDS: photos | galería
APP_NAME: Fotos
REASON: En un celular configurado en español de EE. UU., la app de Google Fotos aparece como «Fotos», y muchos hispanos en EE. UU. piensan en inglés para la tecnología, así que «photos» es lo primero que escribirían. Además no empieza como «Fotos» (pho- frente a fot-), así que de verdad sirve como palabra clave aparte, y se escribe directo con el teclado estándar. «galería» es el nombre de la app de fotos en los Samsung y otros Android, muy comunes en esta comunidad, así que es la costumbre de quien cambia de celular. Mucha gente la escribiría sin tilde («galeria»). Si la búsqueda no ignora los acentos, conviene usar «galeria» en el ejemplo para que también se pueda escribir sin mantener pulsada ninguna tecla.

## it-IT
KEYWORDS: galleria | rullino
APP_NAME: Foto
REASON: Su molti telefoni Android diffusi in Italia (Samsung, Xiaomi) l'app delle foto si chiama «Galleria». Per questo moltissimi italiani dicono «apri la galleria» anche quando usano Google Foto, ed è la prima parola che digiterebbero. «Rullino» è il termine abituale per l'archivio delle foto («rullino foto» su iPhone, e si dice anche nel parlato), quindi è naturale soprattutto per chi arriva da iOS. Sono due parole italiane comuni e brevi, si digitano direttamente con la tastiera standard e nessuna delle due inizia come «Foto», quindi la ricerca normale non le troverebbe.

## pt-BR
KEYWORDS: galeria | álbum
APP_NAME: Fotos
REASON: No Brasil, "galeria" é a palavra que todo mundo usa para o lugar onde ficam as fotos do celular ("tá na galeria"), até porque a Samsung, que lidera o mercado brasileiro, chama o app dela de "Galeria". Por isso, quem troca para o Google Fotos procura instintivamente por "galeria", e ela se digita direto no teclado padrão, sem acento. "Álbum" também é um termo do dia a dia para fotos guardadas (o próprio Google Fotos tem a seção "Álbuns"). A pessoa pode digitar com acento ou sem ("album"), e os dois são críveis. Nenhuma das duas começa como "Fotos", então o exemplo mostra uma vantagem real das palavras-chave.

## pt-PT
KEYWORDS: galeria | álbum
APP_NAME: Fotos (Google Fotos)
REASON: Em Portugal, a aplicação aparece no telemóvel como "Fotos", por isso escrever "fo" já a encontra e "fotografias" não acrescentaria nada. "Galeria" é o nome da aplicação de fotos na maioria dos Android vendidos cá (Samsung, Xiaomi), e quem muda de telemóvel escreve mesmo isso por hábito. "Álbum" é a palavra do dia a dia para "o sítio onde estão as fotos". Também se escreve diretamente no teclado português, com o acento por pressão longa, ou simplesmente "album". As duas são curtas, reais e credíveis, tal como "しゃしん" é para um japonês.

## nl-NL
KEYWORDS: galerij | album
APP_NAME: Foto's
REASON: Op een Nederlands ingestelde telefoon heet de app gewoon "Foto's", dus "foto" vindt de zoekfunctie al vanzelf. Wie van een Samsung komt, kent de fotoapp als "Galerij" en typt dat uit gewoonte in. "Album" is voor Nederlanders het gewone woord voor een verzameling foto's, zoals in "fotoalbum". Beide woorden zijn kort, echt en zonder speciale tekens direct te typen op het standaard toetsenbord.

## sv-SE
KEYWORDS: bilder | galleri
APP_NAME: Foto
REASON: En svensk säger oftast "bilder" när hen menar sina foton ("kolla mina bilder"), så det är troligen det första ordet hen skriver. "Galleri" är vad fotoappen heter i Samsung och många andra Android-telefoner, så den som har bytt telefon söker gärna på det av gammal vana. Inget av orden är början av "Foto". Båda är korta, vardagliga och skrivs direkt med det vanliga svenska tangentbordet.

## da-DK
KEYWORDS: billeder | galleri
APP_NAME: Fotos
REASON: På en dansk telefon hedder appen "Fotos", så "fo…" finder den allerede. I hverdagen siger danskere dog oftest "billeder" ("send mig billederne"). Det er det første ord, mange ville skrive, når de leder efter deres fotos. "Galleri" er navnet på foto-appen på Samsung og mange andre Android-telefoner på dansk, så det er netop det ord, man har i fingrene, når man skifter telefon. Begge ord er korte og ægte danske, og de skrives direkte på det almindelige danske tastatur uden æ, ø eller å.

## no-NO
KEYWORDS: bilder | galleri
APP_NAME: Foto
REASON: «Bilder» er ordet nordmenn faktisk bruker i dagligtale («se på bildene mine»), så når appen heter «Foto», er det det mest naturlige å skrive. «Galleri» er navnet på bildeappen på Samsung-telefoner og mange andre Android-telefoner på norsk, så folk som har byttet telefon, skriver det av gammel vane. Begge ordene er korte, ekte norske ord og kan skrives direkte på det vanlige norske tastaturet, uten æ, ø eller å.

## fi-FI
KEYWORDS: galleria | photos
APP_NAME: Kuvat
REASON: Puhelimessa, jonka kieleksi on asetettu suomi, Google Kuvat näkyy nimellä "Kuvat", joten haku löytää jo sanat "kuva" ja "kuvat". Samsungin ja monen muun valmistajan kuvasovellus on suomeksi "Galleria", joten puhelinta vaihtanut suomalainen kirjoittaa juuri sen. Sana "photos" toimii kuten japanin "photo": moni suomalainen on tottunut sovellusten englanninkielisiin nimiin ja kirjoittaa ensimmäiseksi Google Photos. Kumpikin sana on lyhyt, oikeasti käytössä ja kirjoitettavissa suoraan tavallisella suomalaisella näppäimistöllä.

## pl-PL
KEYWORDS: galeria | fotki
APP_NAME: Zdjęcia
REASON: Na polskich telefonach Samsunga i Xiaomi aplikacja ze zdjęciami nazywa się „Galeria”. Dlatego wiele osób szuka jej z przyzwyczajenia właśnie pod tym słowem, tak jak w angielskim przykładzie z „gallery”. „Fotki” to bardzo popularne, potoczne określenie zdjęć („wrzuć fotki”), więc Polak naturalnie by je wpisał. Oba słowa da się wpisać na standardowej polskiej klawiaturze bez znaków diakrytycznych i żadne nie zaczyna się od „zdj”.

## cs-CZ
KEYWORDS: galerie | obrázky
APP_NAME: Fotky
REASON: Na většině telefonů s Androidem (Samsung, Xiaomi a další) se aplikace s fotkami v češtině jmenuje „Galerie“, takže Čech, který přešel z jiného telefonu, napíše instinktivně právě tohle. „Obrázky“ je běžné hovorové slovo pro fotky a obrázky v telefonu („pošli mi ty obrázky“), a přitom nezačíná jako „Fotky“, takže ho vyhledávání samo nenajde. Obě slova jdou napsat přímo na standardní české klávesnici („á“ na ní má vlastní klávesu) a jsou krátká a uvěřitelná.

## sk
KEYWORDS: galéria | obrázky
APP_NAME: Fotky
REASON: Na slovenských telefónoch Samsung a Xiaomi sa vlastná aplikácia na fotky volá „Galéria“. Kto prejde na iný telefón, z rutiny napíše práve toto slovo, a preto je to najpresvedčivejší príklad. „Obrázky“ je bežné hovorové slovo pre fotky v mobile („pozri si obrázky“) a so začiatkom názvu „Fotky“ nemá nič spoločné. Obe slová sa dajú napísať priamo na štandardnej slovenskej klávesnici. Pri „galéria“ navyše hneď vidno, že ide o slovenské slovo s diakritikou.

## hu-HU
KEYWORDS: képek | galéria
APP_NAME: Fotók
REASON: Magyarul a hétköznapi szó a „kép” vagy „képek”. Ha valaki a fotóit keresi, gyakran ezt gépeli be, nem a „fotó” szót. A „galéria” a Samsung és a Xiaomi telefonokon a képnézegető alkalmazás magyar neve, ezért aki ilyen telefonról vált, megszokásból ezt írja be. Mindkét szó a „Fotók” alkalmazásnévtől eltérő betűkkel kezdődik, és az „é” betű miatt mindkettő közvetlenül beírható a szokásos magyar billentyűzeten.

## ro
KEYWORDS: poze | galerie
APP_NAME: Foto
REASON: „Poze” este cuvântul pe care un român îl folosește zilnic, mult mai des decât „fotografii”. Tocmai de aceea este primul lucru pe care l-ar tasta cineva care își caută pozele. „Galerie” este numele aplicației de galerie pe multe telefoane setate în română (de exemplu Samsung), așa că cine vine de pe alt telefon îl caută din reflex. Niciunul nu începe cu „Foto”, iar amândouă se tastează direct, fără diacritice.

## ru-RU
KEYWORDS: галерея | foto
APP_NAME: Фото (в Google Play — «Google Фото»)
REASON: «Галерея» — самое привычное для русскоязычного пользователя название приложения с фотографиями: так оно называется на Samsung, Xiaomi и многих других телефонах, и люди по привычке ищут именно его. Это слово набирается прямо на русской раскладке и не совпадает с началом названия «Фото». «foto» — так пишут транслитом, когда клавиатура осталась на латинице и переключать её лень. Это слово тоже не является началом названия, и пользователь легко узнает в нём себя.

## uk
KEYWORDS: галерея | photos
APP_NAME: Фото
REASON: На телефоні з українською мовою застосунок називається просто «Фото», тому «фото» пошук і так знайде. Застосунок для фотографій більшість людей за звичкою називає «галереєю», бо так він зветься на Samsung, Xiaomi та інших телефонах. Слово «галерея» набирається прямо на українській розкладці. Англійську назву «Photos» багато хто знає з інтерфейсу Google, і її легко набрати, коли клавіатура вже стоїть на латиниці, а перемикати розкладку лінь.

## el-GR
KEYWORDS: συλλογή | photos
APP_NAME: Φωτογραφίες
REASON: Στα ελληνικά η εφαρμογή συλλογής στα Samsung και σε πολλά άλλα κινητά λέγεται «Συλλογή». Έτσι, όποιος έρχεται από τέτοιο κινητό θα πληκτρολογούσε αυθόρμητα αυτή τη λέξη για να βρει τις φωτογραφίες του, και γράφεται κατευθείαν στο ελληνικό πληκτρολόγιο. Το «photos» είναι το αγγλικό όνομα της εφαρμογής (Google Photos), που οι Έλληνες το βλέπουν και το λένε συχνά. Όποιος έχει ήδη το πληκτρολόγιο στα λατινικά, για παράδειγμα επειδή γράφει greeklish, θα το έγραφε χωρίς δεύτερη σκέψη. Καμία από τις δύο δεν είναι η αρχή του «Φωτογραφίες», άρα και οι δύο προσθέτουν κάτι που η αναζήτηση δεν βρίσκει ήδη. Εναλλακτική για την πρώτη λέξη θα ήταν το «εικόνες», αλλά είναι λιγότερο πειστικό από το «συλλογή».

## tr-TR
KEYWORDS: galeri | resim
APP_NAME: Fotoğraflar
REASON: Türkçe ayarlı telefonda uygulamanın adı "Fotoğraflar" olduğundan "foto" ve "fotoğraf" adın başıyla eşleşir. Bu yüzden ikisi de örnek olarak işe yaramaz. Samsung gibi telefonlardan gelen biri fotoğraflarına "Galeri" uygulamasından bakmaya alışıktır, bu yüzden elleri doğrudan "galeri" yazar. "Resim" de fotoğraf için günlük konuşmada çok kullanılır ("resimlere bak", "resim çek"). Bir Türk'ün aklına ilk gelen kelimelerden biri budur. İki kelime de kısadır ve özel harf içermediği için standart Türkçe klavyede doğrudan yazılır.

## zh-CN
KEYWORDS: zp | 图库
APP_NAME: Google 相册
REASON: 「zp」是「照片」的拼音首字母。中国用户很习惯用拼音首字母快速搜索（比如在微信里搜联系人、在手机上搜应用），不用切换输入法，也不用选字，直接就能打出来。「图库」是华为、荣耀等手机上自带相册应用的名称，从这些手机换过来的用户会下意识地输入它，作用和英语例子里的「gallery」一样。这两个词都不是「Google 相册」的开头，用户看了会觉得"我就是会这么设置"。

## zh-TW
KEYWORDS: 照片 | photo
APP_NAME: 相簿
REASON: 在台灣繁體中文的手機上，Google 相簿顯示為「相簿」，但台灣人平常講「照片」比較多，想看照片時很自然會輸入「照片」，這跟應用程式名稱的開頭不同，所以值得另外設定。「photo」用標準鍵盤的英文模式就能直接打出來，不用選字，也不必切換到注音輸入，台灣使用者常常這樣設定來快速開啟。「照片」用注音輸入時，「ㄓㄠˋㄆㄧㄢˋ」通常第一個候選就是這個詞，日常很常用，看起來真實也可信。

## zh-HK
KEYWORDS: photo | 相片
APP_NAME: 相簿
REASON: 香港人講「睇相」「send photo」時經常中英夾雜，所以「photo」最自然。用標準英文鍵盤可以直接打出嚟，唔使揀字。「相片」係 iPhone 喺香港繁體中文介面入面相簿應用程式嘅名稱，由 iPhone 轉過嚟嘅用家好可能會咁打，作用同英文例子入面嘅「gallery」一樣。兩個都唔係「相簿」嘅開頭，所以真係有加關鍵字嘅價值。

## ko-KR
KEYWORDS: 사진 | 갤러리
APP_NAME: 포토
REASON: 한국어로 설정된 휴대폰에서 Google 포토는 '포토'로 표시됩니다. 그런데 한국 사람은 평소에 사진을 '포토'가 아니라 '사진'이라고 부르기 때문에, 가장 먼저 '사진'을 입력해 볼 가능성이 큽니다. 또 한국에서는 삼성 휴대폰을 쓰는 사람이 많아서 '사진 앱 = 갤러리'라는 습관이 강합니다. 그래서 '갤러리'는 다른 휴대폰에서 넘어온 사람이 "딱 내가 등록할 만한 거네"라고 느낄 만한 예입니다. 두 검색어 모두 표준 한국어 키보드로 변환 없이 바로 입력할 수 있고, '포토'의 앞부분도 아닙니다.

## id
KEYWORDS: galeri | album
APP_NAME: Foto
REASON: Di ponsel berbahasa Indonesia, Google Foto tampil sebagai "Foto", jadi "foto" sudah ditemukan oleh pencarian biasa. Banyak orang Indonesia terbiasa membuka foto lewat aplikasi bawaan bernama "Galeri" (Samsung, Xiaomi, Oppo, Vivo), jadi "galeri" adalah kata yang paling mungkin langsung mereka ketik. "Album" juga kata sehari-hari untuk kumpulan foto, dan Google Foto sendiri memakai istilah itu. Keduanya pendek, nyata, dan bisa diketik langsung dengan keyboard standar.

## vi
KEYWORDS: hinh | thư viện
APP_NAME: Ảnh
REASON: Người Việt (nhất là ở miền Nam) hay gọi ảnh là "hình", như "chụp hình", "coi hình". Nhiều người còn gõ không dấu, nên "hinh" là cách họ thật sự sẽ gõ, và nó không trùng với phần đầu của tên "Ảnh". "Thư viện" là tên ứng dụng xem ảnh trên Samsung và nhiều điện thoại Android khác (giống "gallery" trong tiếng Anh), nên người đổi máy sẽ tự nhiên gõ từ này. Lưu ý: tôi khá chắc nhãn trên màn hình chính bản tiếng Việt là "Ảnh", nhưng chưa xác minh trên máy thật. Có thể nó hiện là "Google Photos". Trường hợp nào thì "hinh" và "thư viện" cũng không phải phần đầu của tên.

## th
KEYWORDS: อัลบั้ม | gallery
APP_NAME: Google Photos (บนหน้าจอหลักน่าจะแสดงว่า "Photos" เพราะไม่ได้แปลเป็นภาษาไทย แต่ยังไม่แน่ใจ ควรเช็กบนเครื่องจริงที่ตั้งเป็นภาษาไทยอีกครั้ง)
REASON: คนไทยเรียกที่เก็บรูปในมือถือว่า "อัลบั้ม" กันจนติดปาก เช่น "เซฟลงอัลบั้ม" หรือ "เข้าไปดูในอัลบั้ม" เห็นแล้วจึงรู้สึกว่าใช่เลย คำนี้พิมพ์ได้ทันทีด้วยแป้นภาษาไทย และไม่ใช่ส่วนต้นของชื่อแอป ส่วน "gallery" เป็นชื่อแอปแกลเลอรีที่คนใช้ Samsung หรือ Android รุ่นอื่นคุ้นกัน และพิมพ์ด้วยแป้นภาษาอังกฤษได้เลยโดยไม่ต้องสลับภาษา ผมไม่เลือก "รูป" หรือ "photo" เพราะถ้าชื่อแอปแสดงเป็น "รูปภาพ" หรือ "Photos" สองคำนี้ก็จะเป็นแค่ส่วนต้นของชื่อแอป

## hi-IN
KEYWORDS: photo | gallery
APP_NAME: फ़ोटो
REASON: भारत में ज़्यादातर लोग फ़ोन पर रोमन लिपि (हिंग्लिश) में टाइप करते हैं, और फ़ोटो ऐप ढूँढते समय वे सबसे पहले "photo" ही लिखेंगे, क्योंकि यह शब्द बोलचाल की हिंदी में पूरी तरह घुल-मिल गया है। फ़ोन हिंदी पर सेट हो तो ऐप का नाम देवनागरी में "फ़ोटो" दिखता है, इसलिए रोमन में "photo" लिखने पर नाम की शुरुआत से मिलान नहीं होता। जापानी उदाहरण में "photo" का भी यही काम है। Samsung, Xiaomi, Vivo और Oppo जैसे लोकप्रिय फ़ोनों में फ़ोटो ऐप का नाम "Gallery" होता है और लोग आम बातचीत में भी "gallery में देखो" कहते हैं, इसलिए "gallery" भी बहुत स्वाभाविक लगेगा। "तस्वीर" जैसा शुद्ध हिंदी शब्द असली ज़रूर है, पर ऐप ढूँढने के लिए शायद ही कोई उसे टाइप करता है। (ध्यान दें: "फ़ोटो" वाला ऐप नाम मैंने किसी असली फ़ोन पर देखकर पक्का नहीं किया है, इसलिए Play Store में डालने से पहले हिंदी पर सेट किसी फ़ोन में एक बार देख लें।)

## ar
KEYWORDS: استوديو | معرض
APP_NAME: صور Google
REASON: على هاتف مضبوط على العربية يظهر التطبيق باسم «صور Google»، والبحث يجده أصلًا عند كتابة «صور». لذلك لا تنفع «صور» ولا «صورة»، فكلتاهما مجرد بداية الاسم. «الاستوديو» هو اسم تطبيق المعرض على هواتف سامسونج، وهي الأكثر انتشارًا في السوق العربية، و«المعرض» هو اسمه على شاومي وغيرها. فهما المقابل العربي الطبيعي لـ«gallery»، وكثير من المستخدمين يكتبونهما بحكم العادة حين يبحثون عن صورهم. كتبتهما دون «ال» لأن المستخدم يكتب أول أحرف الكلمة نفسها، ولأن «ال» تطابق كل شيء تقريبًا، وكلتاهما تُكتب مباشرة بلوحة المفاتيح العربية دون تبديل اللغة.

## iw-IL
KEYWORDS: גלריה | photos
APP_NAME: תמונות
REASON: בישראל רוב הטלפונים הם של סמסונג, ושם אפליקציית התמונות נקראת "גלריה". לכן זו המילה הראשונה שישראלי מקליד כשהוא מחפש את התמונות שלו. אפשר להקליד אותה ישר במקלדת העברית, בלי להחליף שפה. "photos" מתאימה כי הרבה ישראלים קוראים לאפליקציה בשם האנגלי שלה, Google Photos, כמו ש-"photo" עובדת בדוגמה היפנית. אף אחת מהשתיים לא מתחילה כמו "תמונות", כך שכל אחת מוסיפה משהו שהחיפוש לא היה מוצא לבד.

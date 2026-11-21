# JakoTáta – analýza a zadání pro Claude Code

Nov 21, 2026 · @Miloš

## Shrnutí a jak dokument použít

Aplikace je proveditelná plně offline: obličej přes 3D síť obličeje a OpenGL, hlas přes DSP konverzi kalibrovanou na tátovu nahrávku; skutečný neuronový klon hlasu a obličeje je až volitelná fáze 2.

> Tento dokument je zadání pro Claude Code pro Android aplikaci **JakoTáta** (pracovní název) pro telefony a tablety. Přední (nebo zadní) kamera snímá dceru, aplikace v reálném čase zobrazuje tátův obličej a převádí její hlas na tátův, vše lze nahrát jako video.
>
> Ulož ho do nového repozitáře jako `docs/ZADANI-jakotata.md`. Fázi 2 (kap. 13) nezačínej bez splnění akceptačních kritérií fáze 1 (kap. 12) a výslovného pokynu Miloše.
>
> Platí trvalá pravidla z Deskovek: nic nemazat natvrdo, nic nevratného bez schválení, push a vydání verzí jen na výslovný pokyn.

**Výsledek analýzy v pěti bodech**

- **Obličej:** klasický face-swap přes síť 478 bodů (MediaPipe Face Landmarker) a OpenGL. Tátova textura z fotek se napíná na obličej dcery, její oči a vnitřek pusy zůstávají vidět, takže mrkání a mluvení vypadá přirozeně. Cíl je 30 fps na Pixelu a aspoň 24 fps na tabletu, model má jednotky MB.
- **Neuronový face-swap ne:** na nejsilnějších telefonech zvládne jen jednotky snímků za sekundu, modely mají stovky MB a jsou jen pro nekomerční výzkum (kap. 2).
- **Hlas:** posun výšky na tátův medián, posun formantů a vyrovnání barvy podle tátovy minutové nahrávky. Bez trénování, latence v desítkách ms. Výsledek je mužský hlas s tátovou výškou a podobnou barvou, ne dokonalý klon.
- **Hlavní zvukový režim je Papoušek.** Viktorie řekne větu a ta se přehraje tátovým hlasem z reproduktoru nebo do jejích Bluetooth sluchátek. Živý hlas bez zpoždění jen v kabelových či USB-C sluchátkách: Bluetooth přidává zpoždění a z reproduktoru by vznikla zpětná vazba. Nahrávka videa má převedený hlas vždy.
- **Stack:** nativní Kotlin + Jetpack Compose + C++ (NDK). Vydávání a samoaktualizace z GitHub Releases převzít z Deskovek.

## 1. Cíl a principy

Cílem je zábavná rodinná aplikace, ve které se dcera podívá do kamery a uvidí a uslyší sebe jako tátu – okamžitě, bez internetu a s možností nahrát video.

- **Uživatelé:** děti od 7 let (ovládání ikonami a velkými tlačítky, bez nutnosti číst) a rodič, který zakládá profil táty a spravuje nastavení.
- **Profil táty:** obličej (fotky) a hlas (nahrávka) osoby, kterou aplikace napodobuje. Datový model počítá s více profily (táta, máma, děda – max. 5), fáze 1 musí umět aspoň jeden.
- **V reálném čase:** obličej se mění v živém náhledu kamery, hlas se převádí průběžně (sluchátka, režim Papoušek, nahrávka – kap. 3).
- **Záznam:** video MP4 s obrazem po úpravě a převedeným hlasem, uložené do galerie zařízení; také fotka.
- **Plně offline.** Kamera, rozpoznání obličeje, převod hlasu i kódování videa běží v zařízení. Síť jen pro volitelnou kontrolu aktualizace. Žádné účty, reklamy, analytika ani cloud.
- **Fotky, hlas a nahrávky nikdy neopustí zařízení**, pokud je uživatel sám nesdílí (kap. 8).
- **Moderní UI se skleněnými průhlednými prvky** nad živým obrazem kamery, světlý a tmavý režim (kap. 7).
- **Jazyky:** čeština (výchozí), angličtina, španělština. Všechny texty přes `strings.xml` (`values`, `values-cs`, `values-es`), žádné natvrdo, plurály přes `plurals` (čeština 1 / 2–4 / 5+).
- **Distribuce:** APK z GitHub Releases mimo Google Play, se samoaktualizací jako Deskovky (kap. 9).

## 2. Analýza – obličej

Doporučení: režimy A a B z tabulky, oba nad stejným sledováním obličeje. Neuronové metody (C, D) na mobilu v reálném čase nestačí a jejich licence jsou problematické.

| Přístup | Jak funguje | Výkon na mobilu | Velikost, licence | Verdikt |
| --- | --- | --- | --- | --- |
| **A. Maska** – face-swap přes 3D síť | [MediaPipe Face Landmarker](https://ai.google.dev/edge/mediapipe/solutions/vision/face_landmarker) najde 478 bodů obličeje dcery, OpenGL na ně napíná tátovu texturu z fotky | cíl 24–30 fps, detekce bodů v jednotkách ms | model \~4 MB, Apache 2.0 | **Fáze 1, hlavní režim** |
| **B. Loutka** – oživený portrét | Tátova fotka přes celou obrazovku se deformuje podle mimiky a natočení hlavy dcery | stejné jako A | stejné jako A | **Fáze 1, druhý režim** |
| C. Neuronový face-swap (inswapper, FaceFusion) | Neuronová síť překleslí identitu ve výřezu 128 px | [FaceFusion Mobile](https://huggingface.co/AbrahamPJ/facefusion-mobile-models): \~75 ms na 720p snímek se Snapdragon 8 Elite a NPU, \~325 ms bez NPU | 355–604 MB; [inswapper jen pro nekomerční výzkum](https://wavespeed.ai/blog/posts/open-source-face-swap-software/) | Ne |
| D. Neuronová animace portrétu (LivePortrait, MobilePortrait) | Síť oživí tátovu fotku podle videa dcery | LivePortrait \~118 ms/snímek na 8 Elite (FaceFusion Mobile); [MobilePortrait](https://arxiv.org/abs/2407.05712v2) je výzkum s reálným časem, veřejný model neověřen | LivePortrait MIT, pomocné modely InsightFace nekomerční | Fáze 2: jen „HQ úprava“ nahrávky po záznamu |

### 2.1 Sledování obličeje

- MediaPipe Tasks Vision (`com.google.mediapipe:tasks-vision`), režim `LIVE_STREAM` (zahazuje snímky, když nestíhá), GPU delegate s CPU zálohou. Model `face_landmarker.task` přibalit do APK, nikdy nestahovat za běhu.
- Výstup: 478 bodů, 52 blendshapů (mrkání, otevření pusy…) a transformační matice hlavy.
- Vyhlazení bodů filtrem One Euro (bez cukání v klidu, bez zpoždění v pohybu).
- Max. počet obličejů: 1 (výchozí), v nastavení 2 (táta na obou dětech).
- Přední i zadní kamera: táta může mířit telefonem na dceru.

### 2.2 Profil obličeje táty

- Průvodce vyfotí 3 snímky: zepředu, \~20° vlevo, \~20° vpravo. Neutrální výraz, zavřená pusa, otevřené oči, rovnoměrné světlo.
- Automatické vyfocení, až je splněno: obličej nalezen, natočení v toleranci, ostrost a expozice v pořádku, oči otevřené (blendshapy).
- Alternativa: import jedné čelní fotky z galerie.
- Z fotek vznikne **texturový atlas 1024 × 1024** v UV prostoru kanonického modelu obličeje MediaPipe. Boky obličeje doplní boční fotky (váhy podle natočení trojúhelníku ke kameře), v MVP stačí čelní fotka a zrcadlení.
- Uložit atlas, body z fotek a referenční barvu kůže do profilu.

### 2.3 Režim A – Maska (vykreslení každého snímku)

1. Snímek kamery jako externí OES textura = pozadí.
2. Síť obličeje dcery (468 vrcholů, standardní teselace MediaPipe) s UV souřadnicemi do tátova atlasu.
3. Maska průhlednosti: měkký přechod na okraji oválu obličeje (8–15 % šířky obličeje), díry pro oči a vnitřek rtů – dceřiny skutečné oči a ústa prosvítají. Volba „Tátovy oči“ díry zavře.
4. Barevné sladení: průměr a rozptyl barvy kůže v prostoru Lab (tátův atlas ↔ obličej dcery, vyhlazené v čase) se přenesou v shaderu.
5. Přenos stínování: nízkofrekvenční jas obličeje dcery vůči jeho průměru násobí tátovu texturu, aby sedělo světlo scény.
6. Posuvník „Kolik táty“ 0–100 % (prolnutí).

Známé limity: vlasy, uši a tvar hlavy zůstávají dceřiny a tátovy rysy se přizpůsobí dětským proporcím – patří to k legraci. Při natočení hlavy nad \~35° maska plynule zeslábne.

### 2.4 Režim B – Loutka

- Na začátku si aplikace zapamatuje neutrální obličej dcery („Udělej vážný obličej“, 1 s).
- Rozdíl aktuálních bodů proti neutrálním (normalizovaný na velikost obličeje) se přičte k bodům tátovy čelní fotky, fotka se deformuje sítí.
- Natočení hlavy z transformační matice jako 2,5D natočení sítě (omezit na ±20°).
- Vnitřek pusy: obecná textura (tmavá dutina, zuby) uvnitř vnitřního obrysu rtů. Mrkání z bodů víček.

### 2.5 Další zábavné režimy (nízká cena, stejná infrastruktura)

- **Půl na půl:** levá polovina obličeje táta, pravá dcera.
- **Před a po:** rozdělená obrazovka, originál vedle úpravy.

## 3. Analýza – hlas

Doporučení: ve fázi 1 DSP konverze kalibrovaná na tátu (A). Neuronový klon (B, C) zní víc jako táta, ale potřebuje trénink na PC s GPU nebo není real-time, a pro Android neexistuje hotový runtime – proto fáze 2.

| Přístup | Příprava | Latence a výkon | Podobnost s tátou | Licence | Verdikt |
| --- | --- | --- | --- | --- | --- |
| **A. DSP s kalibrací:** posun výšky + formantů ([Signalsmith Stretch](https://github.com/Signalsmith-Audio/signalsmith-stretch)) + ekvalizér podle tátova spektra | 45–60 s nahrávky táty v aplikaci | desítky ms, 1 vlákno CPU | mužský hlas s tátovou výškou a přibližně jeho barvou | MIT | **Fáze 1** |
| B. Neuronový model natrénovaný na tátu: [Beatrice v2](https://huggingface.co/fierce-cats/beatrice-trainer), [LLVC](https://arxiv.org/abs/2311.00873v1), [RVC](https://github.com/RVC-Project/Retrieval-based-Voice-Conversion-WebUI/blob/main/docs/en/README.en.md) | 10–30 min nahrávek + trénink na PC s NVIDIA GPU | Beatrice běží jen na CPU; LLVC pod 20 ms a 2,8× rychleji než reálný čas na PC; RVC na PC s GPU \~170 ms | vysoká (skutečný tátův hlas) | MIT (trénovací kód) | Fáze 2, nutný port na Android |
| C. Zero-shot klon z krátké nahrávky: [OpenVoice V2](https://huggingface.co/myshell-ai/OpenVoiceV2/discussions/16), [MeanVC2](https://huggingface.co/edwixx/MeanVC2) | pár desítek sekund nahrávky | OpenVoice na Androidu ověřen po klipech (streamování neověřeno); MeanVC2 streamuje se 110 ms (měřeno na CPU, ne na mobilu) | střední až vysoká | OpenVoice MIT, MeanVC2 ověřit | Fáze 2 pro Papouška HQ |

### 3.1 Jak funguje DSP konverze (A)

- **Výška hlasu (F0):** tracker výšky (YIN/pYIN) průběžně měří medián dcery. Poměr = medián táty / medián dcery (typicky zhruba o oktávu dolů), aby 7letá i 14letá skončila na tátově výšce. Klouzavá adaptace, omezení −18 až 0 půltónů.
- **Formanty (délka hlasového traktu):** dětské formanty jsou výrazně výš. Odhad F1–F3 z LPC ve znělých úsecích dcery i táty, faktor posunu = poměr, omezení 0,70–1,00. Posun přes `setFormantFactor` v Signalsmith Stretch.
- **Barva (EQ):** průměrné dlouhodobé spektrum táty minus spektrum posunutého hlasu dcery → vyhlazená křivka v třetinoktvách, omezení ±12 dB, banka \~10 biquad filtrů.
- **Dynamika:** noise gate, lehký kompresor, limiter.
- **Posuvník „Kolik táty“** škáluje posun výšky i formantů (0 % = vlastní hlas).

### 3.2 Hlavní riziko: zpětná vazba z reproduktoru

Mikrofon a reproduktor najednou na jednom zařízení vytvoří smyčku: převedený hlas se vrátí do mikrofonu, posune se znovu a začne húčet. Proto tři režimy zvuku:

| Režim | Kdy | Co dcera slyší | Poznámka |
| --- | --- | --- | --- |
| **Sluchátka (live)** | připojena kabelová nebo USB-C sluchátka (`AudioDeviceCallback`) | svou řeč okamžitě tátovým hlasem | s Bluetooth sluchátky jen volba „Živě se zpožděním“ s upozorněním, výchozí zůstává Papoušek |
| **Papoušek** (výchozí, hlavní režim) | vždy | po každé větě (detekce řeči, pauza \~0,6 s) se věta přehraje tátovým hlasem | z reproduktoru i do Bluetooth sluchátek; mikrofon během přehrávání ztlumen |
| **Ticho** | vždy | nic | převedený hlas jen v nahrávce |

Ve všech režimech jde do nahrávky převedený hlas synchronně s obrazem. Experimentálně (skrytá volba): live z reproduktoru se zdrojem `VOICE_COMMUNICATION` a `AcousticEchoCanceler` – jen pokud na testovaných zařízeních nehúčí.

**Bluetooth sluchátka (Viktorie je má):** výstup přes A2DP, mikrofon vždy vestavěný v tabletu. Nikdy nepřepínat do hovorového režimu HFP/SCO – ten použije mikrofon sluchátek a zhorší zvuk na úzkopásmovou kvalitu. Papoušek ve sluchátkách funguje bez omezení, protože zpoždění tam nevadí.

### 3.3 Latence ve sluchátkách

[Zpoždění vlastního hlasu o 50–200 ms](https://en.wikipedia.org/wiki/Delayed_auditory_feedback) narušuje plynulost řeči (zakoktávání). Cíl mikrofon → sluchátka **≤ 50 ms**, přijatelné ≤ 80 ms; nad to varování a nabídnout Papouška.

- Audio přes [Oboe](https://developer.android.com/games/sdk/oboe/low-latency-audio) (AAudio), full duplex, `PerformanceMode::LowLatency`, `SharingMode::Exclusive`.
- Blok Signalsmith Stretch zmenšit pro řeč (2–3 periody tátova hlasu) a latenci změřit; pokud nestačí, přidat nízkolatenční cestu (TD-PSOLA) jen pro sluchátka a kvalitnější cestu pro nahrávku.
- V debug nabídce test round-trip latence (cvaknutí → mikrofon).

### 3.4 Profil hlasu táty

- Telepromptér s textem v jazyce profilu (pestře samohlásky), 45–60 s, tichá místnost, měřič úrovně.
- Kontroly: aspoň 20 s znělé řeči, bez přebuzení, odstup od šumu.
- Výsledek: medián a rozsah F0, formanty, dlouhodobé spektrum → `voice.json` + původní WAV.
- **Příprava na fázi 2:** volitelné další „tréninkové“ nahrávání po kapitolách (cíl 10–30 min) a export WAV pro trénink neuronového modelu.

## 4. Technologie a architektura

Stack je nativní: Kotlin + Jetpack Compose pro UI, C++ (NDK) pro zvuk a OpenGL ES 3 pro obraz. Na rozdíl od Deskovek (Flutter) je jádrem aplikace kamera, GPU a nízkolatenční audio, které by stejně musely být nativní; UI je jen jedna kamerová obrazovka a pár nastavení. Rozhodnutí a případné odchylky krátce zdůvodni v `docs/ARCHITECTURE.md`.

&#91;embedded content: zpracování obrazu a hlasu · 2 souběžné cesty, 1 profil\]

Obraz (nahoře) a hlas (dole) běží souběžně, profil táty nastaví obě cesty a spojí se až v enkodéru MP4.

| Část | Knihovna / API | Licence | Poznámka |
| --- | --- | --- | --- |
| UI | Jetpack Compose (Material 3) + [Haze](https://chrisbanes.github.io/haze/) | Apache 2.0 | skleněné rozmazání pozadí |
| Kamera | CameraX nebo Camera2 → `SurfaceTexture` | Apache 2.0 | přední i zadní kamera, zpracování v 720p |
| Sledování obličeje | MediaPipe Tasks Vision, `face_landmarker.task` v assets | Apache 2.0 | GPU delegate, CPU záloha |
| Vykreslení | OpenGL ES 3.0, vlastní EGL vlákno, sdílený kontext s enkodérem | – | shadery masky, loutky, barevného sladení |
| Zvuk I/O | [Oboe](https://developer.android.com/games/sdk/oboe/low-latency-audio) | Apache 2.0 | full duplex, low latency |
| Převod hlasu | Signalsmith Stretch (výška + formanty), vlastní YIN, LPC, EQ, VAD | MIT / vlastní | C++17, bez alokací v audio callbacku |
| Záznam | MediaCodec (H.264 + AAC 48 kHz mono) + MediaMuxer → MP4 | – | vlastní enkodér: CameraX VideoCapture nahrává mikrofon přímo, ne převedený hlas (ověřit) |
| Uložení nahrávek | MediaStore, složka `Movies/JakoTata` | – | koš přes `createTrashRequest` (API 30+) |
| Nastavení a profily | DataStore + soubory v soukromém úložišti | – | profil = složka s atlasem, JSON a WAV |

### 4.1 Synchronizace obrazu a zvuku

- Společné hodiny `System.nanoTime()`; časové značky zvuku z `oboe::AudioStream::getTimestamp`, obrazu z `SurfaceTexture.getTimestamp`.
- Převedený hlas má latenci algoritmu → při zápisu do MP4 ji odečíst, aby pusa seděla se zvukem (odchylka ≤ 40 ms).

### 4.2 Navrhovaná struktura repozitáře

```
app/                    Compose UI, navigace, téma, lokalizace
core/camera/            kamera → OES textura
core/face/              MediaPipe, vyhlazení, stavba atlasu z fotek
core/render/            GL renderer: Maska, Loutka, Půl na půl, Před a po
core/voice/             Kotlin API + JNI
native/voice/           C++: Oboe engine, YIN, LPC, Signalsmith, EQ, VAD
core/record/            enkodéry, muxer, A/V synchronizace, MediaStore
core/profile/           profily táty: uložení, export tréninkových WAV
core/update/            samoaktualizace z GitHubu (port z Deskovek do Kotlinu)
docs/                   ZADANI, ARCHITECTURE, INSTALACE, design/
```

Jestli to bude jeden Gradle modul s balíčky, nebo víc modulů, nech na sobě; vrstvy dodrž (UI nevolá MediaPipe ani JNI přímo).

## 5. Zařízení a výkonové cíle

Vývoj a testování probíhá na skutečných zařízeních: Pixel 10 Pro (hlavní) a Viktoriin tablet JENBIZZ 10″ (Android 16, 6 GB fyzické RAM, levný osmijádrový čip – přesný model zjistit přes adb, ro.soc.model). Tablet je výkonové minimum: na něm ladit fps a v případě potřeby zpracovávat obraz v 480p. Emulátor postačí na UI, ne na kameru, latenci zvuku a výkon.

- Minimální Android 8.0 (API 26), cílové API aktuální. Release APK jen `arm64-v8a` (jako Deskovky), ABI tabletu ověřit přes adb (ro.product.cpu.abilist).
- Telefon: na výšku. Tablet: na výšku i na šířku, ovládací panel na šířku po straně.
- Dotykové cíle min. 56 dp (děti).

| Metrika | Cíl | Jak měřit |
| --- | --- | --- |
| Snímky za sekundu s maskou | ≥ 30 fps Pixel, ≥ 24 fps tablet | overlay v debug buildu |
| Zpoždění obrazu (kamera → displej) | ≤ 100 ms | video s blikajícím displejem / stopky |
| Mikrofon → sluchátka | ≤ 50 ms, přijatelné ≤ 80 ms | test round-trip v debug nabídce |
| Audio vlákno | ≤ 30 % jednoho jádra | systrace / Perfetto |
| Posun zvuku vůči obrazu v MP4 | ≤ 40 ms | test tlesknutí |
| Spuštění do živého náhledu | ≤ 2 s | log |
| 10 min nepřetržitého provozu | bez poklesu pod 24 fps | ruční test |
| Velikost APK | ≤ 60 MB | release build |

**Adaptivní kvalita:** když fps klesne pod 24 na 3 s, postupně snížit rozlišení zpracování, vypnout přenos stínování a rozmazání skla (náhradou poloprůhledný tón). Video se nahrává v 720p30, na silných zařízeních volba 1080p30.

## 6. Funkce a obrazovky

Aplikace má jednu hlavní kamerovou obrazovku; vše ostatní (průvodce profilem, galerie, nastavení) je od ní jeden klik.

### 6.1 První spuštění

1. Výběr jazyka (předvolený podle systému: cs → čeština, es → španělština, jinak angličtina).
2. Dětsky srozumitelné vysvětlení oprávnění kamera a mikrofon, pak systémová žádost.
3. „Kdo bude táta?“ → průvodce profilem (dokončí rodič). Bez profilu jde aplikace použít jen s ukázkovým hlasem a bez masky.

### 6.2 Průvodce profilem táty

1. **Souhlas:** zaškrtnutí „Jsem to já, nebo mám souhlas této osoby“.
2. **Fotky:** živá kamera s oválem, 3 automatické snímky (zepředu, vlevo, vpravo) nebo import čelní fotky z galerie; náhled masky na tátovi sám.
3. **Hlas:** telepromptér 45–60 s, měřič úrovně, kontrola kvality, karta výsledku (např. „Tvůj hlas: 112 Hz“), možnost nahrát znovu.
4. **Jméno a ikona** (ořez z čelní fotky).
5. Volitelně později: tréninkové nahrávky pro fázi 2 (po kapitolách, ukazatel nahraných minut).

### 6.3 Hlavní obrazovka (kamera)

- Živý obraz přes celou obrazovku, ovládání na skleněných panelech.
- Nahoře: čip profilu („Táta“, přepnutí profilu), ikona režimu zvuku (sluchátka / papoušek / ticho), nastavení.
- Dole: velké tlačítko nahrávání uprostřed, fotka, přepnutí kamery, galerie.
- Výběr režimu obrazu: Maska / Loutka / Půl na půl / Před a po.
- Přepínače Obličej a Hlas, posuvník „Kolik táty“ (v pokročilých zvlášť pro obličej a hlas).
- Měřič hlasu jako skleněná pilulka; když není obličej, přátelská animace „Ukaž obličej“.

### 6.4 Nahrávání

- Odpočet 3-2-1, časovač, pulzující červená tečka, max. délka 2 min (nastavitelné).
- Po zastavení náhled: přehrát, sdílet (systémové sdílení, jen na pokyn uživatele), do koše. Ukládá se automaticky.
- Fotka: snímek s maskou do galerie.

### 6.5 Galerie

- Mřížka vlastních nahrávek a fotek z `Movies/JakoTata` a `Pictures/JakoTata`, přehrávání, sdílení.
- Mazání jen do koše (systémový koš na API 30+, jinak vlastní koš v aplikaci s obnovením, 30 dní).

### 6.6 Nastavení

- Jazyk, téma (systém / světlé / tmavé), kvalita videa, max. délka nahrávky, výchozí kamera a režim zvuku, počet obličejů (1–2).
- Aktualizace: „Zkontrolovat teď“, přepínač automatické kontroly, verze aplikace.
- O aplikaci a licence knihoven.
- Jen v debug buildu: overlay fps a latencí, test round-trip zvuku, zobrazení sítě bodů.

### 6.7 Rodičovská zóna

- Vstup příkladem z násobilky jako v Deskovkách (nebo PIN).
- Správa profilů (přidat, upravit, smazat do koše), vodoznak zap/vyp, povolení sdílení, export tréninkových nahrávek (ZIP s WAV do zvolené složky).

## 7. Vizuální systém – skleněné UI

Všechny ovládací prvky jsou plovoucí skleněné panely nad živým obrazem kamery. Závazný vizuální směr je návrh [JakoTáta – návrh UI](https://claude.ai/artifact/EXBXbZT8RhGCMWZa7YFjpQ): telefon (Maska, Papoušek, nahrávání), Viktoriin tablet na šířku, profil táty (fotky, hlas), galerie a nastavení, každá obrazovka ve světlém i tmavém režimu. Po schválení ho exportuj do `docs/design/` a UI stav podle něj.

- **Sklo:** rozmazání pozadí 20–30 dp (Haze), tón bílý/černý 12–20 % podle režimu, vnitřní okraj 1 dp bílý 25 %, zaoblení 24–28 dp, jemný stín a zrno. Kde rozmazání nejde nebo se šetří výkon, poloprůhledný tón.
- **Barvy:** design tokeny, akcentní přechod (např. korálová → fialová) pro tlačítko nahrávání a aktivní stavy; červená jen pro běžící záznam.
- **Písmo:** zaoblené a přátelské, Nunito / Baloo 2 (OFL) jako v Deskovkách – rodinná podoba aplikací.
- **Pohyb:** pružinové animace, tlačítko nahrávání se mění z kruhu na zaoblený čtverec, plynulý přechod mezi režimy; respektovat systémové omezení animací.
- **Čitelnost na skle:** kontrast textu WCAG AA i nad světlou i tmavou scénou (pod text tmavší scrim), ikonová tlačítka s popisky pro čtečku obrazovky, stav nikdy jen barvou.
- **Ikona aplikace:** návrh součástí mockupu (motiv masky/obličeje, bez reálné fotky).

## 8. Soukromí a ochrana proti zneužití

Obličej a hlas jsou citlivá data a video s tátovou tváří a hlasem by šlo vydávat za skutečné (např. „táta to dovolil“). Proto:

- **Jen v zařízení:** oprávnění `INTERNET` slouží jen kontrole aktualizací; žádná analytika ani odesílání pád.
- **Profily mimo zálohu Google:** `allowBackup=false` a `dataExtractionRules` vyloučí složku profilů z cloudové zálohy i přenosu na nový telefon.
- **Vodoznak** na každé nahrávce a fotce (malý, v rohu, „JakoTáta“) + metadata v MP4, že jde o upravené video. Vypnout jen v rodičovské zóně.
- **Souhlas** osoby při založení profilu (6.2).
- **Veřejný repozitář:** do Gitu nikdy necommitovat rodinné fotky ani nahrávky. Testovací data jen syntetická nebo s volnou licencí (CC0), lokální testovací soubory v `.gitignore`.
- **Žádné mazání natvrdo:** nahrávky i profily jdou do koše s možností obnovení.

## 9. Aktualizace z GitHubu a vydání

Převzat 1:1 kapitolu 4 zadání Deskovek a jejich `release.yml`, jen přepsat `UpdateService` z Dartu do Kotlinu. Aplikace musí být plně funkční bez sítě.

- Kontrola na pozadí max. 1× za 6 h, timeout 4 s, chyby tiše; nikdy neblokuje kameru.
- `GET https://api.github.com/repos/{OWNER}/{REPO}/releases/latest` → `tag_name` vs. verze aplikace → asset `jakotata-<verze>.apk` + `latest.json` (`version`, `versionCode`, `sha256`, `minSupportedVersion`, `notes_cs`, `notes_en`, `notes_es`).
- Stažení do `cache/updates/`, ověření SHA-256, instalace přes `PackageInstaller` nebo FileProvider + `REQUEST_INSTALL_PACKAGES`. Aktualizace nesmí smazat profily.
- GitHub Actions: `ci.yml` (build, lint, unit testy, testy C++ na hostu) na každý push; `release.yml` na tag `v*`: testy → podepsané APK (keystore z GitHub Secrets) → SHA-256 → `latest.json` z `CHANGELOG.md` → Release. `versionCode = major·10000 + minor·100 + patch`.
- Klíče a hesla nikdy nezadávej ani nečti – dodá je Miloš do Secrets.

## 10. Kvalita a testování

Každý commit musí projít lint a testy v CI; výkon a latence se ověřují na skutečném zařízení po každém milníku.

- **Unit testy (JVM):** porovnání verzí a `latest.json`, serializace profilů, výběr režimu zvuku podle připojeného zařízení, logika adaptivní kvality.
- **Testy C++ (GoogleTest, host build v CI):** přesnost YIN na syntetických tónech (±2 %), poměr posunu výšky na harmonickém signálu, křivka EQ, segmentace VAD, žádné alokace v callbacku.
- **Offline test celé hlasové cesty:** WAV na vstupu → WAV na výstupu s kontrolou mediánu F0 a spektra (data CC0, ne rodinné nahrávky).
- **Obraz:** test topologie sítě (indexy trojúhelníků, pokrytí UV), stavba atlasu z připravených bodů v JSON, instrumentovaný test kompilace shaderů.
- **Instrumentované testy na zařízení:** tok oprávnění, nahrávka vytvoří platné MP4 se 2 stopami, koš a obnovení.
- **Compose UI testy** hlavní obrazovky a průvodce; screenshoty telefon / tablet, světlý / tmavý.
- **Ruční protokol** (`docs/TEST-PROTOKOL.md`): 7letá a 14letá, brýle, šero, protisvětlo, natočení hlavy ±30°, dva obličeje, sluchátka kabelová / Bluetooth / žádná, 10 min provozu.

## 11. Postup a milníky (fáze 1)

Nejdřív ověřit dvě nejrizikovější věci – masku a latenci zvuku – až pak stavět UI kolem nich. Po každém milníku krátké shrnutí + screenshoty a krátké video z Pixelu.

1. **Kostra:** repozitář, Gradle, Compose, lokalizace cs/en/es, design tokeny, CI; HTML mockup ke schválení.
2. **Kamera + GL + sledování obličeje:** živý náhled, síť bodů v debug overlay, měření fps.
3. **Profil obličeje (čelní fotka) + režim Maska:** atlas, díry pro oči a pusu, prolnutí okraje, barevné sladení. *Kontrolní bod: Miloš posoudí vzhled na Viktorii.*
4. **Zvukové jádro:** Oboe duplex, YIN, Signalsmith (výška + formanty), EQ, profil hlasu, režimy Sluchátka / Papoušek / Ticho, test latence. *Kontrolní bod: latence a „tátovost“ hlasu.*
5. **Nahrávání** videa a fotek s vodoznakem, A/V synchronizace, galerie, koš.
6. **Skleněné UI podle mockupu** + hlavní obrazovka kompletně.
7. **Režimy Loutka, Půl na půl, Před a po** + boční fotky v profilu.
8. **Nastavení, rodičovská zóna, více profilů**, tréninkové nahrávky a jejich export.
9. **Aktualizace + `release.yml`:** vydat `v0.1.0`, nainstalovat, vydat `v0.1.1`, aplikace nabídne aktualizaci a profily zůstanou.
10. **Ladění na tabletu**, adaptivní kvalita, dokumentace: `ARCHITECTURE.md`, `CLAUDE.md`, `INSTALACE.md`, `TEST-PROTOKOL.md`.

## 12. Akceptační kritéria fáze 1

- [ ] V režimu letadlo funguje vše kromě kontroly aktualizace.
- [ ] Profil táty jde založit za 3 minuty (fotky + hlas) a přežije aktualizaci aplikace.
- [ ] Maska drží na obličeji 7leté i 14leté dcery při běžném pokojovém světle, mrkání a mluvení vypadají přirozeně, okraj není ostrý.
- [ ] ≥ 30 fps na Pixelu 10 Pro a ≥ 24 fps na Viktoriině tabletu, 10 min bez poklesu.
- [ ] Hlas dcery zní jako mužský hlas s výškou táty (medián F0 výstupu do ±10 % tátova), slova zůstávají srozumitelná.
- [ ] Se sluchátky latence mikrofon → ucho ≤ 50 ms na Pixelu (změřeno), bez sluchátek nikdy nevznikne zpětná vazba; Papoušek přehraje větu z reproduktoru i do Bluetooth sluchátek do 1 s po jejím domluvení.
- [ ] Nahrávka 2 min je platné MP4 v galerii s vodoznakem, posun zvuku proti obrazu ≤ 40 ms.
- [ ] Všechny 4 režimy obrazu a 3 režimy zvuku jdou přepnout za běhu bez pádu.
- [ ] Přepnutí CZ / EN / ES přeloží celé UI, nic nepřetéká na telefonu 360 dp.
- [ ] Skleněné UI odpovídá schválenému mockupu na telefonu i tabletu, v světlém i tmavém režimu.
- [ ] Tag `v*` vytvoří podepsaný release; starší verze najde novější, ověří SHA-256 a nainstaluje ji bez ztráty dat.
- [ ] V repozitáři nejsou žádné rodinné fotky ani nahrávky.

## 13. Fáze 2 – volitelné neuronové režimy

Fáze 2 je výzkumná: každý bod začíná prototypem na PC, a teprve když Miloš schválí kvalitu, následuje port do aplikace. DSP a mesh režimy z fáze 1 zůstávají jako záloha.

### 13.1 Skutečný klon tátova hlasu (live)

1. Data: 10–30 min tátových tréninkových nahrávek exportovaných z aplikace (6.7).
2. Trénink na PC s NVIDIA GPU (nebo Google Colab): první volba **Beatrice v2** (lehký, běží na CPU, trénovací kód MIT), druhá **LLVC** (latence pod 20 ms).
3. Prototyp: převeď na PC pár nahrávek Viktorie a porovnej s DSP. Pozor na velký rozdíl dítě → dospělý muž.
4. Port: export do ONNX nebo LiteRT, inference v C++ (ONNX Runtime / LiteRT, GPU nebo NPU), cíl latence ≤ 100 ms, model jako soubor v profilu (import, ne v APK).
5. Ověřit licence runtime a předtrénovaných komponent (např. obsahový enkodér) před použitím.

### 13.2 Papoušek HQ (zero-shot, ne live)

- OpenVoice V2 (MIT) převádí po celých klipech; na Androidu už existuje port na GPU. Vhodné pro režim Papoušek a pro převedení hlasu v hotové nahrávce.

### 13.3 HQ úprava nahrávky (obličej)

- Po záznamu volba „Vylepšit“: neuronová animace portrétu nebo face-swap po snímcích, pomalejší než reálný čas (minuty).
- Jen s modely s vhodnou licencí; inswapper a další modely InsightFace jsou nekomerční výzkumné – rozhodne Miloš.

## 14. Otevřené body pro Miloše

- [ ] Jazyky: CZ / EN / ES jako Deskovky, nebo víc (ThermoCamera má 6)?
- [ ] Automatická kontrola aktualizací ve výchozím stavu zapnutá (doporučeno), nebo jen ručně?
- [ ] Vodoznak ve výchozím stavu zapnutý (doporučeno).
- [ ] Další profily kromě táty (máma, děda) už ve fázi 1?
- [ ] Pro fázi 2: je k dispozici PC s NVIDIA GPU, nebo použít Colab?
- [ ] Přesný čip a ABI tabletu JENBIZZ zjistí Claude Code přes adb při prvním připojení.

**Rozhodnuto 21. 11. 2026:** název JakoTáta, repozitář `mpridal/jakotata`. Viktoriin tablet je JENBIZZ 10″ s Androidem 16 a má k němu Bluetooth sluchátka – Papoušek je hlavní režim zvuku. Grafické UI se schvaluje nad vizuálním návrhem (kap. 7).

## Zdroje

- [MediaPipe Face Landmarker – přehled](https://ai.google.dev/edge/mediapipe/solutions/vision/face_landmarker) a [návod pro Android](https://developers.google.com/edge/mediapipe/solutions/vision/face_landmarker/android)
- [FaceFusion Mobile – modely, výkon, licence](https://huggingface.co/AbrahamPJ/facefusion-mobile-models)
- [Open-source face swap 2026 – licence inswapper](https://wavespeed.ai/blog/posts/open-source-face-swap-software/)
- [MobilePortrait (arXiv)](https://arxiv.org/abs/2407.05712v2)
- [Signalsmith Stretch](https://github.com/Signalsmith-Audio/signalsmith-stretch)
- [Beatrice trainer (MIT)](https://huggingface.co/fierce-cats/beatrice-trainer)
- [LLVC – Low-latency Real-time Voice Conversion on CPU](https://arxiv.org/abs/2311.00873v1)
- [RVC WebUI](https://github.com/RVC-Project/Retrieval-based-Voice-Conversion-WebUI/blob/main/docs/en/README.en.md)
- [OpenVoice V2 na Androidu](https://huggingface.co/myshell-ai/OpenVoiceV2/discussions/16)
- [MeanVC2](https://huggingface.co/edwixx/MeanVC2)
- [StreamVC (Google, arXiv)](https://arxiv.org/abs/2401.03078)
- [Oboe – low latency audio](https://developer.android.com/games/sdk/oboe/low-latency-audio)
- [Delayed auditory feedback](https://en.wikipedia.org/wiki/Delayed_auditory_feedback)
- [Haze – glassmorphism pro Compose](https://chrisbanes.github.io/haze/)
- Zadání Deskovek: `mpridal/deskovky/docs/ZADANI-deskovky.md` (kap. 4 aktualizace, release.yml)

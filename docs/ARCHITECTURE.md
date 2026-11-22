# Architektura JakoTáty

Zadání: [ZADANI-jakotata.md](ZADANI-jakotata.md). Tento dokument zachycuje rozhodnutí a odchylky od zadání.

## Stack

Nativní Kotlin + Jetpack Compose (Material 3, Haze pro sklo), C++17 přes NDK pro zvuk, OpenGL ES 3 pro obraz.
Jádrem aplikace je kamera, GPU a nízkolatenční audio, které musí být nativní; UI je jedna kamerová
obrazovka a několik nastavení. Proto ne Flutter jako Deskovky (zadání kap. 4).

Verze nástrojů (AGP, Kotlin, Compose BOM, Gradle) jsou převzaté z ThermoCamery, aby oba projekty šly
stavět stejným prostředím (JDK 21, SDK 37, NDK 28.2, CMake 3.22.1).

## Moduly

**Rozhodnutí:** jeden Gradle modul `app` s balíčky podle vrstev. Zadání to nechává na mně (kap. 4.2).
Víc modulů by zpomalilo build a v tak malé aplikaci nic nepřináší. Pokud se vrstva rozroste nebo
bude potřeba čistě JVM testovat bez Androidu, oddělí se do vlastního modulu.

| Balíček / složka | Obsah |
| --- | --- |
| `ui/` | Compose obrazovky, téma, design tokeny, skleněné komponenty |
| `core/camera/` | kamera → OES textura (milník 2) |
| `core/face/` | MediaPipe, vyhlazení One Euro, atlas z fotek (milník 2–3) |
| `core/render/` | GL renderer: Maska, Loutka, Půl na půl, Před a po |
| `core/voice/` | Kotlin API + JNI do `native/voice` |
| `core/record/` | MediaCodec, MediaMuxer, A/V synchronizace, MediaStore |
| `core/profile/` | profily táty |
| `core/update/` | samoaktualizace z GitHub Releases |
| `native/voice/` | C++: Oboe engine, YIN, LPC, Signalsmith Stretch, EQ, VAD |

**Pravidlo vrstev:** UI nevolá MediaPipe ani JNI přímo, jen přes `core/*`.

## Nativní kód

`native/voice/CMakeLists.txt` staví dvě věci:

- `voice_core` – čisté C++ bez Androidu. V CI se staví na Linuxu a testuje GoogleTestem
  (`-DJAKOTATA_HOST_TESTS=ON`). Lokálně na Windows chybí hostitelský překladač, testy C++ tedy běží v CI.
- `jakotata_voice` – sdílená knihovna pro Android (JNI most), linkuje `voice_core`.

## ABI

Release jen `arm64-v8a` (zadání kap. 5). Debug navíc `x86_64` pro emulátor.
TODO: release.yml (milník 9) zúží ABI na `arm64-v8a`.

## Soukromí

`allowBackup=false` a `dataExtractionRules`, které vylučují vše z cloudové zálohy i přenosu
na nový telefon (zadání kap. 8). `INTERNET` jen pro kontrolu aktualizací.

## Obraz (milník 2)

- **Kamera:** CameraX, dva výstupy se stejným poměrem 16:9 – náhled 1280×720 do OES textury rendereru
  a analýza 640×360 (RGBA) pro MediaPipe. Shodný poměr stran zaručí, že body obličeje sedí na obraz.
- **Render:** vlastní EGL vlákno (`core/render/CameraRenderer`), kontext ES 3 s `EGL_RECORDABLE_ANDROID`
  pro pozdější sdílení s enkodérem videa. Výstupem je `TextureView`, ne `SurfaceView`: skleněné
  rozmazání (Haze) potřebuje obsah v hierarchii view.
- **Mapování:** `DisplayMapping` – narovnání podle `rotationDegrees` z CameraX, center crop na celou
  plochu a zrcadlení přední kamery. Stejné mapování pro obraz (shader) i body (síť).
- **Obličej:** MediaPipe Face Landmarker, LIVE_STREAM, GPU delegate s CPU zálohou, model v assets
  (nekomprimovaný). Model se načítá líně na vlákně analýzy. Vyhlazení One Euro na všech souřadnicích.
- **Otočení a zrcadlení (ověřeno na Pixelu 10 Pro):** CameraX hlásí `hasCameraTransform=true`, tj. matice
  SurfaceTexture už obsahuje otočení senzoru i zrcadlení přední kamery. Shader proto doplácí jen rozdíl
  `rotationDegrees − sensorRotationDegrees` a nezrcadlí podruhé. Snímek pro MediaPipe otáčíme sami
  (Bitmap), protože MediaPipe s rotací v `ImageProcessingOptions` vracel body se zaměněnými rozměry.
  Tablet na šířku zatím neověřen.
- **Debug:** overlay fps renderu, frekvence a doby detekce, stáří snímku; klepnutím vypne/zapne síť.

## Maska (milník 3)

- **Profil táty** (`core/profile`): čelní fotka z kamery (narovnaný snímek náhledu z GL, bez zrcadlení)
  nebo z galerie → MediaPipe v režimu IMAGE → kontrola (natočení ≤ 15°, oči otevřené, pusa zavřená)
  → čtvercový výřez 1024 × 1024 kolem obličeje + body sítě normalizované k výřezu + barva kůže v Lab.
  Uloženo v `files/profiles/default/` (`face.jpg`, `face.json`), starý profil jde do `files/trash/`.
- **Odchylka od zadání:** „atlas“ je zatím v prostoru čelní fotky, ne v UV prostoru kanonického modelu.
  Pro jednu fotku je to ekvivalentní a nepotřebuje data kanonického modelu. UV atlas přijde
  s bočními fotkami (milník 7).
- **Render** (`core/render/MaskPass`): trojúhelníky z hran teselace MediaPipe, pozice = body dcery,
  texturové souřadnice = body táty. Průhlednost ve vrcholech spočítaná jednou z tátovy fotky: 0 na
  oválu (náběh 12 % šířky obličeje), v obrysu očí a vnitřních rtů (náběh 3,5 %). Volba „Tátovy oči“
  používá variantu bez děr pro oči.
- **Barvy:** přenos průměru a rozptylu v Lab (kůže táty z fotky → kůže dcery z čela, horních tváří
  a nosu, bez vousů; vyhlazeno v čase). Přenos stínování: rozmazaný jas kamery pod maskou vůči
  průměrnému jasu kůže dcery (síla 0,55, omezení 0,75–1,25).
- **Zeslábnutí:** podle odvrácení hlavy z transformační matice – plná do 25°, nulová ve 40°.

## Hlas (milník 4)

- **C++ jádro** (`native/voice`, bez Androidu, testované GoogleTestem): YIN (F0, decimace na 12 kHz),
  LPC formanty F1–F3 (vrcholy obálky), banka 10 pásem (100 Hz–6,3 kHz) pro dlouhodobé spektrum
  a ekvalizér ±12 dB, VAD s adaptivním prahem šumu (konec věty po 0,6 s ticha), gate/kompresor/limiter.
- **Převod:** Signalsmith Stretch (MIT, `third_party/`) – posun výšky = medián táty / medián dcery
  (−18 až 0 půltónů), formanty = průměr poměrů F1–F3 (0,70–1,00), obojí škálováno „Kolik táty“.
  EQ se průběžně adaptuje: tátovo LTAS z profilu − LTAS převedeného hlasu.
  Živý režim: blok 25 ms (latence algoritmu < 50 ms); Papoušek: výchozí preset (kvalitnější).
- **Engine:** Oboe `FullDuplexStream`, float mono 48 kHz, LowLatency/Exclusive, vstup `VoicePerformance`
  (vestavěný mikrofon, nikdy SCO). Režimy: Sluchátka (živě), Papoušek (věta → převod na pracovním
  vlákně → přehrání, mikrofon během přehrávání ignorován + 250 ms doznění), Ticho.
  Start/stop streamů na vlastním vlákně (otevření trvá stovky ms, nesmí blokovat UI).
- **Režim podle výstupu** (`SoundModes`): živý hlas jen s kabelovými/USB sluchátky, jinak Papoušek.
- **Profil hlasu:** AudioRecord 48 kHz (`VOICE_RECOGNITION`), max 60 s, analýza v C++
  (medián a rozsah F0, formanty, spektrum, ořez, odstup od šumu), kontrola kvality, `voice.json` + `voice.wav`.
- **Testy C++ lokálně** (Windows bez hostitelského překladače): NDK + GoogleTest z NDK, spuštění na
  zařízení/emulátoru přes adb (`-DJAKOTATA_DEVICE_TESTS=ON -DJAKOTATA_GTEST_DIR=<NDK>/sources/third_party/googletest`).

## Nahrávání (milník 5)

- **Video:** renderer kreslí každý snímek podruhé do vstupní Surface enkodéru H.264 (720p30) přes
  vlastní EGL surface ve sdíleném kontextu; čas snímku = časová značka kamery převedená na
  CLOCK_MONOTONIC (`eglPresentationTimeANDROID`). Maska i vodoznak jsou v nahrávce, vodoznak v náhledu ne.
- **Zvuk:** v audio callbacku třetí převodník (`rec_`, malý blok) → kruhový buffer → pumpa v Kotlinu
  → AAC 48 kHz mono. Čas prvního vzorku z `AudioStream::getTimestamp(CLOCK_MONOTONIC)`, od času
  zvuku se odečítá latence převodu. Během přehrávání Papouška se do nahrávky píše ticho (bez ozvěny).
- **MP4:** `MediaMuxer` startuje, až jsou známé formáty obou stop; dřívější vzorky se kopírují do fronty.
  Ověřeno na Pixelu: 2 stopy, 30 fps, synchronizace zvuku v pořádku.
- **Galerie:** `MediaStore`, `Movies/JakoTata` a `Pictures/JakoTata`, popis „Upraveno aplikací JakoTáta“.
  Mazání do systémového koše (`createTrashRequest`, API 30+), jinak vlastní koš `files/media_trash/`.
- **Předpověď pohybu masky:** detekce trvá 25–45 ms, maska by zaostávala. Tracker ukládá čas pořízení
  snímku a rychlost bodů; renderer body posune do času kresleného snímku (max. 120 ms).

## Režimy obrazu (milník 7)

- **Půl na půl:** maska s faktorem strany podle x textury táty vůči hřbetu nosu (landmark 168), měkký přechod;
  vždy polovina obličeje, která je na obrazovce vlevo (u zrcadleného náhledu opačná strana obrázku).
- **Před a po:** dva viewporty vedle sebe, každý s vlastním center crop (obličej uprostřed v obou), vlevo
  originál, vpravo s maskou, bílá dělicí čára.
- **Loutka:** `PuppetMath` – body dcery normalizované na střed a šířku oválu, rozdíl proti neutrálu (průměr
  30 snímků po přepnutí režimu) × šířka obličeje táty se přičte k bodům táty. Fotka táty přes celou obrazovku
  (center crop), deformovaná síť s měkkým okrajem, tmavá dutina úst pod otvorem rtů, oči z fotky.
- **Zatím chybí:** 2,5D natočení Loutky podle matice hlavy a boční fotky v profilu (UV atlas kanonického modelu).

## Nastavení a rodičovská zóna (milník 8)

- **Nastavení** (`core/settings/AppSettings`, DataStore): jazyk (AppCompatDelegate, „podle systému“),
  vzhled, kvalita videa 720p/1080p, max. délka nahrávky, výchozí kamera a režim zvuku, počet obličejů 1–2,
  automatická kontrola aktualizací.
- **Rodičovská zóna:** vstup příkladem z násobilky (jednociferné × 11–18), vodoznak a sdílení zap/vyp,
  profily (max. 5, složka na profil, aktivní v nastavení, nový profil = prázdná složka → vyfocení),
  profil do koše (`files/trash/`), tréninkové nahrávky po kapitolách (max. 5 min, cíl 30 min)
  a export ZIP s WAV přes systémový výběr souboru.
- **Dva obličeje:** MediaPipe `numFaces` podle nastavení (změna přestaví stav kamery), maska na každém obličeji.

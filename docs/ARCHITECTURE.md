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

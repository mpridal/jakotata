# Instalace a vydání JakoTáty

## Instalace do telefonu nebo tabletu

1. Na zařízení otevři **github.com/mpridal/jakotata/releases** a stáhni `jakotata-<verze>.apk`.
2. Otevři stažený soubor. Android se zeptá na povolení „Instalovat neznámé aplikace“ pro prohlížeč
   (Chrome) – povol jen pro tuto instalaci.
3. Po instalaci aplikace sama hledá nové verze (max. 1× za 6 h, jde vypnout v Nastavení) a nabídne
   aktualizaci. Profily (obličej, hlas) aktualizace nesmaže.

Požadavky: Android 8.0+, procesor arm64 (64bitový ARM), přední kamera a mikrofon.

## Vývoj

- JDK 21, Android SDK (platforma 37), NDK 28.2.13676358, CMake 3.22.1.
- Kód držet mimo OneDrive (`C:\dev\jakotata`) – synchronizace se pere s Gradle a NDK.
- `./gradlew :app:assembleDebug` · `./gradlew :app:testDebugUnitTest :app:lintDebug`
- Testy C++ na zařízení (Windows nemá hostitelský překladač):

  ```
  cmake -S native/voice -B build-dev -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE=<NDK>/build/cmake/android.toolchain.cmake -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-26 -DANDROID_STL=c++_static -DCMAKE_BUILD_TYPE=Release \
    -DJAKOTATA_DEVICE_TESTS=ON -DJAKOTATA_GTEST_DIR=<NDK>/sources/third_party/googletest
  cmake --build build-dev
  adb push build-dev/voice_tests /data/local/tmp/ && adb shell /data/local/tmp/voice_tests
  ```

## Vydání verze (jen na pokyn Miloše)

Jednorázově – podpisový klíč (vytvoří a uloží Miloš, Claude klíče ani hesla nezadává ani nečte):

1. `keytool -genkeypair -v -keystore jakotata-upload.jks -alias jakotata -keyalg RSA -keysize 4096 -validity 10000`
   – soubor uložit mimo repozitář (např. `C:\dev\jakotata-upload.jks`) a zazálohovat.
   Ztráta klíče = nové verze nepůjde nainstalovat přes starou.
2. V GitHubu (Settings → Secrets and variables → Actions) přidat:
   `KEYSTORE_BASE64` (obsah .jks v base64: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("jakotata-upload.jks"))`),
   `KEYSTORE_PASSWORD`, `KEY_ALIAS` (`jakotata`), `KEY_PASSWORD`.

Každé vydání:

1. Zvýšit `appVersionName` v `app/build.gradle.kts` (versionCode se dopočítá: major·10000 + minor·100 + patch).
2. Do `CHANGELOG.md` přidat sekci `## <verze>` s poznámkami `### cs`, `### en`, `### es`
   (volitelně `Minimální verze: x.y.z` – starší verze pak musí aktualizovat).
3. Commit, push a tag `v<verze>` → workflow `release.yml` otestuje, podepíše APK (arm64),
   spočítá SHA-256, vyrobí `latest.json` a založí GitHub Release.

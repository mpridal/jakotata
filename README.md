# JakoTáta

Rodinná Android aplikace: dcera se podívá do kamery a uvidí a uslyší sebe jako tátu. Vše v reálném čase,
plně offline, s možností nahrát video.

- Zadání: [docs/ZADANI-jakotata.md](docs/ZADANI-jakotata.md)
- Architektura: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)

## Sestavení

Potřeba: JDK 21, Android SDK (platforma 37), NDK 28.2.13676358, CMake 3.22.1.

```
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
```

Testy C++ na hostu (Linux/macOS):

```
cmake -S native/voice -B build-host -DJAKOTATA_HOST_TESTS=ON
cmake --build build-host -j && ctest --test-dir build-host
```

## Soukromí

Do repozitáře nikdy nepatří rodinné fotky ani nahrávky. Testovací data jen syntetická nebo CC0.

## Licence třetích stran

Písma Nunito a Baloo 2 – SIL Open Font License (`app/src/main/assets/licenses/`).

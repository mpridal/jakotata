# JakoTáta – pokyny pro Claude Code

- Zadání: `docs/ZADANI-jakotata.md`, rozhodnutí: `docs/ARCHITECTURE.md`.
- Nic nemazat natvrdo, nic nevratného bez schválení, push a vydání verzí jen na výslovný pokyn Miloše.
- Fázi 2 (kap. 13) nezačínat bez splnění akceptačních kritérií fáze 1 a výslovného pokynu.
- Do Gitu nikdy rodinné fotky ani nahrávky; testovací data jen syntetická nebo CC0.
- Všechny texty přes `strings.xml` (`values`, `values-cs`, `values-es`), nic natvrdo.
- UI nevolá MediaPipe ani JNI přímo – jen přes `core/*`.
- Klíče a hesla nikdy nezadávat ani nečíst – dodá je Miloš do GitHub Secrets.
- Výkon, kamera a latence zvuku se ověřují na skutečném zařízení (Pixel 10 Pro, tablet JENBIZZ), ne na emulátoru.

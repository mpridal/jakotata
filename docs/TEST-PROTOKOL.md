# Ruční testovací protokol (zadání kap. 10)

Zařízení: Pixel 10 Pro (hlavní), tablet JENBIZZ 10″ (výkonové minimum). Debug build ukazuje overlay
fps, detekce, stáří snímku, výšky hlasu a tlačítko testu latence.

Stav: ✅ ověřeno · ⚠️ částečně · ⬜ neověřeno. Datum a zařízení k výsledku.

## Obličej

| Test | Pixel | Tablet |
| --- | --- | --- |
| Síť bodů sedí na obličeji, přední i zadní kamera | ✅ 21. 11. | ⬜ |
| Maska: 7letá dcera, pokojové světlo, mrkání a mluvení přirozené | ⬜ | ⬜ |
| Maska: 14letá dcera | ⬜ | ⬜ |
| Brýle | ⬜ | ⬜ |
| Šero, protisvětlo | ⬜ | ⬜ |
| Natočení hlavy ±30° (maska nad ~35° zeslábne) | ⬜ | ⬜ |
| Dva obličeje (nastavení 2) | ⬜ | ⬜ |
| Loutka: kalibrace „vážný obličej“, mimika, pusa | ⬜ | ⬜ |
| Půl na půl, Před a po | ⬜ | ⬜ |
| Maska nezaostává při pohybu hlavy (předpověď pohybu) | ✅ 22. 11. | ⬜ |

## Hlas

| Test | Pixel | Tablet |
| --- | --- | --- |
| Profil hlasu táty (45–60 s, kontrola kvality) | ✅ 21. 11. (105 Hz) | ⬜ |
| Papoušek z reproduktoru: věta se přehraje do 1 s po domluvení, bez zpětné vazby | ✅ 21. 11. | ⬜ |
| Papoušek do Bluetooth sluchátek | ⬜ | ⬜ |
| Sluchátka (kabel/USB-C): živý hlas, latence ≤ 50 ms (test latence) | ⬜ | ⬜ |
| Medián F0 výstupu do ±10 % tátova, slova srozumitelná | ⬜ | ⬜ |

## Nahrávání a galerie

| Test | Pixel | Tablet |
| --- | --- | --- |
| Video MP4 se 2 stopami, vodoznak, převedený hlas | ✅ 22. 11. | ⬜ |
| Posun zvuku vůči obrazu ≤ 40 ms (tlesknutí) | ✅ 22. 11. (subjektivně OK) | ⬜ |
| Nahrávka 2 min | ⬜ | ⬜ |
| Fotka s maskou a vodoznakem | ✅ 21. 11. | ⬜ |
| Galerie, přehrání, sdílení, koš (systémové potvrzení) | ⚠️ | ⬜ |

## Výkon a provoz

| Test | Pixel | Tablet |
| --- | --- | --- |
| ≥ 30 fps Pixel / ≥ 24 fps tablet s maskou | ✅ 30–32 fps | ⬜ |
| 10 min provozu bez poklesu pod 24 fps | ⬜ | ⬜ |
| Adaptivní kvalita při poklesu fps | ⬜ | ⬜ |
| Spuštění do živého náhledu ≤ 2 s | ⬜ | ⬜ |
| Režim letadlo: vše kromě kontroly aktualizace | ⬜ | ⬜ |

## UI, jazyky, nastavení

| Test | Pixel | Tablet |
| --- | --- | --- |
| Skleněné UI podle návrhu, světlý i tmavý režim | ⚠️ tmavý | ⬜ na šířku |
| CZ / EN / ES, nic nepřetéká na 360 dp | ⬜ | ⬜ |
| Rodičovská zóna: násobilka, vodoznak, sdílení, profily, trénink, export ZIP | ⬜ | ⬜ |
| Aktualizace v0.1.0 → v0.1.1 bez ztráty profilů | ⬜ | ⬜ |

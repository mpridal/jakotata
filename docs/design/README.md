# Návrh UI – schváleno 21. 11. 2026

Export ze schváleného návrhu „JakoTáta – návrh UI“ (Design artefakt na claude.ai). Závazný vizuální
směr pro skleněné UI (zadání kap. 7).

| Soubor | Obrazovka |
| --- | --- |
| `Main.dc.html` | Kamera – Maska, Papoušek poslouchá (telefon) |
| `Papousek.dc.html` | Papoušek přehrává tátovým hlasem |
| `Nahravani.dc.html` | Nahrávání videa |
| `Tablet.dc.html` | Viktoriin tablet na šířku |
| `ProfilFoto.dc.html` | Profil táty – fotky |
| `ProfilHlas.dc.html` | Profil táty – hlas |
| `Galerie.dc.html` | Galerie |
| `Nastaveni.dc.html` | Nastavení (světlý režim) |
| `canvas.json` | rozmístění obrazovek na plátně |

Soubory jsou podklad k převodu do Compose, samostatně se nespouštějí (potřebují runtime artefaktu).
Každá obrazovka má světlou a tmavou variantu (vlastnost `svetly` ve skriptu na konci souboru).

## Klíčové tokeny

| Token | Tmavý | Světlý |
| --- | --- | --- |
| sklo | `rgba(22,18,30,0.42)` | `rgba(255,255,255,0.72)` |
| okraj skla | `rgba(255,255,255,0.18)` | `rgba(255,255,255,0.90)` |
| text | `#FFFFFF` | `#1B1724` |
| tlumený text | `rgba(255,255,255,0.80)` | `#4A4458` |
| akcent | `#FF6B57` | `#FF6B57` |

Rozmazání 22 px se sytostí 150 %, stín `0 10px 34px rgba(0,0,0,0.22)`, pilulky 52 px, spodní panel
zaoblení 32 px, tlačítko nahrávání 84 px (vnitřek 64 px). Písma Baloo 2 (nadpisy) a Nunito (text).
V Kotlinu: `app/src/main/java/io/github/mpridal/jakotata/ui/theme/Tokens.kt`.

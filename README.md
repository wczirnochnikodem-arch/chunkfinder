# Sus Chunk Finder - samodzielny mod Fabric (bez Meteor Client)

Client-side. Wymaga tylko Fabric Loader + Fabric API.

## Build jara
**Bez instalowania czegokolwiek:** wrzuc ten folder na GitHub (repozytorium), wejdz w Actions -> build -> Artifacts -> `sus-chunk-finder-jar`.
**Lokalnie:** JDK 21 + Gradle 8.12+ -> `gradle build` -> `build/libs/sus-chunk-finder-0.1.0.jar`.
Wersje ustaw w `gradle.properties` (minecraft_version, yarn_mappings, fabric_version) zgodnie z instancja.

## Modrinth App
1. Instancja: loader **Fabric**, ta sama wersja gry co w gradle.properties.
2. Dodaj Fabric API (Modrinth App -> Add content).
3. Wrzuc jar moda (*Add content -> From file* albo do folderu `mods`).

## Uzycie w grze
- **K** - wlacz/wylacz (zmienisz w Ustawienia -> Sterowanie -> Sus Chunk Finder).
- HUD: lista najbardziej podejrzanych chunkow (score, koordynaty, odleglosc, zakres Y, pochodzenie).
- Ramki 3D: zielony / zolty / pomaranczowy / czerwony, szary = INCOMPLETE.
- Komendy: `/susfinder toggle | scan | clear | export | reload | list | options | get <opcja> | set <opcja> <wartosc>`
- Pliki: `config/sus-chunk-finder/settings.json` (opcje), `config.json` (progi blokow, wagi), `exports/` (TXT/JSON).

## Ograniczenia
- Kod nie byl kompilowany (brak Minecrafta w srodowisku) - API Yarn/Fabric zalezy od wersji.
- Ramki 3D sa glebokosciowe (widac je w linii wzroku); przez sciany pokazuje HUD i `/susfinder list`.
- "Player Placed" = nowy sledzony blok pojawil sie przy ponownym skanie tego samego chunku w tej sesji; zamiana bloku na inny solidny blok bez zmiany liczby pelnych blokow moze umknac.
- "Natural" to heurystyka po blokach-markerach struktur w tym samym chunku. Wynik to sygnal, nie wyrok.

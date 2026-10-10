# SpacerLogger

Aplikacja na Androida 17 (API 37; działa od Androida 8.0) do rejestrowania spacerów:

- trasa na mapie OpenStreetMap (bez klucza API) rysowana na żywo,
- dystans, czas, średnia prędkość,
- licznik spalonych kalorii (MET × waga × czas, MET zależny od bieżącej prędkości marszu),
- praca w tle jako usługa pierwszoplanowa (z powiadomieniem), pauza/wznowienie,
- historia spacerów (podgląd trasy na mapie), ustawienie wagi.

## Budowanie APK przez GitHub Actions

1. Wgraj całe repozytorium na GitHub (gałąź `main`).
2. Zakładka **Actions → Build SpacerLogger.apk** uruchomi się automatycznie (lub ręcznie: *Run workflow*).
3. Po zakończeniu pobierz **SpacerLogger.apk** z sekcji *Artifacts* (plik jest w ZIP-ie `SpacerLogger`).
4. Chcesz stały link? Wypchnij tag, np. `git tag v1.0.0 && git push --tags` – APK trafi do **Releases**.

APK jest podpisany kluczem debug, więc da się go od razu zainstalować (włącz „instalowanie z nieznanych źródeł”).

## Budowanie lokalnie

Potrzebne: JDK 17+, Android SDK (platforma 37), Gradle 9.4+ → `gradle assembleDebug`.

## Uwagi

- Kalorie to oszacowanie (brutto, wzór MET), nie pomiar.
- Filtr GPS odrzuca odczyty o dokładności > 25 m i skoki > 43 km/h.
- Gdy system zabije proces aplikacji, trwający spacer zostanie utracony.

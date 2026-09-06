# AJUSTE — votre laboratoire personnel, privé par défaut

[![iOS CI](https://github.com/fab72309/ajuste/actions/workflows/ios-ci.yml/badge.svg)](https://github.com/fab72309/ajuste/actions/workflows/ios-ci.yml)

AJUSTE aide à observer ses routines, tester un changement à la fois et repérer
des tendances personnelles. Les données restent sur l’appareil, sans compte ni
backend AJUSTE.

La version 1.2.4 renforce la fiabilité des associations temporelles, améliore les comparaisons entre unités différentes et rend les graphiques, légendes et fenêtres de l'application plus explicites. AJUSTE comprend aussi des expériences N-of-1, HealthKit en lecture, des widgets, les Live Activities et des sauvegardes chiffrées.

## Quickstart

1. Install Xcode 15+ (iOS target 15.0+).
2. Install [XcodeGen](https://github.com/yonaskolb/XcodeGen):  
   ```bash
   brew install xcodegen
   ```
3. Generate the Xcode project:
   ```bash
   xcodegen generate
   open BioTrack.xcodeproj
   ```
4. Build & run the **AJUSTE** app with the `BioTrack` scheme on an iPhone Simulator. Le schéma
   conserve son nom technique historique pour préserver la compatibilité.

## Fonctionnalités

- checklist quotidienne, rappels locaux et profils de routine ;
- métriques personnalisées et check-ins matin/soir ;
- lecture HealthKit facultative : sommeil, pas, poids, fréquence cardiaque au repos et HRV ;
- graphiques multi-séries accessibles et filtres par période ;
- associations Pearson/Spearman contrôlées pour la tendance et l’autocorrélation, avec intervalle de confiance et correction des comparaisons multiples ;
- protocoles, suppléments et expériences N-of-1 ;
- widget, Live Activity, export CSV/JSON et sauvegarde chiffrée.

## Targets

- iOS 15+ pour l’app principale ;
- iOS 17+ pour le widget et la Live Activity ;
- HealthKit est facultatif et utilisé en lecture uniquement.

## Android

La cible Android native se trouve dans [`android/`](android/). Elle reprend le parcours local AJUSTE avec Kotlin/Jetpack Compose et remplace HealthKit par Health Connect facultatif. Voir [`android/README.md`](android/README.md) et [`docs/google-play-android-release.md`](docs/google-play-android-release.md) pour la compilation, les limites connues et la préparation Play Store.

## Project structure

```
BioTrack-MVP/
├─ BioTrack/
│  ├─ Models/
│  ├─ Services/
│  ├─ ViewModels/
│  ├─ Views/
│  │  └─ Components/
│  └─ Resources/
│     ├─ en.lproj/
│     └─ fr.lproj/
├─ .github/workflows/ios-ci.yml
├─ project.yml
└─ README.md
```

## Validation

Le schéma technique `BioTrack` contient les tests unitaires du moteur statistique. La CI GitHub reconstruit l’app AJUSTE sur simulateur.

```bash
xcodebuild \
  -project BioTrack.xcodeproj \
  -scheme BioTrack \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  test
```

Le test de fumée statistique peut aussi s’exécuter sans simulateur en compilant [Scripts/CorrelationSmoke.swift](Scripts/CorrelationSmoke.swift) avec les modèles et services associés.

## Landing page (marketing)

This repo also includes a standalone landing + waitlist server in `landing/`.

```bash
cd landing
npm run dev
```

The landing includes SEO/AEO metadata, legal pages, waitlist form (`POST /api/leads`), double opt-in confirmation, and analytics events (`POST /api/events`).

## Confidentialité et sécurité

Les données restent **sur l’appareil**. Le fichier local utilise la protection des fichiers iOS et est exclu des sauvegardes iCloud. HealthKit nécessite un consentement explicite et n’est jamais utilisé pour la publicité. Les résultats statistiques sont exploratoires et ne constituent ni un diagnostic ni un conseil médical.

Les pages publiques destinées aux stores se trouvent dans `docs/` et sont publiées par GitHub Pages.

## License

MIT

# AJUSTE Android

La cible Android native reproduit le parcours AJUSTE iOS avec Kotlin et Jetpack Compose : checklist, check-ins matin/soir, rappels locaux, métriques, graphiques, corrélations exploratoires, protocoles, suppléments, expériences N-of-1, export/import JSON et sauvegarde chiffrée.

## Ouvrir et compiler

Ouvrir le dossier `android/` dans Android Studio, avec un JDK 17 et les SDK Android 36 installés.

```bash
./gradlew test
./gradlew assembleDebug
./gradlew bundleRelease
```

L’application utilise `com.fabienlopes.biotrack`, `versionName 1.2.4` et `versionCode 9`, alignés sur la release iOS actuelle. Le bundle Play Store attendu est `app/build/outputs/bundle/release/app-release.aab`.

## Données et permissions

- Les données principales sont conservées dans le stockage privé de l’application, dans un snapshot JSON versionné.
- Les notifications et rappels sont locaux.
- Health Connect est facultatif et en lecture seule. Android peut fournir le sommeil, les pas, le poids, la fréquence cardiaque au repos et la variabilité RMSSD ; cette dernière n’est pas strictement identique à la HRV SDNN lue sur iOS.
- Aucune permission Internet, aucun compte AJUSTE et aucun backend ne sont nécessaires.
- Le fichier d’export normal est un JSON lisible. Le fichier chiffré utilise AES-GCM avec une clé dérivée par PBKDF2-HMAC-SHA256.

## Publication

La liste des éléments à confirmer dans Play Console se trouve dans [`docs/google-play-android-release.md`](../docs/google-play-android-release.md). Une compilation locale ne vaut pas signature, upload Play Console, examen ou mise en production.

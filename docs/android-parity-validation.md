# Validation de parité Android — AJUSTE

Date du chantier : 2026-08-18. Cette checklist distingue les validations
automatisées des vérifications qui exigent un émulateur ou un appareil réel.

## Baseline

- [x] `ANDROID_HOME=/Users/fabienlopes/Library/Android/sdk ./gradlew test`
- [x] `ANDROID_HOME=/Users/fabienlopes/Library/Android/sdk ./gradlew assembleDebug`
- [x] Aucun appareil connecté via ADB au début du chantier.
- [x] Aucun AVD configuré au début du chantier.
- [x] Validation finale JVM après tous les lots : 48 tests, 0 échec.
- [x] Validation finale `assembleDebug` après tous les lots.
- [x] APK de tests instrumentés compilé avec `assembleDebugAndroidTest`.
- [ ] Validation instrumentée exécutée sur émulateur/appareil — non exécutée,
  conformément à la consigne finale de ne pas tester sur simulateur Android.

## Parcours fonctionnel

- [ ] 1. Nouvelle installation et absence de données résiduelles.
- [ ] 2. Première ouverture, onboarding et permissions facultatives.
- [ ] 3. Création d'un protocole depuis chacun des modèles du catalogue.
- [ ] 4. Création d'un supplément depuis chacun des modèles du catalogue.
- [ ] 5. Fréquence **Si besoin** absente du planning mais journalisable.
- [ ] 6. Plusieurs prises dans une journée, avec progression indépendante.
- [ ] 7. Jours spécifiques respectés sur une semaine complète simulée.
- [ ] 8. Modification puis suppression des objets et de leurs journaux.
- [ ] 9. Notification reçue lorsque l'application est en arrière-plan.
- [ ] 10. Rappels restaurés après redémarrage de l'appareil.
- [ ] 11. Sélection, suppression et réorganisation des champs de check-in.
- [ ] 12. Changement de profil et exclusions conservées après redémarrage.
- [ ] 13. Graphiques, calendrier, heatmap et filtres de statistiques.
- [ ] 14. Synchronisation Health Connect sans doublon après deux lectures.
- [ ] 15. Export puis import d'une sauvegarde créée par la version courante.
- [ ] 16. Mise à niveau à partir d'une sauvegarde Android schema v3.
- [ ] 17. Nom visible **AJUSTE**, palette bleue, sombre et contrastes.

## Rappels et changements système

- [ ] Permission `POST_NOTIFICATIONS` accordée puis refusée (Android 13+).
- [ ] Activation, désactivation, édition et suppression d'un rappel.
- [ ] Raccourcis Tous les jours, Semaine et Week-end.
- [ ] Actions Fait, Reporter 15 min, Reporter 30 min et Reporter 60 min.
- [ ] Changement manuel de l'heure système.
- [ ] Changement de fuseau horaire.
- [ ] Redémarrage complet de l'appareil.
- [ ] Aucune notification en double après chaque événement système.

## Import et migration

- [x] Snapshot Android v1 vers version courante (test JVM).
- [x] Snapshot Android v2 vers version courante (test JVM).
- [x] Snapshot Android v3 vers version courante, avec données (test JVM).
- [x] Snapshot version courante exporté puis réimporté (test JVM).
- [x] Fichier corrompu refusé avant remplacement (test JVM).
- [x] Version d'échange future/inconnue refusée explicitement (test JVM).
- [x] Export iOS essayé dans l'adaptateur Android : forme Codable détectée et
  refusée explicitement (test JVM).
- [x] Export Android côté iOS : absence d’adaptateur documentée; aucun faux
  résultat de compatibilité déclaré.

Limite documentée : l’adaptateur Android refuse explicitement la forme Codable
iOS non partagée, et iOS ne sait pas encore lire l’enveloppe Android
`ajuste.android.exchange` v1.

## Santé

- [ ] Health Connect absent.
- [ ] Health Connect présent mais permissions refusées.
- [ ] Permissions accordées puis révoquées.
- [ ] Synchronisation initiale sur la plage configurée.
- [ ] Synchronisation au retour dans l'application.
- [x] Saisie manuelle conservée à côté d'une donnée Health Connect (fixture JVM).
- [x] Deux synchronisations successives sans doublon importé (fixture JVM).
- [x] HRV affichée comme **RMSSD** sur Android, jamais comme SDNN (fixture JVM).

## Résultat final

- Émulateur/appareil utilisé : aucun, conformément à la consigne finale.
- Version Android testée : `1.2.4 (9)` au début du chantier.
- APK debug : `android/app/build/outputs/apk/debug/app-debug.apk`, 68 Mio.
- APK de tests : `android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`, 888 Kio; compilé mais non exécuté.
- Tests en échec : 0 sur 48 tests JVM. `lintDebug` : 0 erreur, 17 avertissements, 2 conseils.
- Localisation automatisée : 392 ressources FR et 392 EN, mêmes clés; les
  libellés calculés de fréquence, corrélation et recommandation utilisent la
  locale active au lieu des textes persistés en français.
- Limite de validation restante : les comportements dépendant d’un appareil
  (notifications réelles, redémarrage, Health Connect et rendu visuel) n’ont pas
  été exécutés sur cible, conformément à la consigne finale. 7,9 Gio étaient
  disponibles avant le build final.

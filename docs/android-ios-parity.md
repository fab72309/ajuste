# Matrice de parité iOS / Android — AJUSTE

Cette matrice suit le chantier de mise à niveau de la cible Android native. Elle
compare le comportement observable attendu avec les sources iOS, sans modifier
le format technique historique `com.fabienlopes.biotrack`.

## Contrats transverses

- Les données restent locales et les anciennes sauvegardes Android doivent
  rester lisibles.
- `weekly(days = [])` est la représentation persistée de **Si besoin** sur les
  deux plateformes. Elle n'est jamais planifiée automatiquement.
- Les jours de fréquence utilisent `1 = lundi … 7 = dimanche`. Les jours des
  rappels iOS utilisent historiquement la convention Calendar
  `1 = dimanche … 7 = samedi`; tout adaptateur interplateforme doit donc les
  convertir explicitement.
- HealthKit fournit la HRV SDNN sur iOS. Health Connect fournit la HRV RMSSD sur
  Android. Les deux séries ne doivent pas être fusionnées ni présentées comme
  équivalentes.
- Les catalogues intégrés sont des références en lecture seule. Ils restent
  disponibles sur une nouvelle installation sans être réinsérés au démarrage.

## État initial et critères de validation

| Lot | Référence iOS | État Android au démarrage du chantier | Écart à fermer | Preuve attendue |
| --- | --- | --- | --- | --- |
| 0 — Baseline | `BioTrack/` | Tests JVM et `assembleDebug` passent; worktree déjà modifié | Préserver les changements locaux et tracer les erreurs préexistantes | `git diff`, `./gradlew test`, `./gradlew assembleDebug` |
| 1 — Données | `Models/`, `BioTrackSnapshot.swift`, `MigrationService.swift` | Snapshot JSON v3, modèles partiels, normalisation sans vraie migration | Modèles complets, schema versionné, migration v1-v4, logs enrichis, compatibilité ancienne sauvegarde | Tests migration et round-trip |
| 2 — Catalogues | `TemplatesSheet.swift`, `SupplementLibrarySheet.swift` | Aucun catalogue intégré; un protocole de démonstration est inséré au premier lancement | 5 protocoles, 11 suppléments, recherche/filtres, modèles personnalisés, aucune duplication | Tests de contenu et déduplication |
| 3 — Protocoles | `ProtocolOnboardingView.swift`, `ProtocolEditSheet.swift`, `ProtocolLogSheet.swift` | CRUD minimal; pas de sélection de modèle, objectif/intervention, rappel ou journal détaillé | Éditeur complet, modèle personnalisé, log corrigeable/supprimable, saisie manuelle Si besoin | Tests VM + parcours Compose |
| 4 — Suppléments | `AddSupplementForm.swift`, `SupplementLogSheet.swift` | CRUD minimal; pas de fréquence complète ni de journal détaillé | Catalogue/modèle, plusieurs prises, jours, rappel, durée, notes, log corrigeable/supprimable | Tests VM + parcours Compose |
| 5 — Fréquence/planner | `FrequencyPickerSheet.swift`, `DailyPlanner.swift` | Quotidien/hebdomadaire/x-fois existent; Si besoin corrigé localement; une seule ligne par jour | Sélecteur clair, plusieurs occurrences, recalcul et persistance, tests des quatre modes | Tests unitaires planner |
| 6 — Rappels | `ManageRemindersSheet.swift`, `EditReminderSheet.swift`, schedulers iOS | Création simple et activation; pas d'édition complète, restauration reboot ni actions | CRUD complet, raccourcis jours, reprogrammation/annulation, Fait et report 15/30/60, BOOT/TIME/TIMEZONE | Tests scheduler + émulateur/appareil |
| 7 — Check-in/suivi | `DailyCheckInSheet.swift`, `CheckInMetricSelectionView.swift`, `TrackView.swift` | Check-in fixe; suivi limité à ajout/suppression et graphe simple | Sélection/ordre des métriques, date/filtres/périodes, édition/suppression, CSV | Tests sélection/VM + parcours Compose |
| 8 — Profils | `RoutineProfilesSettingsView.swift`, `DailyPlanner.swift` | Sélection du profil uniquement; les listes `disabled*Ids` sont lues par le planner | Activer/désactiver chaque élément, réinitialiser et persister | Tests VM/planner + parcours Compose |
| 9 — Statistiques | `StatsView.swift`, `UnifiedCalendarView.swift`, moteurs statistiques | Comparaison à deux séries, corrélations et N=1 de base | Graphique/calendrier/heatmap, périodes, sélection multiple, filtres routines, moyennes/tendances, CSV | Tests calculs + rendu fonctionnel |
| 10 — Santé | `HealthKitService.swift`, synchronisation `AppState.swift` | Lecture Health Connect des cinq familles, import marqué par note | Permissions/révocation, sync initiale/retour, plage configurable, source structurée, déduplication sans écraser le manuel | Tests fixtures + appareil Health Connect |
| 11 — Import/export | `ExportService.swift`, sauvegarde JSON/chiffrée | Remplacement direct du snapshot Android, sans confirmation ni format interplateforme | Validation bornée, aperçu/confirmation, format d'échange versionné, adaptateurs et limites documentées | Matrice de round-trips et snapshots fixtures |
| 12 — Interface | ressources et vues iOS | Marque AJUSTE présente; palette locale en cours; presque tous les textes Compose sont codés en dur | Chaînes localisées, cohérence bleue, états vides, sombre, contraste, tailles, accessibilité | Inspection des ressources + screenshots émulateur |
| 13 — Tests | tests iOS et checklist du brief | 3 classes de tests JVM, aucun test instrumenté | Couverture minimale de chaque lot, installation neuve, migration, validation manuelle 17 étapes | `test`, `connectedDebugAndroidTest`, `assembleDebug`, checklist signée |

## Catalogues de référence

Protocoles intégrés : Pause respiratoire calme; Marche en extérieur; Bloc de
concentration; Journal de gratitude; Préparation du coucher.

Suppléments intégrés : Vitamine D3; Vitamine B12; Vitamine C; Magnésium
glycinate; Zinc; Oméga-3; Créatine; L-théanine + caféine; Rhodiola; Bacopa;
Mélatonine.

## Contrat d’échange et limites interplateformes

- Les sauvegardes Android courantes utilisent l’enveloppe
  `ajuste.android.exchange`, version `1`, contenant un snapshot de schéma `4`.
- Les snapshots Android historiques sans enveloppe (schémas 1 à 3) restent
  acceptés, migrés puis réécrits dans le schéma courant.
- La représentation Swift synthétisée des enums à valeurs associées n’est pas
  assimilée silencieusement au modèle Android. Android la détecte et la refuse
  avec une erreur explicite tant qu’un schéma commun vérifié n’existe pas.
- iOS ne possède pas encore d’adaptateur pour l’enveloppe Android. Un export
  Android ne doit donc pas être présenté comme importable sur iOS.
- Les dates restent des timestamps Unix en millisecondes; les heures de rappel
  sont stockées séparément (`hour`, `minute`) et les jours Android suivent la
  convention ISO lundi = 1.

## Statuts utilisés

- **À faire** : absent ou insuffisant dans Android.
- **En cours** : implémenté mais pas encore validé sur tous les critères.
- **Validé JVM** : tests déterministes Android réussis.
- **Validé émulateur/appareil** : parcours manuel ou instrumenté réellement
  exécuté sur une cible Android.
- **Bloqué réel** : nécessite un appareil, une permission propriétaire ou une
  décision produit explicite.

## Résultat du chantier

| Périmètre | Statut final automatisé | Limite restante |
| --- | --- | --- |
| Lots 1 à 5 — données, catalogues, éditeurs, fréquence | Validé JVM et build | Parcours tactile complet à rejouer sur cible |
| Lots 6 à 8 — rappels, check-in, suivi, profils | Validé JVM et build | Alarmes, permissions et redémarrage à éprouver sur appareil |
| Lots 9 à 11 — statistiques, santé, échanges | Validé JVM et build; accès aux permissions Health Connect, révocation et écran de confidentialité intégrés | Health Connect réel et échange iOS non partagé à éprouver/décider |
| Lot 12 — marque et localisation | 392 ressources FR et 392 EN, ensembles identiques; libellés de fréquence, corrélations et recommandations rendus dans la langue active; lint sans erreur | Revue visuelle FR/EN, sombre, grandes polices et contraste non exécutée sur cible |
| Lot 13 — validation | 48 tests JVM verts; APK debug et APK de tests construits | Tests instrumentés et parcours sur simulateur non exécutés, conformément à la consigne finale |

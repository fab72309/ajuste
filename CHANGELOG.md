# Changelog

## 1.2.4 — prochaine version

### Expérience des fenêtres

- La fenêtre Objectifs adopte une hiérarchie plus claire : progression,
  période active et actions à réaliser sont séparées visuellement.
- Le sélecteur Aujourd'hui / 7 jours / 30 jours est plus lisible et plus
  facile à toucher, avec un état sélectionné explicite.
- Les feuilles iOS partagent désormais le même fond, le même rayon et le
  même indicateur de glissement pour une expérience cohérente dans toute
  l'application.
- Les boutons de fermeture et les états de progression restent accessibles
  avec VoiceOver et les grandes tailles de texte.
- La cible Android suit la même version et améliore la lisibilité du suivi :
  métriques défilables, axes et dates visibles, trous de saisie conservés
  dans les courbes et résumé explicite des journées comparées.
- Les corrélations Android appliquent désormais la même fenêtre de preuve que
  l’iOS : au moins 12 journées alignées et des décalages de −2 à +2 jours.
- L’onboarding iOS devient défilable et conserve les textes complets avec les
  grandes tailles de texte ; les actions et les informations légales restent
  accessibles sans troncature.
- Les confirmations de suppression, d’import et de création de modèles
  partagent désormais la même surface visuelle, avec des actions plus lisibles
  et une fermeture accessible.
- Lorsqu’une modale est ouverte, le contenu sous-jacent est masqué de la
  navigation VoiceOver pour conserver un parcours focalisé.

## 1.2.3 — prochaine version

### Associations et accessibilité

- Les détails statistiques des associations sont présentés dans une grille
  lisible, sans défilement horizontal obligatoire.
- Les coefficients Pearson, Spearman et sans tendance utilisent une notation
  décimale adaptée au français.
- Le coefficient de Pearson est décrit plus précisément par VoiceOver, avec
  son sens et son échelle de −1 à +1.

## 1.2.2 — prochaine version

### Statistiques et graphiques

- Les moyennes affichées correspondent désormais aux moyennes quotidiennes,
  comme les points de la courbe : une journée avec plusieurs saisies ne pèse
  plus plusieurs fois dans le résumé.
- Les graduations de l’axe des dates s’adaptent à la largeur de l’écran pour
  rester lisibles sur iPhone, tout en conservant les bornes de la période.
- Les barres utilisent une largeur calibrée sur le nombre réel de jours, afin
  d’éviter les colonnes trop larges ou trop serrées.
- Les jours planifiés sans réalisation apparaissent à zéro dans les graphiques
  de protocoles et de suppléments, avec une explication directement sous le
  graphique.
- Les segments pointillés restent réservés aux journées sans mesure pour les
  métriques ; ils ne suggèrent plus une absence de donnée les jours où un
  protocole hebdomadaire n’était pas planifié.

### Expérience et transparence

- Les légendes et messages d’aide expliquent plus clairement la période,
  l’agrégation quotidienne et la lecture des jours à zéro.
- Les associations restent exploratoires : elles ne constituent ni un
  diagnostic, ni une preuve de causalité, ni un conseil médical.

## 1.2.1 — prochaine version

### Fiabilité et performance

- Les sauvegardes ne recalculent plus deux fois les associations après un
  check-in.
- Les mesures enregistrées plusieurs fois le même jour sont agrégées avant
  l’analyse, afin d’éviter de surpondérer une journée.

### Expérience utilisateur

- Les fenêtres Objectifs et Hall des succès partagent une présentation plus
  lisible, avec un bouton de fermeture facile à toucher et des actions
  entièrement sélectionnables.
- La progression indique clairement lorsqu’aucun objectif n’est configuré,
  sans afficher de dénominateur artificiel.

## 1.2.0 — prochaine version

### Expérience utilisateur

- Les fenêtres Objectifs et Hall des succès adoptent désormais une même
  surface visuelle, avec une hiérarchie plus claire, des espacements réguliers
  et des boutons de fermeture accessibles.
- La fenêtre Objectifs utilise la présentation native iOS au lieu d’empiler
  une seconde popup au-dessus de la feuille système.
- Les actions sont regroupées dans des cartes lisibles et les périodes
  Aujourd’hui, 7 jours et 30 jours sont directement sélectionnables.
- Les états vides et la progression sont mieux expliqués, y compris avec les
  technologies d’assistance.

## 1.1.0 — 2026-07-31

### Statistiques et graphiques

- Les associations temporelles pénalisent désormais l’autocorrélation et
  affichent le nombre de journées réellement utiles à l’analyse.
- Un contrôle sans tendance générale écarte les associations qui ne reposent
  que sur deux séries évoluant parallèlement avec le temps.
- Les comparaisons entre unités différentes utilisent une standardisation
  robuste centrée sur la médiane, avec les valeurs réelles conservées dans les
  info-bulles.
- Les courbes distinguent les journées sans mesure, les barres positives et
  négatives partent correctement de zéro, et les légendes combinent couleur,
  symbole et style.
- Les détails d’association affichent Pearson, Spearman, la corrélation sans
  tendance, l’intervalle de confiance et la valeur ajustée pour les
  comparaisons multiples.

### Qualité

- Version et build synchronisés entre l’app et le widget : `1.1.0 (4)`.
- Onze tests unitaires couvrent le moteur statistique, dont les tendances
  trompeuses, l’autocorrélation et la standardisation robuste.
- Ajout d’un mode de capture de développement reproductible pour contrôler les
  graphiques multi-métriques en courbes et en barres.

## 1.0.0 — 2026-07-30

### Première publication publique

- Alignement de la version App Store et du binaire sur `1.0.0 (3)`.
- Préparation de la première soumission publique de BioTrack.
- Normalisation de l’icône App Store 1024 × 1024 pour garantir sa détection
  pendant la compilation et la validation Apple.
- Ajout du texte d’usage HealthKit exigé par Apple, avec mention explicite que
  BioTrack n’écrit aucune donnée dans Santé.
- Reprise des améliorations statistiques, graphiques, d’accessibilité et de
  confidentialité validées dans la version candidate 0.2.0.

## 0.2.0 — 2026-07-26

### Nouveautés

- Nouveau moteur d’associations locales : Pearson, Spearman, intervalle de confiance à 95 %, correction de Benjamini-Hochberg et sélection d’un seul décalage par paire.
- Nouvelle présentation des associations avec niveau de preuve, sens temporel et avertissement explicite sur la causalité.
- Graphiques multi-séries plus lisibles : agrégation quotidienne, formes et styles distinctifs, info-bulles contraintes à l’écran, filtres réellement appliqués et résumé VoiceOver.
- Tests unitaires et test de fumée reproductible pour le moteur statistique.

### Qualité et conformité

- Version et build synchronisés entre l’app et le widget : `0.2.0 (2)`.
- Protection iOS appliquée au fichier local et maintien de l’exclusion des sauvegardes iCloud.
- Déclaration HealthKit alignée sur l’accès réel en lecture uniquement.
- Déclaration du chiffrement exempt utilisant CryptoKit et les fonctions du système.
- Garde-fous “bien-être, non médical” ajoutés aux conditions, aux associations et à la bibliothèque de suppléments.
- Icônes App Store régénérées sans canal alpha.
- Pages de confidentialité et de support préparées pour GitHub Pages.
- Catalogue de suppléments rendu neutre : aucune dose, promesse de bénéfice ou
  recommandation de prise n’est préremplie.
- Modèles de protocoles à risque remplacés par des routines générales de
  bien-être et de productivité.
- Workflow reproductible pour générer les captures App Store iPhone 6,9 pouces.
- Métadonnées et notes App Review complètes ajoutées au dossier de publication.

### Corrections

- Les filtres de protocoles et suppléments de l’écran Statistiques agissent désormais sur les séries affichées.
- Le seuil minimal de douze jours pour les associations est maintenant appliqué
  de façon identique lors des sauvegardes et des recalculs manuels.
- Les agrégats journaliers du moteur d’associations ne sont calculés qu’une fois
  par métrique, ce qui réduit fortement le coût lorsque le nombre de métriques
  augmente.
- La période “Tout” s’appuie sur la première donnée disponible au lieu d’une date distante invalide.
- Les séries utilisent des identifiants stables et les moyennes vides n’affichent plus `0`.
- Suppression des courbes lissées susceptibles de suggérer des valeurs non mesurées.
- Ajout de la couleur `Surface` manquante et correction de la couleur de l’écran de lancement.
# AJUSTE — Unreleased

- Nouvelle identité AJUSTE : symbole, palette, icônes iOS/Android et landing.
- Le nom visible change sans modifier les identifiants d'installation, les
  groupes d'app ni les formats de sauvegarde existants.
- Nouveau positionnement : observer une routine, ajuster un détail et lire ses
  tendances avec prudence, sans diagnostic ni promesse médicale.

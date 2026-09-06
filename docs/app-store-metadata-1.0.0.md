# Métadonnées App Store — AJUSTE 1.0.0

Ce document regroupe les champs prêts à copier dans App Store Connect. La langue
principale proposée est le français.

## Identité

- Nom : `AJUSTE`
- Sous-titre : `Routines & données privées`
- Catégorie principale : `Santé et remise en forme`
- Catégorie secondaire proposée : `Style de vie`
- Classification calculée : `9+` (sujets de santé et de bien-être, sans
  diagnostic ni traitement)
- Plateforme publiée : `iPhone` (Mac Apple Silicon et Vision Pro désactivés
  faute de validation dédiée)
- Copyright : `2026 Fabien LOPES`
- Version : `1.0.0`
- Build : `3`
- Bundle ID : `com.fabienlopes.biotrack`
- SKU proposé si aucune fiche n’existe : `AJUSTE-IOS-001`

## URLs

- Marketing : `https://fab72309.github.io/ajuste/`
- Support : `https://fab72309.github.io/ajuste/support.html`
- Confidentialité : `https://fab72309.github.io/ajuste/privacy-policy.html`

Le canal de support public actuel est GitHub Issues. Avant la soumission, vérifier
si le territoire de distribution exige aussi l’affichage d’une adresse postale,
d’un e-mail ou d’un numéro de téléphone.

## Texte promotionnel

> Suivez vos routines, visualisez vos tendances et explorez des associations
> statistiques directement sur votre iPhone, sans compte ni profilage
> publicitaire.

## Description

AJUSTE vous aide à suivre vos routines et vos indicateurs de bien-être dans un
espace privé, directement sur votre iPhone.

ORGANISEZ VOTRE QUOTIDIEN

Créez vos protocoles personnels, configurez vos rappels, suivez vos habitudes et
réalisez vos check-ins du matin et du soir. Des profils de routine vous permettent
d’adapter votre organisation aux jours de semaine, aux week-ends ou aux voyages.

VISUALISEZ CE QUI ÉVOLUE

Enregistrez vos propres métriques, puis consultez des graphiques multi-séries, des
vues calendrier et des filtres par période. Les courbes, symboles et légendes sont
conçus pour rester lisibles et accessibles.

EXPLOREZ DES ASSOCIATIONS

AJUSTE compare vos séries avec plusieurs méthodes statistiques, des intervalles
de confiance et une correction des comparaisons multiples. Les résultats sont
présentés comme des hypothèses exploratoires : une association ne prouve jamais
une relation de cause à effet.

GARDEZ LE CONTRÔLE DE VOS DONNÉES

Aucun compte AJUSTE n’est nécessaire. Vos données restent sur l’appareil et ne
sont pas utilisées pour la publicité. Vous pouvez les exporter manuellement en
CSV ou JSON, ou créer une sauvegarde locale chiffrée par phrase de passe.

HEALTHKIT, SI VOUS LE SOUHAITEZ

Avec votre autorisation, AJUSTE peut lire localement le sommeil, les pas, le
poids, la fréquence cardiaque au repos et la variabilité de la fréquence
cardiaque afin de préremplir certaines métriques. Cette connexion est facultative
et AJUSTE n’écrit aucune donnée dans Santé.

AJUSTE est un outil de suivi personnel et de bien-être. Il ne constitue pas un
dispositif médical, ne fournit pas de diagnostic et ne remplace pas l’avis d’un
professionnel de santé.

## Mots-clés

`routine,sommeil,bien-être,habitudes,suivi,statistiques,confidentialité,HealthKit,journal`

## Nouveautés de la version

Les statistiques AJUSTE ont été entièrement repensées : associations plus
robustes, graphiques plus lisibles, filtres corrigés, légendes accessibles et
meilleure protection des données locales. Cette version améliore aussi la
conformité HealthKit et la stabilité générale.

## Notes pour App Review

AJUSTE ne nécessite aucun compte et ne possède aucun backend applicatif. Toutes
les données de suivi restent localement sur l’appareil.

Parcours conseillé :

1. Terminer l’onboarding en choisissant « Plus tard » pour les autorisations.
2. Ouvrir « Suivi » pour ajouter une mesure aux métriques Sommeil ou Humeur
   précréées.
3. Ouvrir « Statistiques » pour consulter les graphiques et les vues calendrier.
4. Ouvrir « Protocoles » pour consulter la routine d’exemple.
5. Ouvrir les réglages depuis l’accueil pour tester les exports et la sauvegarde
   chiffrée.

HealthKit est facultatif et utilisé en lecture uniquement. L’application demande
seulement les catégories effectivement exploitées : sommeil, pas, poids,
fréquence cardiaque au repos et HRV.

Les associations statistiques exigent au minimum douze jours alignés et sont
volontairement masquées lorsque le signal est insuffisant. Elles sont décrites
comme non causales et ne déclenchent aucune décision médicale.

Le catalogue de suppléments est un catalogue neutre destiné à créer une fiche de
suivi. Il ne fournit ni dose, ni bénéfice attendu, ni recommandation de prise.

La sauvegarde chiffrée utilise AES-GCM via CryptoKit et reste locale. Le binaire
déclare `ITSAppUsesNonExemptEncryption = NO`.

## Confidentialité proposée dans App Store Connect

- Données collectées par le développeur : `Aucune`
- Suivi publicitaire : `Non`
- Données liées à l’utilisateur sur un serveur AJUSTE : `Aucune`
- Données HealthKit : traitées uniquement sur l’appareil après consentement
- Export : uniquement à l’initiative de l’utilisateur via la feuille de partage

Ces réponses restent valides tant qu’aucun SDK tiers, compte utilisateur,
backend ou télémétrie distante n’est ajouté.

## Classement par âge — proposition

- Pas de violence, sexualité, jeu d’argent, alcool, tabac ou contenu généré
  publiquement.
- Contenu santé : suivi général du bien-être, sans traitement ni diagnostic.
- Ne pas déclarer l’application comme « Made for Kids ».
- Confirmer le classement produit par le questionnaire App Store Connect avant
  la soumission.

## Réglage de publication proposé

- Envoyer d’abord le build sur TestFlight interne.
- Vérifier HealthKit, notifications, widget et Live Activity sur un iPhone réel.
- Pour l’App Store, choisir une publication manuelle après approbation afin de
  garder la maîtrise de la date de sortie.

## Ressources graphiques

Le workflow GitHub `App Store screenshots` produit quatre captures JPEG
françaises au format iPhone 6,9 pouces, 1320 × 2868 pixels, sans transparence :

1. checklist quotidienne ;
2. saisie et historique des métriques ;
3. statistiques et graphiques ;
4. protocoles.

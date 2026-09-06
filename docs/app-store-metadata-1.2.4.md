# Métadonnées App Store Connect — AJUSTE 1.2.4

Document prêt à copier dans App Store Connect pour la build **1.2.4 (12)**.
Les réponses de confidentialité, de santé et d’export doivent être confirmées
par le titulaire du compte à partir du comportement réel de la build.

## Identité

- Nom : `AJUSTE — Labo personnel`
- Sous-titre : `Routines, tendances & N=1`
- Catégorie principale : `Santé et remise en forme`
- Catégorie secondaire : `Style de vie`
- Version : `1.2.4`
- Build : `12`
- Bundle ID : `com.fabienlopes.biotrack`
- Classification proposée : `9+`

## URLs

- Marketing : `https://fab72309.github.io/ajuste/`
- Support : `https://fab72309.github.io/ajuste/support.html`
- Confidentialité : `https://fab72309.github.io/ajuste/privacy-policy.html`

## Texte promotionnel

> Observez vos routines, testez un changement à la fois et repérez vos
> tendances — avec des données qui restent sur votre appareil.

## Nouveautés

- La fenêtre Objectifs sépare clairement la progression, la période active et
  les actions à réaliser.
- Le sélecteur Aujourd’hui / 7 jours / 30 jours est plus lisible et plus
  facile à utiliser.
- Les feuilles de l’application partagent désormais le même fond, le même
  rayon et le même indicateur de glissement.
- Les associations affichent plus clairement leurs indicateurs et leurs
  limites ; elles restent exploratoires et ne prouvent aucune causalité.
- Les confirmations et choix sensibles utilisent désormais une même interface
  cohérente, avec des boutons explicites et une fermeture accessible.

## Texte public court (YouTube / réseaux)

AJUSTE 1.2.4 améliore la fenêtre Objectifs, harmonise les fenêtres de
l’application et rend les graphiques et associations exploratoires plus
lisibles. Vos données restent privées ; AJUSTE aide à observer vos habitudes
et ne fournit ni diagnostic ni conseil médical.

## Notes pour App Review / TestFlight

AJUSTE ne nécessite aucun compte. Les données restent locales sur l’appareil
et aucun serveur AJUSTE n’est utilisé.

Parcours conseillé :

1. Terminer l’onboarding en choisissant « Plus tard » pour les autorisations.
2. Ouvrir « Suivi » et ajouter quelques mesures.
3. Ouvrir « Statistiques » pour consulter le graphique et les associations.
4. Depuis l’accueil, ouvrir « Objectifs », tester les trois périodes et marquer
   une ligne comme terminée.
5. Tester les réglages, les exports et la sauvegarde chiffrée.

HealthKit est facultatif et utilisé en lecture uniquement pour les catégories
effectivement exploitées. Les statistiques ne constituent ni un diagnostic,
ni un conseil médical.

## App Privacy — préparation à confirmer

- Suivi : `Non`
- Données collectées par le développeur : `Aucune` si aucun SDK ou backend
  distant n’a été ajouté.
- Données Santé : utilisées localement après consentement, sans envoi serveur.
- Exports : déclenchés manuellement par l’utilisateur.
- Notifications : rappels locaux uniquement.

## Export compliance

Le binaire déclare `ITSAppUsesNonExemptEncryption = NO`. L’application utilise
CryptoKit pour une sauvegarde locale chiffrée par phrase de passe ; la réponse
finale au questionnaire d’export doit être validée par le titulaire du compte.

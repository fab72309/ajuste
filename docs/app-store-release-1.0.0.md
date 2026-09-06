# AJUSTE 1.0.0 — préparation App Store

## Texte “Nouveautés” proposé

Les statistiques AJUSTE ont été entièrement repensées : associations plus robustes, graphiques plus lisibles, filtres corrigés, légendes accessibles et meilleure protection des données locales. Cette version améliore aussi la conformité HealthKit et la stabilité générale.

## URLs

- Politique de confidentialité : `https://fab72309.github.io/ajuste/privacy-policy.html`
- Support : `https://fab72309.github.io/ajuste/support.html`
- Marketing : `https://fab72309.github.io/ajuste/`

## Positionnement santé

- Catégorie : Santé et remise en forme.
- AJUSTE est un outil de suivi personnel et de bien-être.
- AJUSTE n’est pas un dispositif médical et ne diagnostique, ne traite, ne guérit ni ne prévient aucune maladie.
- Les associations statistiques ne démontrent pas de causalité.
- HealthKit est facultatif et utilisé en lecture uniquement pour le sommeil, les pas, le poids, la fréquence cardiaque au repos et la HRV.

## App Privacy proposé

- Sélection : “Aucune donnée collectée par le développeur”, sous réserve que le comportement reste sans backend ni SDK tiers.
- Les données locales et HealthKit ne quittent pas l’appareil.
- Les exports sont initiés manuellement par l’utilisateur.
- Aucun suivi publicitaire.

## Chiffrement

AJUSTE utilise CryptoKit pour une sauvegarde locale AES-GCM protégée par phrase de passe. Le projet déclare `ITSAppUsesNonExemptEncryption = NO`, car l’implémentation utilisée est fournie par le système et n’est pas une cryptographie propriétaire. La qualification réglementaire finale reste sous la responsabilité du titulaire du compte.

## Contrôles avant envoi en revue

- Vérifier les pages GitHub Pages en HTTPS.
- Exécuter les tests sur un simulateur opérationnel puis un iPhone réel.
- Vérifier HealthKit, notifications, widget et Live Activity sur appareil.
- Générer l’archive Release avec signature automatique.
- Valider l’archive avant upload.
- Renseigner captures, description, mots-clés, catégorie, âge et coordonnées App Review.
- Répondre au questionnaire export compliance avec le comportement réel.
- Envoyer d’abord sur TestFlight, puis associer le build `1.0.0 (3)` à la version App Store.

## Google Play — préparation d’une future version Android

Le dépôt ne contient actuellement aucun binaire Android. Une publication Google Play nécessitera une application Android distincte. Le positionnement et la politique sont déjà préparés : déclaration Health apps, politique publique, divulgation claire des permissions, absence de promesse médicale, avertissement professionnel de santé et fiche Data safety cohérente.

# AJUSTE Android — préparation Google Play

## État de la cible

- Application ID : `com.fabienlopes.biotrack`
- Version : `1.2.4` / `versionCode 10`
- Format de livraison : Android App Bundle (`.aab`)
- Données : stockage privé local ; aucun compte AJUSTE, backend ou permission Internet
- Santé : lecture facultative via Health Connect, avec permissions limitées aux types réellement utilisés
- Positionnement : auto-observation personnelle ; les associations sont exploratoires et ne sont pas un diagnostic

## Contrôles avant upload

- [ ] Remplacer la clé de signature de démonstration par une clé Play App Signing gérée par le propriétaire du compte.
- [ ] Générer puis conserver hors du dépôt le keystore et ses mots de passe.
- [ ] Injecter `AJUSTE_RELEASE_KEYSTORE`, `AJUSTE_RELEASE_STORE_PASSWORD`, `AJUSTE_RELEASE_KEY_ALIAS` et `AJUSTE_RELEASE_KEY_PASSWORD` uniquement dans l’environnement de build ; le script n’active la signature release que si les quatre valeurs sont présentes.
- [x] Vérifier `./gradlew test`, `./gradlew lint` et `./gradlew bundleRelease` avec le SDK 36 (6 tests sans échec, lint sans erreur bloquante, AAB générée ; signature encore absente).
- [ ] Tester l’installation et la mise à jour sur au moins un appareil Android 13+ et un Android 14+.
- [ ] Tester le refus des notifications, l’absence de Health Connect, le refus Health Connect, l’import JSON invalide et un mot de passe de sauvegarde incorrect.
- [ ] Tester les rappels avec l’application fermée et vérifier le canal de notification.
- [ ] Tester un export/import normal et chiffré sans divulguer de données personnelles.
- [ ] Contrôler le formulaire Play Data safety avec les permissions effectivement déclarées et l’absence d’envoi vers un serveur AJUSTE.
- [ ] Déclarer précisément l’usage Health Connect dans Play Console et fournir l’URL de politique de confidentialité demandée par Google.
- [ ] Préparer l’icône, les captures Android, le nom court, la description, le support et les mentions légales.
- [ ] Faire une piste de test interne puis une piste fermée avant toute publication publique.

## Validation locale réalisée le 2 août 2026

Avec le SDK Android 36 installé dans `/opt/homebrew/share/android-commandlinetools`
et les licences acceptées, la commande suivante passe :

```bash
./gradlew test lint bundleRelease -Dandroid.builder.sdkDownload=false
```

Résultat contrôlé : **6 tests unitaires, 0 échec, 0 erreur**, lint sans erreur
bloquante et bundle `app/build/outputs/bundle/release/app-release.aab` généré
(SHA-256 `269d897344f1d67a25c209f4d5afb5a6d6027351f37c8ec3cbe6914cb9fcca05`).
Lint signale seulement des versions plus récentes de la toolchain et des
dépendances disponibles ; aucune erreur bloquante ni avertissement de code
applicatif ne subsiste.
Le bundle est volontairement non signé tant que les quatre variables de
keystore ne sont pas fournies.

## Limites connues et équivalences iOS

- Health Connect remplace HealthKit. La HRV Android utilisée par la cible est RMSSD ; elle ne doit pas être présentée comme une mesure SDNN.
- Les rappels Android utilisent `AlarmManager` avec des alarmes inexactes ; la planification reste locale mais l’instant précis peut varier selon le système et les restrictions constructeur.
- Les widgets iOS et Live Activities n’ont pas d’équivalent identique livré dans cette première cible Android. Le minuteur de protocole reste visible dans l’application et les rappels passent par les notifications système.
- La cible Android ne partage pas automatiquement son snapshot local avec l’application iOS. Le transfert interplateforme devra être spécifié et testé séparément si nécessaire.

## Commandes de livraison

```bash
cd android
./gradlew clean test lint bundleRelease
```

Sans ces quatre variables, `bundleRelease` vérifie le code et produit un `.aab` non signé. Avec le keystore de livraison du propriétaire, le même appel produit le bundle signable attendu par Play App Signing ; aucun secret ne doit être ajouté au dépôt.

Ne pas confondre le fichier `.aab` généré avec une publication : l’upload, les déclarations Play Console, la revue et la mise en production sont des étapes distinctes.

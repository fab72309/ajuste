# Migration de marque BioTrack → AJUSTE

## Ce qui change

- marque, icônes, palette et textes visibles dans les apps ;
- nom des fiches App Store et Google Play ;
- landing, pages de support et politique de confidentialité ;
- nom et description du dépôt GitHub ;
- contenus de lancement et profils sociaux.

## Ce qui reste stable

- Bundle ID et Application ID `com.fabienlopes.biotrack` ;
- App Group et extension widget ;
- URL scheme `biotrack://` pour les liens déjà distribués ;
- noms de modules, packages Kotlin, schémas Xcode et formats de sauvegarde ;
- noms des variables de signature déjà configurées dans les secrets CI.

Ces éléments ne sont pas visibles comme marque. Les conserver évite de créer
une nouvelle app store, de casser une mise à jour ou de rendre les données
existantes inaccessibles.

## Checklist externe

- [ ] Renommer le dépôt GitHub en `ajuste` et mettre à jour sa description.
- [ ] Vérifier GitHub Pages après le renommage.
- [x] Réserver le nom App Store `AJUSTE — Labo personnel` et le sous-titre
  `Routines, tendances & N=1` (le nom exact `AJUSTE` est déjà utilisé).
- [x] Remplacer et ordonner les quatre captures iPhone 6,9 pouces.
- [ ] Enregistrer la description, les mots-clés et les URLs App Store après
  ajout des coordonnées App Review obligatoires.
- [ ] Importer les nouvelles captures et l'icône via une nouvelle build.
- [ ] Modifier la fiche Google Play si l'application existe déjà.
- [ ] Renommer les profils sociaux et remplacer avatar, bio et liens.
- [ ] Choisir puis acquérir un domaine avant de remplacer le domaine historique.
- [ ] Vérifier INPI/EUIPO et disponibilité juridique avant campagne payante.

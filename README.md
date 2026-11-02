# Suivi de vol GPS

Application Android qui suit un vol en avion **avec le seul GPS du téléphone** : position sur une
carte OpenStreetMap, altitude (GPS et baromètre), vitesse sol, cap, distance et durée restantes
jusqu'à l'aéroport d'arrivée, résumé de fin de vol et export GPX.

Fonctionne **en mode avion**, sans compte, sans serveur, sans télémétrie.

Ce n'est **pas** un instrument de navigation aéronautique : usage informatif uniquement.

## Installer l'APK

1. Ouvrir la page des releases : <https://github.com/ClawFabriceH92/suivi-vol-gps/releases/latest>
2. Télécharger le fichier `SuiviVol_AAAAMMJJ_vX.Y.Z.apk` sur le téléphone (Android 8.0 / API 26 minimum).
3. Autoriser l'installation depuis cette source si Android le demande, puis installer.
4. Au premier « Décoller », autoriser la **position précise** et les notifications.

Signature : si les secrets de signature ne sont pas configurés (voir plus bas), l'APK est signé avec
une clé de debug générée par le build. Dans ce cas, pour installer une version suivante, il faut
**désinstaller** la précédente (les vols enregistrés sont perdus) ou configurer une clé stable.

## Utilisation

1. **Préparation** : saisir l'aéroport de départ et d'arrivée (code IATA « CDG », OACI « LFPG » ou
   ville « Tokyo »), vérifier l'heure de décollage, puis **télécharger la carte du trajet** avec le
   wifi (corridor de 250 km, zooms 3 à 8, quelques Mo). Sans ce téléchargement, la carte est vide en vol.
2. **Décoller** : l'écran de vol s'ouvre, un service au premier plan suit le GPS écran éteint.
   Placer le téléphone côté hublot ; l'accrochage GPS prend quelques minutes. Le mode avion peut
   rester activé, seule la localisation doit être en marche.
3. **Terminer le vol** : résumé (durée réelle, distance parcourue, altitude et vitesse maximales,
   écart à la route) et **export GPX** par le partage Android.

Réglages (engrenage) : vitesse de croisière de référence, unités (m/ft, km/h/kt), fréquence GPS
(1 s / 5 s / 15 s), écran allumé, thème, vidage du cache de carte, vérification de mise à jour.

## Structure du projet

| Dossier | Contenu |
|---|---|
| `core/` | Module Kotlin pur, sans Android : haversine, cap, altitude barométrique ICAO et recalage, lissage de vitesse, durée restante, progression, analyse de trace, GPX, recherche d'aéroports, tuiles du corridor. Tests JUnit. |
| `app/` | Application Android (Kotlin, Jetpack Compose, Material 3, Room, DataStore, osmdroid). |
| `app/src/main/assets/aeroports.json` | Base d'aéroports embarquée (OurAirports, domaine public), générée par `scripts/generer_aeroports.py`. |
| `docs/SPEC.md` | Cahier des charges. |
| `.github/workflows/android.yml` | Tests, APK et release GitHub. |

### Base d'aéroports

```bash
curl -L -o /tmp/airports.csv https://davidmegginson.github.io/ourairports-data/airports.csv
```

```bash
python3 scripts/generer_aeroports.py /tmp/airports.csv
```

Filtre : `scheduled_service = yes` et type `large_airport` ou `medium_airport`. Avec le jeu de
données du 2 novembre 2026, cela donne 3 268 aéroports (273 Ko de JSON).

## Compiler

Prérequis : JDK 17, Android SDK (API 35). Le wrapper Gradle télécharge Gradle 8.11.1.

```bash
./gradlew :core:test
```

```bash
./gradlew :app:assembleRelease
```

L'APK est dans `app/build/outputs/apk/release/`. Sur une machine sans SDK Android, seuls les tests
du module `core` sont possibles :

```bash
SUIVIVOL_CORE_SEUL=1 ./gradlew :core:test
```

## Release et mise à jour

Le workflow GitHub Actions `Android` :

- à chaque push : tests `core`, APK release, artefact de build ;
- lancé manuellement (**Run workflow**) : en plus, création ou mise à jour de la release
  `v<version>` avec l'APK `SuiviVol_AAAAMMJJ_vX.Y.Z.apk`, son empreinte SHA-256 et un `version.json`.

La version se change dans `app/build.gradle.kts` (`versionCodeApp`, `versionNameApp`).

Mise à jour depuis l'application : Réglages → « Vérifier les mises à jour » lit
`https://github.com/ClawFabriceH92/suivi-vol-gps/releases/latest/download/version.json` et propose
le téléchargement de l'APK si `versionCode` est plus grand. La vérification n'est jamais automatique.

**Dépôt privé** : tant que le dépôt est privé, cette URL répond 404 sans session GitHub. La
vérification depuis l'application affiche alors « Réponse HTTP 404 » et le lien de téléchargement
ne fonctionne que dans un navigateur connecté à GitHub. Rendre le dépôt public suffit à activer le
mécanisme ; sinon il faudra un jeton d'accès (non prévu en v1).

### Clé de signature stable (recommandé)

Créer une clé une fois pour toutes :

```bash
keytool -genkeypair -v -keystore suivivol.keystore -alias suivivol -keyalg RSA -keysize 2048 -validity 10000
```

```bash
base64 -w0 suivivol.keystore
```

Puis définir dans le dépôt GitHub (Settings → Secrets and variables → Actions) :

| Secret | Contenu |
|---|---|
| `SUIVIVOL_KEYSTORE_BASE64` | le keystore encodé en base64 |
| `SUIVIVOL_KEYSTORE_PASSWORD` | mot de passe du keystore |
| `SUIVIVOL_KEY_ALIAS` | `suivivol` |
| `SUIVIVOL_KEY_PASSWORD` | mot de passe de la clé |

Les fichiers `*.keystore` sont ignorés par git : ne jamais les committer.

## Écarts par rapport au cahier des charges

- **Distance parcourue** (§7.7) : le seuil fixe de 2 km entre deux points écarterait toute la
  croisière en mode 15 s (900 km/h × 15 s = 3,75 km). Le critère retenu est une vitesse impliquée
  supérieure à 1 500 km/h (saut impossible) ou un trou de plus de 10 minutes.
- **Nombre d'aéroports** (§6) : 3 268 avec le filtre demandé, et non « environ 4 000 ».
- **Permission INTERNET** (§8) : utilisée pour les tuiles de carte et, en plus, pour la vérification
  manuelle de mise à jour. Aucune autre connexion.
- **Politique OSM** : le téléchargement du corridor est limité aux zooms 3 à 8 et déclenché par
  l'utilisateur ; la politique de la fondation OSM déconseille le préchargement, il reste modeste
  (quelques centaines de tuiles par trajet).
- **Minification** désactivée en v1 (APK plus gros, mais pas de risque R8 sur osmdroid).

## Licence

À définir avant la première release publique.

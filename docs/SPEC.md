# Suivi de vol GPS : cahier des charges

Application Android qui suit un vol en avion en temps réel à partir des seules données GPS du
téléphone : position sur une carte OpenStreetMap, altitude, vitesse, distance et durée restante
jusqu'à l'aéroport d'arrivée.

Version du document : **v1, 2 novembre 2026**
Document destiné à la publication : il ne contient aucune donnée personnelle, aucun identifiant
technique et aucune adresse d'infrastructure.

---

## 1. Décisions de cadrage (validées le 2 novembre 2026)

| Décision | Choix retenu | Conséquence technique |
|---|---|---|
| Plateforme | **Android natif** (Kotlin + Jetpack Compose) | Accès direct au GNSS et aux capteurs, fonctionnement en mode avion. Pas de PWA : le GPS navigateur est peu fiable et la carte se recharge en continu. |
| Source des données de vol | **GPS seul** pour la v1. Aucune API de suivi de vol, aucun compte, aucun réseau. | Toutes les valeurs (position, altitude, vitesse, cap) viennent du GNSS ; distance et durée restante sont calculées par l'app. L'app doit être pleinement utilisable en mode avion. |
| Aéroports de départ et d'arrivée | **Saisis à la main** en v1, dans une base d'aéroports embarquée dans l'APK. | Une base locale (code IATA/OACI, nom, pays, latitude, longitude) est livrée dans les assets : recherche instantanée, hors ligne. |
| Évolution prévue | **Les deux** : saisie manuelle *et* récupération automatique du vol (API) dans une version ultérieure. | L'écran de préparation est écrit autour d'une interface `SourceVol` (manuelle aujourd'hui, API demain) pour que l'ajout ne casse pas le reste. |
| Réseau à bord | **Aucune dépendance au réseau en vol.** | Les tuiles de carte nécessaires sont téléchargées **avant** le décollage (wifi de l'aéroport ou de l'avion), puis lues depuis le cache. |
| Périmètre | **Application séparée de SpaceSkySea Radar** (décision du 3 novembre 2026). | Aucune fusion : le radar suit le trafic environnant avec le réseau, cette app suit l'appareil du passager sans réseau. Pas de base d'aéroports ni de cache de tuiles dans l'app radar, et pas de dépendance à OpenSky/AISstream ici. |

### Point d'interprétation à confirmer

« Que le GPS pour le point 2 » signifie : **aucune donnée de vol extérieure**, tout vient du GPS.
La saisie manuelle des deux aéroports reste nécessaire en v1, sinon il n'y a rien à comparer pour
calculer une distance et une durée restante. Si l'intention était plutôt « pas de saisie du tout,
l'app devine le vol », c'est la version 2 (API), et il faut alors du réseau à bord.

### Devinette du vol (décision du 3 novembre 2026)

La saisie manuelle des aéroports de départ et d'arrivée **reste toujours possible** : c'est la
voie de référence, celle qui marche sans rien attendre. La devinette ne fait que **pré-remplir**
les deux champs, et un appui suffit à corriger.

| Élément | Décision |
|---|---|
| Base d'aéroports | Embarquée dans l'APK, issue du jeu public **OurAirports** (domaine public, 86 335 aéroports, 1 789 en France). On n'embarque que les aéroports à service régulier. |
| Détection du décollage | Vitesse sol > 150 km/h et altitude > 1 000 m tenues une minute. |
| Aéroport de départ deviné | Dernier aéroport de la base à moins de 15 km de la position au sol avant le décollage. |
| Destination devinée | Grand cercle dans l'axe de l'avion : on note chaque aéroport par son écart de cap (< 15°) et le détour par rapport à la distance directe ; dès que la vitesse verticale passe sous -3 m/s, l'aéroport le plus proche du point d'impact projeté prend le dessus. |
| Confiance | Affichée et recalculée à chaque point GPS ; la liste des candidats se resserre au fil du vol. |
| Correction | Toujours possible : les deux champs restent éditables, la devinette ne verrouille rien. |
| Ce qui n'est pas devinable hors ligne | Numéro de vol et horaire théorique. Prévu en option via OpenSky **avant** le décollage, jamais en dépendance en vol. |

---

## 2. Objectif

Pendant un vol, savoir à tout moment :

- **où je suis** sur une carte, entre l'aéroport de départ et celui d'arrivée ;
- **l'altitude** actuelle ;
- **la vitesse** (vitesse sol) ;
- **la distance restante** jusqu'à l'arrivée ;
- **la durée restante** estimée et l'heure d'arrivée prévue ;
- la **progression** du trajet, en pourcentage et sous forme de barre.

Après le vol : un récapitulatif et une trace GPX exportable (utile pour la géolocalisation des
photos, un litige bagage ou un simple souvenir).

### Ce que l'app ne fait pas

- Ce n'est **pas** un instrument de navigation aéronautique : usage informatif uniquement, mention
  affichée à l'écran de préparation et dans l'à-propos.
- Elle ne remplace ni Flightradar ni les écrans de cabine : pas de données officielles de vol, pas
  d'heure de décollage réelle fournie par la compagnie.
- Elle ne transmet rien : aucune donnée ne quitte le téléphone (pas de compte, pas de télémétrie).

---

## 3. Utilisateurs et contexte d'usage

- **Utilisateur principal** : un passager en cabine, en mode avion, téléphone en main ou posé sur la
  tablette, parfois batterie limitée (prises souvent absentes en economy).
- **Contraintes réelles de l'usage en vol** : pas de réseau, GNSS seul, position côté hublot
  meilleure qu'au couloir, écran éteint pendant une grande partie du vol, luminosité maximale.
- **Conséquence** : l'app doit tenir plusieurs heures avec l'écran éteint (service au premier plan),
  afficher une interface lisible d'un coup d'œil et ne jamais bloquer sur une erreur réseau.

---

## 4. Périmètre fonctionnel de la v1

### 4.1 Écran 1 : Préparation du vol

- **Aéroport de départ** : champ de recherche (code IATA « CDG », OACI « LFPG », ou nom de ville
  « Tokyo »). Résultats en liste : `CDG · Paris Charles de Gaulle · France`. Sélection par un tap.
- **Aéroport d'arrivée** : même champ. Interdiction de choisir le même aéroport aux deux bouts.
- **Inversion** départ/arrivée en un bouton.
- **Heure de décollage** : pré-remplie à l'heure courante, modifiable (utile si l'app est lancée
  après le décollage).
- **Aperçu** : distance totale grand-cercle entre les deux aéroports, durée théorique à une vitesse
  de croisière de référence (850 km/h, ajustable), cap initial. Une petite carte montre la ligne
  entre les deux points.
- **Préparation hors ligne de la carte** : bouton « Télécharger la carte du trajet » qui met en
  cache les tuiles autour du corridor du trajet (largeur ~250 km) et affiche la taille estimée
  avant de lancer. Indispensable : sans ce cache, la carte sera vide en vol.
- **Bouton « Décoller »** : passe à l'écran de vol, démarre le service de localisation.

### 4.2 Écran 2 : Suivi du vol (écran principal)

Bandeau de valeurs, avec gros chiffres lisibles en une seconde :

| Valeur | Source | Détail |
|---|---|---|
| **Altitude** | GPS (altitude ellipsoïdale) **et** baromètre si le téléphone en a un | Le baromètre est bien plus stable que le GPS en vol. Altitude barométrique calculée avec la formule ICAO à partir de la pression, référence QNH 1013,25 hPa, recalée sur la dernière altitude GPS fiable. Les deux valeurs sont affichées (« 10 680 m », avec « GPS 10 720 m » en second). |
| **Vitesse sol** | GPS | En km/h, lissée sur les 5 derniers points (médiane glissante) pour éviter les sauts. |
| **Cap** | GPS | En degrés + rose des vents simplifiée (« 274° O »). |
| **Distance restante** | Calcul | Distance grand-cercle (haversine) entre la position actuelle et l'aéroport d'arrivée, en km. |
| **Durée restante** | Calcul | `distance restante / vitesse sol lissée`, avec un plancher de vitesse à 200 km/h pour éviter une durée aberrante au roulage. Recalculée à chaque point. |
| **Heure d'arrivée estimée** | Calcul | Heure courante + durée restante, affichée en heure locale du téléphone. |
| **Progression** | Calcul | `1 - distance restante / distance totale`, en % et en barre horizontale. |

Carte (occupe le reste de l'écran) :

- fond **OpenStreetMap** (raster, cache local, attribution obligatoire visible) ;
- marqueurs des deux aéroports, ligne pointillée départ → arrivée ;
- position actuelle (flèche orientée au cap), trace parcourue en trait plein ;
- bouton « recentrer », zoom par pincement ;
- si une tuile manque (hors zone téléchargée) : afficher un fond uni avec le message « carte non
  téléchargée pour cette zone », **jamais** un écran blanc muet.

Pied d'écran : heure de départ réelle, temps de vol écoulé, bouton « Terminer le vol ».
L'écran ne se met pas en veille (option désactivable), luminosité non modifiée.

### 4.3 Écran 3 : Résumé de fin de vol

- Aéroports, heure de décollage et d'arrivée, **durée réelle** du vol.
- Distance parcourue réelle (somme des segments GPS, plus longue que la grand-cercle : c'est
  normal, un avion ne vole pas en ligne droite) et écart avec la distance théorique.
- Altitude maximale, vitesse maximale, vitesse moyenne.
- Trace enregistrée : nombre de points, écart maximal à la route théorique (en km).
- **Export GPX** (partage Android : mail, stockage cloud, autre appareil). Fichier nommé
  `vol_LHR_JFK_2026-06-14.gpx`.
- Bouton « Nouveau vol » qui repart de l'écran de préparation, et « Effacer ce vol ».

### 4.4 Réglages (écran discret, accessible par l'engrenage)

- Vitesse de croisière de référence (850 km/h par défaut) : sert à la durée théorique et au calcul
  de la progression avant le décollage.
- Unités : mètres ou pieds, km/h ou nœuds. Défaut : mètres et km/h.
- Fréquence de localisation : 1 s (défaut) / 5 s / 15 s. Le mode 15 s suffit en croisière et
  économise la batterie.
- Garder l'écran allumé : oui par défaut.
- Thème clair / sombre / système (défaut système).

---

## 5. Modèle de données (local, sans serveur)

Tout est stocké en base locale. Aucune synchronisation.

```
vol
  id                : entier auto
  aeroportDepart    : code IATA (clé de la base aéroports)
  aeroportArrivee   : code IATA
  decolleLe         : datetime (epoch ms)
  termineLe         : datetime nullable
  distanceTheoriqueKm : réel
  sequence          : entier (numéro de vol, pour ne pas mélanger deux vols)

point_trace
  id                : entier auto
  volId             : clé étrangère
  horodatage        : epoch ms
  lat, lon          : réels (WGS84)
  altitudeGpsM      : réel nullable
  altitudeBaroM     : réel nullable
  vitesseSolKmh     : réel nullable
  capDeg            : réel nullable
  precisionM        : réel nullable
```

Base d'aéroports : livrée en asset JSON, lecture seule, non recopiée en base à l'installation.

---

## 6. Données de référence

- **Aéroports** : jeu de données **OurAirports** (`airports.csv`, domaine public) filtré sur
  `scheduled_service = yes` et `type` dans (large_airport, medium_airport) : environ 4 000 entrées.
  Champs conservés : `ident` (OACI), `iata_code`, `name`, `municipality`, `iso_country`, `latitude_deg`,
  `longitude_deg`, `elevation_ft`. Convertis en JSON compact (≈ 500 Ko) et embarqué dans `assets/`.
  Recherche : insensible à la casse et aux accents, priorité au code exact, puis préfixe de code,
  puis nom de ville, puis nom d'aéroport.
- **Fond de carte** : OpenStreetMap, politique d'usage des tuiles respectée (User-Agent applicatif
  identifiable, cache, pas de prétéléchargement massif en masse). Attribution « © OpenStreetMap
  contributors » affichée en permanence sur la carte.
- **Altitudes de référence** : `elevation_ft` des aéroports sert à afficher l'altitude du terrain et
  à vérifier la cohérence à l'atterrissage.

---

## 7. Calculs (à tester unitairement)

1. **Distance grand-cercle** (haversine, rayon terrestre 6 371 008,8 m).
2. **Cap initial** (formule du cap orthodromique), ramené à 0-360.
3. **Altitude barométrique** : `h = 44 330 × (1 - (P / 1013,25)^0,1903)` en mètres, P en hPa.
   Recalage : `décalage = altitudeGpsFiable - altitudeBaro` lissé sur 10 mesures, plafonné à ±150 m,
   puis `altitudeAffichée = altitudeBaro + décalage`.
4. **Vitesse lissée** : médiane des 5 dernières vitesses GPS non nulles.
5. **Durée restante** : `distanceRestante / max(vitesseLissée, 200 km/h)` en heures, convertie en
   `h min`. Si la vitesse est nulle plus de 5 minutes (appareil au sol, GPS perdu), afficher
   « en attente de mouvement » au lieu d'une durée absurde.
6. **Progression** : bornée entre 0 et 100 %.
7. **Distance parcourue** : somme des segments entre points consécutifs, points à plus de 2 km
   d'écart ou de plus de 10 minutes ignorés (trous de réception).

Tous ces calculs sont dans un module Kotlin pur (`core/`) sans dépendance Android, testés par des
tests JUnit reproductibles, avec des valeurs de référence connues (ex. LHR → JFK = 5 539 km,
cap initial 288°).

---

## 8. Exigences techniques

| Sujet | Exigence |
|---|---|
| Plateforme | Android 8.0 (API 26) minimum, cible API 35. |
| Langue | Kotlin, Jetpack Compose (Material 3), une seule activité, navigation entre les 3 écrans. |
| Localisation | **GNSS natif** (`LocationManager` / `GnssStatus`) plutôt que Play Services : aucune dépendance Google Play, aucun réseau, fonctionne en mode avion. Rythme piloté par les réglages, `minDistance = 0`. |
| Premier plan | `ForegroundService` de type `location` avec notification persistante « Vol LHR → JFK en cours · 10 680 m · 890 km/h », pour que le suivi continue écran éteint et application en arrière-plan. |
| Capteurs | `Sensor.TYPE_PRESSURE` en `SENSOR_DELAY_NORMAL` si disponible ; détection propre de l'absence. |
| Permissions | `ACCESS_FINE_LOCATION`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS` (API 33+). Refus de la localisation = écran explicatif, pas de crash. |
| Carte | `osmdroid` 6.1.x (tuiles raster OSM, cache disque géré, licence libre) : plus simple et plus léger que MapLibre pour de la raster hors ligne. |
| Cache hors ligne | Téléchargement du corridor du trajet avant le vol, taille annoncée, possibilité d'effacer le cache depuis les réglages. |
| Persistance | Room, deux entités (`vol`, `point_trace`). |
| Stockage batterie | GPS à 1 s = ≈ 5 à 8 %/h en pointe. Réglage 15 s en croisière. Pas de wake-lock hors service de localisation. |
| Vie privée | Aucun réseau, aucune analytique, aucune permission INTERNET pour la v1 (sauf téléchargement des tuiles, donc INTERNET est nécessaire : elle n'est utilisée **que** pour les tuiles, et cette limite est écrite dans l'à-propos). |
| Livraison | APK signé, versionné (`SuiviVol_2026xxxx_v1.0.apk`), publié en release GitHub, avec mise à jour automatique reprenant le mécanisme déjà en place sur les autres apps (release roulante + `version.json`). Icône propre à l'app. |
| Tests | Tests JUnit sur `core/` (calculs), test instrumenté sur le parsing de la base d'aéroports, vérification réelle sur appareil en vol ou en voiture (vitesse > 90 km/h, altitude non testable au sol). |

---

## 9. Contraintes et limites physiques (à dire dans l'app)

- **Le GNSS fonctionne en mode avion** : la puce GPS ne fait que recevoir. En revanche, la position
  demande quelques minutes d'accrochage, et **seul le côté hublot** garantit une réception correcte.
  Au couloir ou au milieu d'un gros-porteur, la position peut être absente ou sauter.
- **L'altitude GPS est imprécise** (± 20 à 50 m, biais ellipsoïdal) : c'est le baromètre qui donne
  une valeur stable, d'où l'affichage des deux.
- **La vitesse GPS est une vitesse sol**, pas une vitesse air : elle inclut l'effet du vent (jusqu'à
  ± 200 km/h en jet-stream). La durée restante est donc une estimation.
- **La durée restante ignore les détours réels** (attente d'atterrissage, circuit d'approche,
  dégagement) : compter 20 à 30 minutes de plus en pratique.
- **Le mode avion doit rester activé** avec la localisation en marche : c'est la configuration
  recommandée, et elle n'empêche pas l'app de fonctionner.

---

## 10. Découpage de la livraison

| Étape | Contenu | Livrable |
|---|---|---|
| **0. Cadrage** | Ce document, maquettes des 3 écrans | SPEC validée + maquette HTML cliquable |
| **1. Socle** | Projet Android, thème, base d'aéroports embarquée, recherche, écran de préparation, navigation | APK v0.1 utilisable pour choisir un trajet |
| **2. Calculs** | Module `core/` complet + tests unitaires (haversine, cap, altitudes, durées) | Tests verts, journal de sortie |
| **3. GPS et suivi** | Service au premier plan, points de trace, bandeau de valeurs, écran de vol | APK v0.2 : fonctionne en voiture, en vélo |
| **4. Carte** | osmdroid, ligne théorique, trace, cache du corridor | APK v0.3 : carte utilisable hors ligne |
| **5. Fin de vol** | Résumé, export GPX, réglages | APK v0.4 |
| **6. Livraison** | Icône, signature, release GitHub, auto-update, README d'installation | APK v1.0 publié |
| **7. Évolution** | Récupération automatique du vol (API) en plus de la saisie manuelle | v2 |

Critère de réussite de la v1 : lors d'un vol réel en mode avion, l'app affiche en continu une
altitude cohérente avec l'écran de cabine (± 300 m), une vitesse cohérente (± 50 km/h), et une durée
restante qui se réduit régulièrement jusqu'à l'atterrissage.

---

## 11. Questions ouvertes

1. **Faut-il prévoir une tablette / un mode paysage** dédié (bandeau à droite, carte à gauche) ?
2. **Route réelle ou grand-cercle** : en v2, tracer la route airways simplifiée plutôt que la ligne
   droite ?
3. **Multi-vols** : garder l'historique de tous les vols et proposer une liste, ou un seul vol à la
   fois (v1 : un seul vol actif, l'historique des vols terminés est conservé) ?
4. **Widget / complication** : un widget 2×1 avec altitude, vitesse et durée restante ?

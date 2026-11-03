# Plan v1 — Suivi de vol GPS

> Statut : v0.1.0 construite et signée (versionCode 1), 38 tests unitaires verts.


Cadrage : `docs/SPEC.md`. Squelette Gradle déjà en place (compose, osmdroid 6.1.20,
`com.trucdecomptable.suivivol`, minSdk 26, `assembleDebug` vert).

Base d'aéroports déjà générée : `app/src/main/assets/airports.csv` (8 925 aéroports
à service régulier ou avec code IATA, 789 Ko, séparateur `;`,
colonnes `code;iata;icao;nom;lat;lon;pays;ville;type;regulier`). Source
OurAirports (domaine public), aéroports fermés `(*)` exclus.

## Principe

Le GPS seul. La saisie manuelle des deux aéroports reste **toujours** possible :
c'est la source de référence. La devinette ne fait que **proposer**, et se
corrige en trois taps.

## Phase 1 — données, géométrie et moteur de devinette (aucune UI)

Fichiers à créer sous `app/src/main/java/com/trucdecomptable/suivivol/` :

```kotlin
// data/Aeroport.kt
data class Aeroport(val code: String, val iata: String, val icao: String, val nom: String,
                    val lat: Double, val lon: Double, val pays: String, val ville: String,
                    val regulier: Boolean)
object Aeroports {
    fun parser(lignes: List<String>): List<Aeroport>        // pure, testable
    fun charger(contexte: Context): List<Aeroport>          // lit assets/airports.csv, cache mémoire
    fun rechercher(tous: List<Aeroport>, texte: String, max: Int = 20): List<Aeroport>  // code, nom ou ville
    fun proches(tous: List<Aeroport>, lat: Double, lon: Double, rayonKm: Double): List<Aeroport>
    fun parCode(tous: List<Aeroport>, code: String): Aeroport?
}

// data/Geo.kt
object Geo {
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double
    fun capDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double
    fun ecartCapDeg(cap1: Double, cap2: Double): Double      // 0..180
    fun pointApres(lat: Double, lon: Double, capDeg: Double, distanceKm: Double): Pair<Double, Double>
    fun pointLePlusProche(lat: Double, lon: Double, capDeg: Double, latCible: Double, lonCible: Double): Pair<Double, Double>
}

// vol/PointVol.kt
data class PointVol(val lat: Double, val lon: Double, val altitudeM: Double,
                    val vitesseSolKmh: Double, val capDeg: Double,
                    val vitesseVerticaleMs: Double, val horodatageMs: Long)

enum class PhaseVol { SOL, DECOLLAGE, MONTEE, CROISIERE, DESCENTE, ARRIVE }
enum class OrigineVol { MANUELLE, DEVINEE }

// vol/SourceVol.kt — ce que l'interface impose, pour que l'API v2 s'ajoute sans casser
interface SourceVol {
    val depart: Aeroport?
    val arrivee: Aeroport?
    val origine: OrigineVol
    val confiance: Double        // 0..1
}

// vol/DevinetteVol.kt
class DevinetteVol(private val aeroports: List<Aeroport>) {
    fun ajouter(point: PointVol): EtatVol
}
data class EtatVol(val phase: PhaseVol, val depart: Aeroport?, val arrivee: Aeroport?,
                   val candidats: List<Candidat>, val confiance: Double)
data class Candidat(val aeroport: Aeroport, val score: Double)
```

Règles (celles du SPEC, à implémenter au plus près) :
- décollage quand `vitesseSolKmh > 150` et `altitudeM > 1000` tenus 60 s ;
- départ = dernier aéroport à moins de 15 km de la position **au sol** avant le décollage,
  mémorisé à chaque point tant qu'on est au sol ;
- candidats en vol : aéroports à plus de 100 km du point courant, écart de cap < 15°,
  score = écart de cap + détour (`distance via le point` − `distance directe`) ;
- en descente (`vitesseVerticaleMs < -3`) : l'aéroport le plus proche du point
  d'impact projeté (position + cap + distance restante) prend la tête ;
- confiance = 1 − (score du premier − score du deuxième) normalisée, bornée entre 0 et 1 ;
- jamais de devinette au sol : `degrade` l'état à `PhaseVol.SOL` et garde le départ mémorisé.

Tests JUnit (`app/src/test/.../`) :
`GeoTest` (Paris→Marseille ≈ 660 km, cap ≈ 155°, écart de cap symétrique, pointApres cohérent),
`AeroportsTest` (parsing, recherche par code/nom/ville, proches dans un rayon, code inconnu),
`DevinetteVolTest` (au sol → SOL sans candidat ; décollage près de CDG ; croisière cap ouest →
candidats filtrés ; descente près de JFK → JFK en tête avec confiance > 0,5).

## Phase 2 — interface (après validation de la phase 1)

Trois écrans Compose : **Préparation** (recherche IATA/OACI/ville, deux champs toujours
éditables, bouton « Deviner d'après le GPS » quand un départ est détecté, pré-téléchargement
des tuiles autour de la route), **Vol** (carte osmdroid hors ligne, bandeau altitude /
vitesse sol / cap / vitesse verticale, distance et durée restante, destination probable +
bouton corriger), **Arrivée** (résumé, export GPX, remise à zéro).
Service de localisation en avant-plan, écran allumé en vol.

## Vérification

Phase 1 : `./gradlew :app:testDebugUnitTest` vert. Phase 2 : `assembleDebug` + tests verts,
puis APK de test et essai réel sur un vol.

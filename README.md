# Suivi de vol GPS

Application Android qui suit un vol en avion **avec le seul GPS du téléphone** : position sur une
carte OpenStreetMap, altitude, vitesse sol, distance et durée restante jusqu'à l'aéroport d'arrivée.

Fonctionne **en mode avion**, sans compte, sans serveur, sans télémétrie.

## État

Cadrage terminé, développement à venir.

- Cahier des charges : [`docs/SPEC.md`](docs/SPEC.md)
- Décisions verrouillées : Android natif (Kotlin/Compose), GPS seul, aéroports saisis à la main,
  récupération automatique du vol en v2.

## Ce que fera l'app

Trois écrans :

1. **Préparation** : aéroport de départ et d'arrivée (recherche par code IATA/OACI ou par ville,
   base d'aéroports embarquée), heure de décollage, distance théorique, téléchargement préalable des
   tuiles de carte du trajet.
2. **Vol** : carte OSM hors ligne, trace, altitude (GPS + baromètre), vitesse sol lissée, cap,
   distance restante, durée restante, heure d'arrivée estimée, progression.
3. **Résumé** : durée réelle, distance parcourue, altitude et vitesse maximales, export GPX.

## Limites assumées

- L'altitude GPS est imprécise : le baromètre du téléphone fait foi quand il existe.
- La vitesse GPS est une vitesse sol : le vent fausse l'estimation de durée restante.
- Ce n'est pas un instrument de navigation aéronautique.

## Licence

À définir avant la première release.

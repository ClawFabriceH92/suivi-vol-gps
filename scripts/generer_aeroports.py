#!/usr/bin/env python3
"""Génère app/src/main/assets/aeroports.json à partir du fichier airports.csv d'OurAirports.

Usage : python3 scripts/generer_aeroports.py chemin/vers/airports.csv

Source : OurAirports (https://ourairports.com/data/), domaine public.
Filtre : scheduled_service = yes et type dans (large_airport, medium_airport).
Format de sortie : tableau JSON compact, une entrée par aéroport :
  [oaci, iata, nom, ville, pays ISO-2, latitude, longitude, altitude_ft, grand]
  - iata vaut "" si absent, altitude_ft vaut null si absente,
  - grand vaut 1 pour large_airport, 0 pour medium_airport.
"""
import csv
import json
import sys
from pathlib import Path

SORTIE = Path(__file__).resolve().parent.parent / "app/src/main/assets/aeroports.json"


def principal(chemin_csv: str) -> None:
    with open(chemin_csv, encoding="utf-8", newline="") as f:
        lignes = list(csv.DictReader(f))
    retenus = []
    for r in lignes:
        if r["scheduled_service"] != "yes":
            continue
        if r["type"] not in ("large_airport", "medium_airport"):
            continue
        oaci = r["ident"].strip()
        if not oaci:
            continue
        try:
            lat = round(float(r["latitude_deg"]), 5)
            lon = round(float(r["longitude_deg"]), 5)
        except ValueError:
            continue
        alt = r["elevation_ft"].strip()
        retenus.append([
            oaci,
            r["iata_code"].strip(),
            r["name"].strip(),
            r["municipality"].strip(),
            r["iso_country"].strip(),
            lat,
            lon,
            int(alt) if alt else None,
            1 if r["type"] == "large_airport" else 0,
        ])
    retenus.sort(key=lambda a: (a[8] == 0, a[1] or "~", a[0]))
    SORTIE.parent.mkdir(parents=True, exist_ok=True)
    with open(SORTIE, "w", encoding="utf-8") as f:
        json.dump(retenus, f, ensure_ascii=False, separators=(",", ":"))
    print(f"{len(retenus)} aéroports écrits dans {SORTIE} ({SORTIE.stat().st_size // 1024} Ko)")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print(__doc__)
        sys.exit(1)
    principal(sys.argv[1])

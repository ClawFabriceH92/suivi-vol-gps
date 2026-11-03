#!/usr/bin/env python3
"""Régénère app/src/main/assets/airports.csv depuis OurAirports (domaine public).

    python3 tools/gen_airports.py [--source URL] [--sortie CHEMIN]

Filtre retenu : aéroports ni fermés (pas de préfixe « (*) » dans le nom) ni de
type autre que large/medium/small, à condition d'avoir un service régulier ou au
moins un code IATA. C'est ce qui donne ~8 900 entrées pour ~790 Ko, assez pour
deviner une destination sans alourdir l'APK.

La base est embarquée : aucune requête réseau à l'exécution, donc l'app reste
utilisable en mode avion.
"""
from __future__ import annotations

import argparse
import csv
import io
import os
import sys
import urllib.request

DEFAUT = "https://davidmegginson.github.io/ourairports-data/airports.csv"
SORTIE = "app/src/main/assets/airports.csv"
TYPES = ("large_airport", "medium_airport", "small_airport")


def telecharger(url: str) -> str:
    with urllib.request.urlopen(url, timeout=120) as r:
        return r.read().decode("utf-8")


def convertir(brut: str) -> list[tuple]:
    lignes = []
    for r in csv.DictReader(io.StringIO(brut)):
        if r["name"].startswith("(*"):
            continue
        if r["type"] not in TYPES:
            continue
        if r["scheduled_service"] != "yes" and not r["iata_code"]:
            continue
        code = r["iata_code"] or r["icao_code"] or r["ident"]
        lignes.append((
            code, r["iata_code"], r["icao_code"], r["name"],
            r["latitude_deg"], r["longitude_deg"], r["iso_country"],
            r["municipality"], r["type"], r["scheduled_service"],
        ))
    lignes.sort(key=lambda x: (x[6], x[3]))
    return lignes


def ecrire(lignes: list[tuple], chemin: str) -> None:
    os.makedirs(os.path.dirname(chemin), exist_ok=True)
    with open(chemin, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, delimiter=";")
        w.writerow(["code", "iata", "icao", "nom", "lat", "lon", "pays", "ville", "type", "regulier"])
        w.writerows(lignes)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--source", default=DEFAUT)
    ap.add_argument("--sortie", default=SORTIE)
    a = ap.parse_args()
    lignes = convertir(telecharger(a.source))
    if len(lignes) < 1000:
        sys.exit(f"suspect : seulement {len(lignes)} aéroports, rien n'a été écrit")
    ecrire(lignes, a.sortie)
    taille = os.path.getsize(a.sortie) / 1024
    fr = sum(1 for l in lignes if l[6] == "FR")
    iata = sum(1 for l in lignes if l[1])
    print(f"{a.sortie} : {len(lignes)} aéroports ({iata} avec IATA, {fr} en France), {taille:.0f} Ko")


if __name__ == "__main__":
    main()

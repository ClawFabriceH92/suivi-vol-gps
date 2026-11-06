#!/usr/bin/env python3
"""Aperçu fidèle du rendu des cadrans (mêmes cotes que Cadrans.kt).

Ce script ne sert qu'à visualiser : il rejoue la géométrie exacte du code
Kotlin (rayons, graduations, plages, aiguille) en SVG, pour juger l'esthétique
sans passer par un appareil.
"""
import math

FOND_CADRE = "#1B2231"
FOND_CADRAN = "#0A0D14"
FOND_DEGRADE = "#161D2B"
LUNETTE = "#39435A"
LUNETTE_HAUTE = "#6B7893"
GRADUATION = "#B8C6DC"
GRADUATION_FINE = "#B8C6DC"
AIGUILLE = "#FF4B3E"
MOYEU = "#E9EFF8"
TEXTE = "#E9EFF8"
DISCRET = "#8FA0BA"
VERT = "#35D07F"
JAUNE = "#F2C14E"
BLANC = "#D5DEEA"

TAILLE = 300.0
R = TAILLE / 2 * 0.93


def pt(cx, cy, r, deg):
    rad = math.radians(deg - 90)
    return cx + r * math.cos(rad), cy + r * math.sin(rad)


def arc(cx, cy, r, a0, a1, couleur, largeur, opacite=1.0):
    x0, y0 = pt(cx, cy, r, a0)
    x1, y1 = pt(cx, cy, r, a1)
    grand = 1 if (a1 - a0) > 180 else 0
    return (f'<path d="M {x0:.2f} {y0:.2f} A {r:.2f} {r:.2f} 0 {grand} 1 {x1:.2f} {y1:.2f}" '
            f'fill="none" stroke="{couleur}" stroke-width="{largeur:.2f}" opacity="{opacite}" stroke-linecap="butt"/>')


def angle_valeur(v, vmin, vmax, a0=-130.0, a1=130.0):
    ratio = min(max((v - vmin) / (vmax - vmin), 0.0), 1.0)
    return a0 + ratio * (a1 - a0)


def lunette_et_face(cx, cy, uid):
    return f'''
    <defs>
      <linearGradient id="lun{uid}" x1="0" y1="0" x2="1" y2="1">
        <stop offset="0%" stop-color="{LUNETTE_HAUTE}"/>
        <stop offset="55%" stop-color="{LUNETTE}"/>
        <stop offset="100%" stop-color="{FOND_CADRE}"/>
      </linearGradient>
      <radialGradient id="face{uid}" cx="50%" cy="40%" r="62%">
        <stop offset="0%" stop-color="{FOND_DEGRADE}"/>
        <stop offset="100%" stop-color="{FOND_CADRAN}"/>
      </radialGradient>
    </defs>
    <circle cx="{cx}" cy="{cy}" r="{R:.2f}" fill="url(#lun{uid})"/>
    <circle cx="{cx}" cy="{cy}" r="{R*0.90:.2f}" fill="none" stroke="#05070B" stroke-width="2"/>
    <circle cx="{cx}" cy="{cy}" r="{R*0.90:.2f}" fill="url(#face{uid})"/>'''


def aiguille(cx, cy, rayon, angle, avant=0.82, arriere=0.18):
    rad = math.radians(angle - 90)
    dx, dy = math.cos(rad), math.sin(rad)
    px, py = -dy, dx
    b = rayon * 0.030
    pointe = (cx + dx * rayon * avant, cy + dy * rayon * avant)
    queue = (cx - dx * rayon * arriere, cy - dy * rayon * arriere)
    a = (cx + px * b, cy + py * b)
    c = (cx - px * b, cy - py * b)
    d = rayon * 0.006
    tri = lambda off: (f'<polygon points="{pointe[0]+off:.2f},{pointe[1]+off:.2f} '
                       f'{a[0]+off:.2f},{a[1]+off:.2f} {c[0]+off:.2f},{c[1]+off:.2f}" fill="{AIGUILLE}"/>')
    return (f'<polygon points="{pointe[0]+d:.2f},{pointe[1]+d:.2f} {a[0]+d:.2f},{a[1]+d:.2f} '
            f'{c[0]+d:.2f},{c[1]+d:.2f}" fill="#000" opacity="0.4"/>'
            + tri(0)
            + f'<line x1="{cx}" y1="{cy}" x2="{queue[0]:.2f}" y2="{queue[1]:.2f}" stroke="{AIGUILLE}" '
              f'stroke-width="{rayon*0.05:.2f}" opacity="0.55" stroke-linecap="round"/>'
            + moyeu(cx, cy, rayon))


def moyeu(cx, cy, rayon):
    return (f'<circle cx="{cx}" cy="{cy}" r="{rayon*0.065:.2f}" fill="#05070B"/>'
            f'<circle cx="{cx}" cy="{cy}" r="{rayon*0.050:.2f}" fill="{MOYEU}"/>'
            f'<circle cx="{cx}" cy="{cy}" r="{rayon*0.016:.2f}" fill="#05070B"/>')


def texte(x, y, contenu, taille, couleur=TEXTE, gras=True, mono=False):
    famille = "DejaVu Sans Mono, monospace" if mono else "DejaVu Sans, sans-serif"
    poids = "bold" if gras else "normal"
    return (f'<text x="{x:.2f}" y="{y:.2f}" fill="{couleur}" font-size="{taille:.2f}" '
            f'text-anchor="middle" font-family="{famille}" font-weight="{poids}">{contenu}</text>')


def cadran_arc(titre, unite, valeur_txt, valeur, vmin, vmax, pas_grad, pas_etiq, plages,
               a0=-130.0, a1=130.0, zero=False, uid="0"):
    cx = cy = TAILLE / 2
    s = [lunette_et_face(cx, cy, uid)]
    for debut, fin, couleur in plages:
        s.append(arc(cx, cy, R * 0.70, angle_valeur(debut, vmin, vmax, a0, a1),
                     angle_valeur(fin, vmin, vmax, a0, a1), couleur, R * 0.055, 0.95))
    g = vmin
    while g <= vmax + 1e-9:
        ang = angle_valeur(g, vmin, vmax, a0, a1)
        majeur = abs(((g - vmin) % pas_etiq)) < 1e-6
        long = R * 0.13 if majeur else R * 0.07
        x1, y1 = pt(cx, cy, R * 0.76, ang)
        x2, y2 = pt(cx, cy, R * 0.76 - long, ang)
        s.append(f'<line x1="{x1:.2f}" y1="{y1:.2f}" x2="{x2:.2f}" y2="{y2:.2f}" '
                 f'stroke="{GRADUATION}" stroke-width="{3 if majeur else 1.4}" '
                 f'opacity="{1 if majeur else 0.4}"/>')
        if majeur:
            tx, ty = pt(cx, cy, R * 0.55, ang)
            val = int(g)
            etiq = f"{val//1000}k" if pas_etiq >= 1000 and val and val % 1000 == 0 else str(val)
            s.append(texte(tx, ty + R * 0.055, etiq, R * 0.15))
        g += pas_grad
    if zero:
        zx, zy = pt(cx, cy, R * 0.33, (a0 + a1) / 2)
        s.append(texte(zx, zy + R * 0.05, "0", R * 0.15, DISCRET, False))
    if valeur is None:
        s.append(moyeu(cx, cy, R))
    else:
        s.append(aiguille(cx, cy, R, angle_valeur(valeur, vmin, vmax, a0, a1)))
    corp = "".join(s)
    return f'''
  <div class="cadran">
    <svg viewBox="0 0 {TAILLE} {TAILLE}" width="100%">{corp}</svg>
    <div class="valeur">{valeur_txt}</div>
    <div class="titre">{titre} · {unite}</div>
  </div>'''


def cadran_cap(cap_txt, cap, cardinal, uid="c"):
    cx = cy = TAILLE / 2
    s = [lunette_et_face(cx, cy, uid)]
    # la carte tourne à l'opposé du cap
    s.append(f'<g transform="rotate({-cap:.2f} {cx} {cy})">')
    for deg in range(0, 360, 5):
        majeur = deg % 30 == 0
        long = R * 0.14 if deg % 90 == 0 else (R * 0.10 if majeur else R * 0.055)
        x1, y1 = pt(cx, cy, R * 0.86, deg)
        x2, y2 = pt(cx, cy, R * 0.86 - long, deg)
        s.append(f'<line x1="{x1:.2f}" y1="{y1:.2f}" x2="{x2:.2f}" y2="{y2:.2f}" '
                 f'stroke="{GRADUATION}" stroke-width="{3 if majeur else 1.3}" opacity="{1 if majeur else 0.4}"/>')
    for deg in range(0, 360, 30):
        cardinal_l = {0: "N", 90: "E", 180: "S", 270: "O"}.get(deg)
        if cardinal_l:
            tx, ty = pt(cx, cy, R * 0.60, deg)
            s.append(texte(tx, ty + R * 0.065, cardinal_l, R * 0.19, AIGUILLE if cardinal_l == "N" else TEXTE))
        else:
            tx, ty = pt(cx, cy, R * 0.62, deg)
            s.append(texte(tx, ty + R * 0.045, f"{deg//10:02d}", R * 0.13, DISCRET, False))
    s.append('</g>')
    # index fixe en haut
    px_, py_ = pt(cx, cy, R * 0.90, 0)
    gx, gy = pt(cx, cy, R * 0.74, -3.2)
    dx_, dy_ = pt(cx, cy, R * 0.74, 3.2)
    s.append(f'<polygon points="{px_:.2f},{py_:.2f} {gx:.2f},{gy:.2f} {dx_:.2f},{dy_:.2f}" fill="{JAUNE}"/>')
    # symbole appareil
    av = pt(cx, cy, R * 0.34, 0); ar = pt(cx, cy, R * 0.30, 180)
    ag = pt(cx, cy, R * 0.20, 270); ad = pt(cx, cy, R * 0.20, 90)
    s.append(f'<line x1="{ar[0]:.2f}" y1="{ar[1]:.2f}" x2="{av[0]:.2f}" y2="{av[1]:.2f}" stroke="{TEXTE}" stroke-width="3.5"/>')
    s.append(f'<line x1="{ag[0]:.2f}" y1="{ag[1]:.2f}" x2="{ad[0]:.2f}" y2="{ad[1]:.2f}" stroke="{TEXTE}" stroke-width="3.5"/>')
    s.append(moyeu(cx, cy, R))
    return f'''
  <div class="cadran">
    <svg viewBox="0 0 {TAILLE} {TAILLE}" width="100%">{"".join(s)}</svg>
    <div class="valeur">{cap_txt} <span style="color:{DISCRET}; font-size:13px">{cardinal}</span></div>
    <div class="titre">CAP</div>
  </div>'''


HTML = f'''<!DOCTYPE html>
<html lang="fr"><head><meta charset="utf-8"><style>
  * {{ margin:0; padding:0; box-sizing:border-box; }}
  body {{ background:#0d1016; font-family:"DejaVu Sans",Arial,sans-serif; padding:24px; }}
  .grille {{ display:grid; grid-template-columns:1fr 1fr; gap:14px; width:760px; }}
  .cadran {{ background:rgba(10,13,20,.94); border:1px solid rgba(255,255,255,.25);
             border-radius:22px; padding:10px 10px 8px; display:flex; flex-direction:column;
             align-items:center; }}
  .valeur {{ color:{TEXTE}; font-family:"DejaVu Sans Mono",monospace; font-weight:bold;
             font-size:20px; margin-top:-4px; }}
  .titre {{ color:{DISCRET}; font-size:11px; letter-spacing:1.2px; margin-top:2px; }}
  .legende {{ color:#7b8aa8; font-size:13px; text-align:center; margin-top:14px; letter-spacing:.4px; }}
</style></head><body>
  <div class="grille">
    {cadran_cap("225°", 225, "SO", "a")}
    {cadran_arc("VARIOMÈTRE", "m/s", "+3 m/s", 3, -10, 10, 2, 2,
                [(-10, -2, VERT), (2, 10, VERT)], -120, 120, zero=True, uid="b")}
    {cadran_arc("VITESSE SOL", "km/h", "842 km/h", 842, 0, 1000, 50, 200,
                [(0, 1000, BLANC), (700, 950, VERT)], uid="d")}
    {cadran_arc("ALTITUDE", "m", "10 700 m", 10700, 0, 12000, 500, 3000,
                [(0, 12000, BLANC), (9000, 12000, "#90CAF9")], uid="e")}
  </div>
  <div class="legende">Rendu cible des cadrans : lunette, face en dégradé, graduations hiérarchisées, plages colorées, aiguille effilée</div>
</body></html>'''

if __name__ == "__main__":
    chemin = "/root/projects/suivi-vol-gps/maquette/cadrans_pro.html"
    with open(chemin, "w") as f:
        f.write(HTML)
    print("écrit", chemin)

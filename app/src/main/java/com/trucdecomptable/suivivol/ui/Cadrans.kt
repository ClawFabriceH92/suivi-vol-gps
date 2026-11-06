package com.trucdecomptable.suivivol.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trucdecomptable.suivivol.vol.CadranMath
import com.trucdecomptable.suivivol.vol.PointVol
import kotlin.math.cos
import kotlin.math.sin

// --- Palette d'instruments : fonds sombres, aiguille rouge, plages normalisées ---
private val FOND_CADRE = Color(0xFF1B2231)
private val FOND_CADRAN = Color(0xFF0A0D14)
private val FOND_DEGRADE = Color(0xFF161D2B)
private val LUNETTE = Color(0xFF39435A)
private val LUNETTE_HAUTE = Color(0xFF6B7893)
private val GRADUATION = Color(0xFFB8C6DC)
private val GRADUATION_FINE = Color(0x66B8C6DC)
private val AIGUILLE = Color(0xFFFF4B3E)
private val AIGUILLE_OMBRE = Color(0x66000000)
private val MOYEU = Color(0xFFE9EFF8)
private val TEXTE_CADRAN = Color(0xFFE9EFF8)
private val TEXTE_DISCRET = Color(0xFF8FA0BA)
private val VERT_PLAGE = Color(0xFF35D07F)
private val JAUNE_PLAGE = Color(0xFFF2C14E)
private val BLANC_PLAGE = Color(0xFFD5DEEA)

/** Plage colorée d'un cadran (début, fin, couleur). */
private data class Plage(val debut: Double, val fin: Double, val couleur: Color)

/**
 * Tableau de bord « avion » : uniquement des cadrans, sans carte ni texte long.
 *
 * Quatre instruments, disposés en grille :
 * 1. cap (rose des vents complète, aiguille qui suit le cap GPS),
 * 2. variomètre (vitesse verticale, -10 à +10 m/s),
 * 3. vitesse sol (0 à 300 km/h, plages façon badin),
 * 4. altitude (0 à 5 000 m).
 *
 * Rendu d'instrument réel : lunette métallique, face en dégradé radial,
 * graduations hiérarchisées, plages colorées, aiguille effilée avec contrepoids
 * et moyeu, ombre portée discrète. Une valeur inconnue affiche « — » et
 * l'aiguille reste au repos, jamais au hasard.
 */
@Composable
fun TableauDeBord(
    dernier: PointVol?,
    modifier: Modifier = Modifier,
    /** Opacité des panneaux, en pourcentage : 100 = opaques, 0 = invisibles. */
    opacitePanneaux: Int = 85,
) {
    Column(
        modifier.fillMaxSize().padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PanneauCadran(Modifier.weight(1f).aspectRatio(1f), opacitePanneaux) {
                CadranCap(cap = dernier?.capDeg, modifier = Modifier.fillMaxSize())
            }
            PanneauCadran(Modifier.weight(1f).aspectRatio(1f), opacitePanneaux) {
                CadranInstrument(
                    titre = "VARIOMÈTRE",
                    unite = "m/s",
                    valeurTexte = CadranMath.libelleVitesseVerticale(dernier?.vitesseVerticaleMs),
                    valeur = dernier?.vitesseVerticaleMs,
                    min = -10.0,
                    max = 10.0,
                    pasGraduation = 2.0,
                    pasEtiquette = 2.0,
                    angleDebut = -120.0,
                    angleFin = 120.0,
                    plages = listOf(
                        Plage(-10.0, -2.0, VERT_PLAGE),
                        Plage(2.0, 10.0, VERT_PLAGE),
                    ),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PanneauCadran(Modifier.weight(1f).aspectRatio(1f), opacitePanneaux) {
                CadranInstrument(
                    titre = "VITESSE SOL",
                    unite = "km/h",
                    valeurTexte = CadranMath.libelleVitesse(dernier?.vitesseSolKmh),
                    valeur = dernier?.vitesseSolKmh,
                    // Échelle calée sur la réalité : un biréacteur croise vers
                    // 800-900 km/h, un cadran plafonné à 300 serait toujours en butée.
                    min = 0.0,
                    max = 1000.0,
                    pasGraduation = 50.0,
                    pasEtiquette = 200.0,
                    angleDebut = -130.0,
                    angleFin = 130.0,
                    // Bande de croisière usuelle, le reste de l'échelle en blanc.
                    plages = listOf(
                        Plage(0.0, 1000.0, BLANC_PLAGE),
                        Plage(700.0, 950.0, VERT_PLAGE),
                    ),
                    modifier = Modifier.fillMaxSize(),
                )
            }
            PanneauCadran(Modifier.weight(1f).aspectRatio(1f), opacitePanneaux) {
                CadranInstrument(
                    titre = "ALTITUDE",
                    unite = "m",
                    valeurTexte = CadranMath.libelleAltitude(dernier?.altitudeM),
                    valeur = dernier?.altitudeM,
                    // Jusqu'à 12 000 m : altitude de croisière des longs-courriers.
                    min = 0.0,
                    max = 12000.0,
                    pasGraduation = 500.0,
                    pasEtiquette = 3000.0,
                    angleDebut = -130.0,
                    angleFin = 130.0,
                    plages = listOf(
                        Plage(0.0, 12000.0, BLANC_PLAGE),
                        Plage(9000.0, 12000.0, Color(0xFF90CAF9)),
                    ),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * Panneau translucide derrière chaque cadran : la carte reste visible entre les
 * instruments, mais les aiguilles restent lisibles sur les tuiles.
 */
@Composable
private fun PanneauCadran(
    modifier: Modifier = Modifier,
    opacitePourcent: Int = 85,
    contenu: @Composable () -> Unit,
) {
    val alpha = opacitePourcent.coerceIn(0, 100) / 100f * 0.94f
    Surface(
        color = FOND_CADRAN.copy(alpha = alpha),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0x40FFFFFF)),
        modifier = modifier,
    ) {
        contenu()
    }
}

/**
 * Compas type HSI : la carte (chiffres + points cardinaux) tourne sous un index
 * fixe, comme sur un instrument réel. Le chiffre lu en haut est le cap.
 */
@Composable
private fun CadranCap(cap: Double?, modifier: Modifier = Modifier) {
    val utilisable = CadranMath.valeurUtilisable(cap)
    val capNormalise = if (utilisable) CadranMath.capNormalise(cap!!) else 0.0

    Column(modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val rayon = size.minDimension / 2f * 0.93f
            val centre = Offset(size.width / 2f, size.height / 2f)

            dessinerLunette(centre, rayon)
            dessinerFace(centre, rayon * 0.90f)

            // La carte tourne à l'opposé du cap : le repère du haut donne le cap.
            rotate(degrees = -capNormalise.toFloat(), pivot = centre) {
                for (degre in 0 until 360 step 5) {
                    val majeure = degre % 30 == 0
                    val longueur = when {
                        degre % 90 == 0 -> rayon * 0.14f
                        majeure -> rayon * 0.10f
                        else -> rayon * 0.055f
                    }
                    val exterieur = pointSurCercle(centre, rayon * 0.86f, degre.toDouble())
                    val interieur = pointSurCercle(centre, rayon * 0.86f - longueur, degre.toDouble())
                    drawLine(
                        color = if (majeure) GRADUATION else GRADUATION_FINE,
                        start = interieur,
                        end = exterieur,
                        strokeWidth = if (majeure) 3f else 1.3f,
                    )
                }
                // Chiffres tous les 30°, points cardinaux à leur place.
                for (degre in 0 until 360 step 30) {
                    val cardinal = when (degre) {
                        0 -> "N"
                        90 -> "E"
                        180 -> "S"
                        270 -> "O"
                        else -> null
                    }
                    val p = pointSurCercle(centre, rayon * 0.60f, degre.toDouble())
                    if (cardinal != null) {
                        texteCadran(
                            cardinal,
                            p,
                            taille = rayon * 0.19f,
                            couleur = if (cardinal == "N") AIGUILLE else TEXTE_CADRAN,
                            gras = true,
                            decalageY = rayon * 0.065f,
                        )
                    } else {
                        texteCadran(
                            "%02d".format(degre / 10),
                            pointSurCercle(centre, rayon * 0.62f, degre.toDouble()),
                            taille = rayon * 0.13f,
                            couleur = TEXTE_DISCRET,
                            decalageY = rayon * 0.045f,
                        )
                    }
                }
            }

            // Index fixe (lubber line) en haut : c'est lui qui donne le cap.
            val pointeIndex = pointSurCercle(centre, rayon * 0.90f, 0.0)
            val baseG = pointSurCercle(centre, rayon * 0.74f, -3.2)
            val baseD = pointSurCercle(centre, rayon * 0.74f, 3.2)
            val index = Path().apply {
                moveTo(pointeIndex.x, pointeIndex.y)
                lineTo(baseG.x, baseG.y)
                lineTo(baseD.x, baseD.y)
                close()
            }
            drawPath(index, color = JAUNE_PLAGE)

            // Symbole appareil au centre, fixe.
            val avant = pointSurCercle(centre, rayon * 0.34f, 0.0)
            val arriere = pointSurCercle(centre, rayon * 0.30f, 180.0)
            val aileG = pointSurCercle(centre, rayon * 0.20f, 270.0)
            val aileD = pointSurCercle(centre, rayon * 0.20f, 90.0)
            drawLine(TEXTE_CADRAN, arriere, avant, strokeWidth = 3.5f)
            drawLine(TEXTE_CADRAN, aileG, aileD, strokeWidth = 3.5f)
            dessinerMoyeu(centre, rayon)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (utilisable) CadranMath.libelleCap(cap) else "—",
                color = TEXTE_CADRAN,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 17.sp,
            )
            if (utilisable) {
                Text(
                    "  ${CadranMath.pointCardinal(capNormalise)}",
                    color = TEXTE_DISCRET,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Text("CAP", color = TEXTE_DISCRET, fontSize = 9.5.sp, letterSpacing = 1.4.sp)
    }
}

/** Cadran générique : lunette, face, graduations, plages colorées, aiguille. */
@Composable
private fun CadranInstrument(
    titre: String,
    unite: String,
    valeurTexte: String,
    valeur: Double?,
    min: Double,
    max: Double,
    pasGraduation: Double,
    pasEtiquette: Double,
    angleDebut: Double,
    angleFin: Double,
    plages: List<Plage> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val utilisable = CadranMath.valeurUtilisable(valeur)

    Column(modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val rayon = size.minDimension / 2f * 0.93f
            val centre = Offset(size.width / 2f, size.height / 2f)

            dessinerLunette(centre, rayon)
            dessinerFace(centre, rayon * 0.90f)

            // Plages colorées, juste à l'intérieur de la lunette.
            plages.forEach { plage ->
                val debut = CadranMath.angleAiguille(plage.debut, min, max, angleDebut, angleFin)
                val fin = CadranMath.angleAiguille(plage.fin, min, max, angleDebut, angleFin)
                drawArc(
                    color = plage.couleur,
                    startAngle = (debut + 90.0).toFloat(),
                    sweepAngle = (fin - debut).toFloat(),
                    useCenter = false,
                    // Bande extérieure : l'arc ne doit croiser ni les graduations
                    // ni les chiffres (constaté illisible sur petit cadran).
                    topLeft = Offset(centre.x - rayon * 0.885f, centre.y - rayon * 0.885f),
                    size = Size(rayon * 1.77f, rayon * 1.77f),
                    style = Stroke(width = rayon * 0.048f),
                )
            }

            // Graduations : fines partout, majeures avec étiquette.
            var graduation = min
            while (graduation <= max + 1e-9) {
                val angle = CadranMath.angleAiguille(graduation, min, max, angleDebut, angleFin)
                val estEtiquette = estMultiple(graduation - min, pasEtiquette)
                val longueur = if (estEtiquette) rayon * 0.135f else rayon * 0.075f
                val p1 = pointSurCercle(centre, rayon * 0.845f, angle)
                val p2 = pointSurCercle(centre, rayon * 0.845f - longueur, angle)
                drawLine(
                    color = if (estEtiquette) GRADUATION else GRADUATION_FINE,
                    start = p1,
                    end = p2,
                    strokeWidth = if (estEtiquette) 3f else 1.4f,
                )
                if (estEtiquette) {
                    val pTexte = pointSurCercle(centre, rayon * 0.575f, angle)
                    texteCadran(
                        etiquette(graduation, pasEtiquette),
                        pTexte,
                        taille = rayon * 0.165f,
                        couleur = TEXTE_CADRAN,
                        decalageY = rayon * 0.055f,
                    )
                }
                graduation += pasGraduation
            }
            if (utilisable) {
                val angle = CadranMath.angleAiguille(valeur!!, min, max, angleDebut, angleFin)
                dessinerAiguille(centre, rayon, angle, longueurAvant = 0.795f, longueurArriere = 0.18f)
            } else {
                dessinerMoyeu(centre, rayon)
            }
        }
        Text(
            valeurTexte,
            color = TEXTE_CADRAN,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            fontSize = 15.sp,
        )
        Text("$titre · $unite", color = TEXTE_DISCRET, fontSize = 9.sp, letterSpacing = 1.1.sp)
    }
}

// ------------------------------------------------------------------ dessin

/** Lunette métallique : anneau clair en haut, sombre en bas. */
private fun DrawScope.dessinerLunette(centre: Offset, rayon: Float) {
    drawCircle(
        brush = Brush.linearGradient(
            colors = listOf(LUNETTE_HAUTE, LUNETTE, FOND_CADRE),
            start = Offset(centre.x - rayon, centre.y - rayon),
            end = Offset(centre.x + rayon, centre.y + rayon),
        ),
        radius = rayon,
        center = centre,
    )
    // Filet intérieur : sépare la lunette de la face.
    drawCircle(color = Color(0xFF05070B), radius = rayon * 0.90f, center = centre, style = Stroke(width = 2f))
}

/** Face du cadran : dégradé radial, plus clair au centre. */
private fun DrawScope.dessinerFace(centre: Offset, rayon: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(FOND_DEGRADE, FOND_CADRAN),
            center = Offset(centre.x, centre.y - rayon * 0.18f),
            radius = rayon * 1.6f,
        ),
        radius = rayon,
        center = centre,
    )
}

/** Aiguille effilée, ombre portée et moyeu central. */
private fun DrawScope.dessinerAiguille(
    centre: Offset,
    rayon: Float,
    angle: Double,
    longueurAvant: Float,
    longueurArriere: Float,
) {
    val radians = Math.toRadians(angle - 90.0)
    val dx = cos(radians).toFloat()
    val dy = sin(radians).toFloat()
    // Perpendiculaire : donne de l'épaisseur à la base de l'aiguille.
    val px = -dy
    val py = dx
    val baseDemi = rayon * 0.030f
    val pointe = Offset(centre.x + dx * rayon * longueurAvant, centre.y + dy * rayon * longueurAvant)
    val queue = Offset(centre.x - dx * rayon * longueurArriere, centre.y - dy * rayon * longueurArriere)
    val coteA = Offset(centre.x + px * baseDemi, centre.y + py * baseDemi)
    val coteB = Offset(centre.x - px * baseDemi, centre.y - py * baseDemi)

    fun triangle(couleur: Color, decalage: Float = 0f) {
        val chemin = Path().apply {
            moveTo(pointe.x + decalage, pointe.y + decalage)
            lineTo(coteA.x + decalage, coteA.y + decalage)
            lineTo(coteB.x + decalage, coteB.y + decalage)
            close()
        }
        drawPath(chemin, color = couleur)
    }

    triangle(AIGUILLE_OMBRE, decalage = rayon * 0.006f)
    triangle(AIGUILLE)
    drawLine(
        color = AIGUILLE.copy(alpha = 0.55f),
        start = centre,
        end = queue,
        strokeWidth = rayon * 0.05f,
    )
    dessinerMoyeu(centre, rayon)
}

/** Moyeu : trois cercles concentriques, comme sur un instrument réel. */
private fun DrawScope.dessinerMoyeu(centre: Offset, rayon: Float) {
    drawCircle(color = Color(0xFF05070B), radius = rayon * 0.065f, center = centre)
    drawCircle(color = MOYEU, radius = rayon * 0.050f, center = centre)
    drawCircle(color = Color(0xFF05070B), radius = rayon * 0.016f, center = centre)
}

/** Texte centré dessiné dans le canvas, avec anti-aliasing. */
private fun DrawScope.texteCadran(
    texte: String,
    position: Offset,
    taille: Float,
    couleur: Color,
    gras: Boolean = false,
    decalageY: Float = 0f,
) {
    drawContext.canvas.nativeCanvas.drawText(
        texte,
        position.x,
        position.y + decalageY,
        android.graphics.Paint().apply {
            color = couleur.toArgbInt()
            textSize = taille
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            typeface = if (gras) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
        },
    )
}

// ------------------------------------------------------------------ outils

/** Point du cercle pour un angle « 0 = haut, sens horaire ». */
private fun pointSurCercle(centre: Offset, rayon: Float, angleDeg: Double): Offset {
    val radians = Math.toRadians(angleDeg - 90.0)
    return Offset(
        centre.x + (rayon * cos(radians)).toFloat(),
        centre.y + (rayon * sin(radians)).toFloat(),
    )
}

/** Vrai si [ecart] est un multiple de [pas] (tolérance flottante). */
private fun estMultiple(ecart: Double, pas: Double): Boolean {
    if (pas <= 0.0) return false
    val reste = ecart % pas
    return reste < 1e-6 || pas - reste < 1e-6
}

/** Étiquette compacte : 1000 devient 1k pour rester lisible sur un cadran. */
private fun etiquette(valeur: Double, pas: Double): String {
    val entiere = valeur.toInt()
    return if (pas >= 1000.0 && entiere != 0 && entiere % 1000 == 0) "${entiere / 1000}k" else entiere.toString()
}

/** Conversion Color Compose -> entier ARGB pour le Paint Android. */
private fun Color.toArgbInt(): Int =
    android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt(),
    )

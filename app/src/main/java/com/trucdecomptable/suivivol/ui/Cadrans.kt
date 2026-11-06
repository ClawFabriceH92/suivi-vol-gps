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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trucdecomptable.suivivol.vol.CadranMath
import com.trucdecomptable.suivivol.vol.PointVol
import kotlin.math.cos
import kotlin.math.sin

/**
 * Tableau de bord « avion » : uniquement des cadrans, sans carte ni texte long.
 *
 * Quatre instruments, disposés en grille :
 * 1. cap (rose des vents complète, aiguille qui suit le cap GPS),
 * 2. variomètre (vitesse verticale, -10 à +10 m/s, zéro au sommet),
 * 3. vitesse sol (0 à 300 km/h),
 * 4. altitude (0 à 5 000 m).
 *
 * Toutes les valeurs proviennent du dernier point GPS ; une valeur inconnue est
 * affichée « — » et l'aiguille reste au repos, jamais au hasard.
 */
@Composable
fun TableauDeBord(
    dernier: PointVol?,
    modifier: Modifier = Modifier,
    /** Opacité des panneaux, en pourcentage : 100 = opaques, 0 = invisibles. */
    opacitePanneaux: Int = 85,
) {
    val couleurCadran = MaterialTheme.colorScheme.outlineVariant
    val couleurAiguille = MaterialTheme.colorScheme.error
    val couleurAccent = MaterialTheme.colorScheme.primary
    val couleurTexte = MaterialTheme.colorScheme.onSurface

    Column(
        modifier.fillMaxSize().padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PanneauCadran(Modifier.weight(1f).aspectRatio(1f), opacitePanneaux) {
                CadranCap(
                cap = dernier?.capDeg,
                modifier = Modifier.weight(1f).aspectRatio(1f),
                couleurCadran = couleurCadran,
                couleurAiguille = couleurAiguille,
                    couleurTexte = couleurTexte,
                )
            }
            PanneauCadran(Modifier.weight(1f).aspectRatio(1f), opacitePanneaux) {
                CadranAArc(
                    titre = "VARIOMÈTRE",
                valeurTexte = CadranMath.libelleVitesseVerticale(dernier?.vitesseVerticaleMs),
                valeur = dernier?.vitesseVerticaleMs,
                min = -10.0,
                max = 10.0,
                angleDebut = -120.0,
                angleFin = 120.0,
                couleurCadran = couleurCadran,
                couleurAiguille = couleurAiguille,
                couleurAccent = couleurAccent,
                couleurTexte = couleurTexte,
                    graduationTousLes = 2.0,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PanneauCadran(Modifier.weight(1f).aspectRatio(1f), opacitePanneaux) {
                CadranAArc(
                    titre = "VITESSE SOL",
                valeurTexte = CadranMath.libelleVitesse(dernier?.vitesseSolKmh),
                valeur = dernier?.vitesseSolKmh,
                min = 0.0,
                max = 300.0,
                angleDebut = -130.0,
                angleFin = 130.0,
                couleurCadran = couleurCadran,
                couleurAiguille = couleurAiguille,
                couleurAccent = couleurAccent,
                couleurTexte = couleurTexte,
                    graduationTousLes = 50.0,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            PanneauCadran(Modifier.weight(1f).aspectRatio(1f), opacitePanneaux) {
                CadranAArc(
                    titre = "ALTITUDE",
                valeurTexte = CadranMath.libelleAltitude(dernier?.altitudeM),
                valeur = dernier?.altitudeM,
                min = 0.0,
                max = 5000.0,
                angleDebut = -130.0,
                angleFin = 130.0,
                couleurCadran = couleurCadran,
                couleurAiguille = couleurAiguille,
                couleurAccent = couleurAccent,
                couleurTexte = couleurTexte,
                    graduationTousLes = 1000.0,
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
    val alpha = (opacitePourcent.coerceIn(0, 100) / 100f * 0.94f)
    Surface(
        color = Color(0x0B0E14).copy(alpha = alpha),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0x33FFFFFF)),
        modifier = modifier,
    ) {
        contenu()
    }
}

/** Rose des vents complète avec aiguille de cap. */
@Composable
private fun CadranCap(
    cap: Double?,
    modifier: Modifier,
    couleurCadran: Color,
    couleurAiguille: Color,
    couleurTexte: Color,
) {
    val utilisable = CadranMath.valeurUtilisable(cap)
    val capNormalise = if (utilisable) CadranMath.capNormalise(cap!!) else 0.0

    Column(modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val rayon = size.minDimension / 2f * 0.92f
            val centre = Offset(size.width / 2f, size.height / 2f)

            drawCircle(color = couleurCadran, radius = rayon, center = centre, style = Stroke(width = 3f))
            drawCircle(
                color = couleurCadran.copy(alpha = 0.35f),
                radius = rayon * 0.72f,
                center = centre,
                style = Stroke(width = 2f),
            )

            // Graduations tous les 30°, majeures tous les 90°.
            for (degre in 0 until 360 step 30) {
                val longueur = if (degre % 90 == 0) rayon * 0.16f else rayon * 0.09f
                val exterieur = pointSurCercle(centre, rayon, degre.toDouble())
                val interieur = pointSurCercle(centre, rayon - longueur, degre.toDouble())
                drawLine(
                    color = couleurCadran,
                    start = interieur,
                    end = exterieur,
                    strokeWidth = if (degre % 90 == 0) 4f else 2f,
                )
            }
            // Points cardinaux
            listOf("N" to 0, "E" to 90, "S" to 180, "O" to 270).forEach { (lettre, degre) ->
                val p = pointSurCercle(centre, rayon * 0.62f, degre.toDouble())
                drawContext.canvas.nativeCanvas.drawText(
                    lettre,
                    p.x,
                    p.y + 8f,
                    android.graphics.Paint().apply {
                        color = couleurTexte.toArgbInt()
                        textSize = rayon * 0.14f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                        if (lettre == "N") typeface = android.graphics.Typeface.DEFAULT_BOLD
                    },
                )
            }

            // Aiguille : rouge vers l'avant, queue courte à l'opposé.
            if (utilisable) {
                val pointe = pointSurCercle(centre, rayon * 0.78f, capNormalise)
                val queue = pointSurCercle(centre, rayon * 0.22f, capNormalise + 180.0)
                drawLine(couleurAiguille, queue, pointe, strokeWidth = 6f)
                drawCircle(color = couleurAiguille, radius = 7f, center = centre)
            }
        }
        Text(
            if (utilisable) "${CadranMath.libelleCap(cap)}  ${CadranMath.pointCardinal(capNormalise)}" else "—",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text("CAP", style = MaterialTheme.typography.labelSmall)
    }
}

/** Cadran générique : arc gradué, aiguille, valeur au centre. */
@Composable
private fun CadranAArc(
    titre: String,
    valeurTexte: String,
    valeur: Double?,
    min: Double,
    max: Double,
    angleDebut: Double,
    angleFin: Double,
    couleurCadran: Color,
    couleurAiguille: Color,
    couleurAccent: Color,
    couleurTexte: Color,
    graduationTousLes: Double,
    modifier: Modifier,
) {
    val utilisable = CadranMath.valeurUtilisable(valeur)

    Column(modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val rayon = size.minDimension / 2f * 0.90f
            val centre = Offset(size.width / 2f, size.height / 2f)

            // Les arcs Compose partent de 3 h (90°) et tournent dans le sens
            // horaire : on convertit nos angles « 0 = haut ».
            drawArc(
                color = couleurCadran,
                startAngle = (angleDebut + 90.0).toFloat(),
                sweepAngle = (angleFin - angleDebut).toFloat(),
                useCenter = false,
                topLeft = Offset(centre.x - rayon, centre.y - rayon),
                size = Size(rayon * 2, rayon * 2),
                style = Stroke(width = 4f),
            )

            var graduation = min
            while (graduation <= max + 1e-9) {
                val angle = CadranMath.angleAiguille(graduation, min, max, angleDebut, angleFin)
                val p1 = pointSurCercle(centre, rayon, angle)
                val p2 = pointSurCercle(centre, rayon * 0.88f, angle)
                drawLine(couleurCadran, p1, p2, strokeWidth = 2f)
                // Étiquette de graduation (une sur deux pour rester lisible)
                val index = ((graduation - min) / graduationTousLes).toInt()
                if (index % 2 == 0) {
                    val pTexte = pointSurCercle(centre, rayon * 0.70f, angle)
                    drawContext.canvas.nativeCanvas.drawText(
                        graduation.toInt().toString(),
                        pTexte.x,
                        pTexte.y + 6f,
                        android.graphics.Paint().apply {
                            color = couleurTexte.toArgbInt()
                            textSize = rayon * 0.13f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                        },
                    )
                }
                graduation += graduationTousLes
            }

            if (utilisable) {
                val angle = CadranMath.angleAiguille(valeur!!, min, max, angleDebut, angleFin)
                val pointe = pointSurCercle(centre, rayon * 0.82f, angle)
                val queue = pointSurCercle(centre, rayon * 0.18f, angle + 180.0)
                drawLine(couleurAiguille, queue, pointe, strokeWidth = 6f)
                drawCircle(color = couleurAccent, radius = 8f, center = centre)
            }
        }
        Text(valeurTexte, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(titre, style = MaterialTheme.typography.labelSmall)
    }
}

/** Point du cercle pour un angle « 0 = haut, sens horaire ». */
private fun pointSurCercle(centre: Offset, rayon: Float, angleDeg: Double): Offset {
    val radians = Math.toRadians(angleDeg - 90.0)
    return Offset(
        centre.x + (rayon * cos(radians)).toFloat(),
        centre.y + (rayon * sin(radians)).toFloat(),
    )
}

/** Conversion Color Compose -> entier ARGB pour le Paint Android. */
private fun Color.toArgbInt(): Int =
    android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt(),
    )

/** Dessin utilitaire conservé pour les cas où seul un trait est nécessaire. */
private fun DrawScope.trait(centre: Offset, rayon: Float, angle: Double, couleur: Color, epaisseur: Float = 2f) {
    drawLine(couleur, centre, pointSurCercle(centre, rayon, angle), strokeWidth = epaisseur)
}

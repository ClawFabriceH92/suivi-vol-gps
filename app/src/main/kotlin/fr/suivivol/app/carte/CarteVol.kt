package fr.suivivol.app.carte

import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import fr.suivivol.app.R
import fr.suivivol.core.Coordonnee
import fr.suivivol.core.Geo
import fr.suivivol.core.PlanDeVol
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.ScaleBarOverlay

/** Couches dessinées sur la carte : ligne théorique, trace, marqueurs. */
private class CouchesCarte(val carte: MapView) {
    val ligneTheorique = Polyline(carte).apply {
        outlinePaint.color = Color.argb(200, 11, 61, 145)
        outlinePaint.strokeWidth = 5f
        outlinePaint.pathEffect = DashPathEffect(floatArrayOf(18f, 14f), 0f)
        outlinePaint.strokeCap = Paint.Cap.ROUND
        setOnClickListener { _, _, _ -> false }
    }
    val trace = Polyline(carte).apply {
        outlinePaint.color = Color.argb(230, 211, 47, 47)
        outlinePaint.strokeWidth = 7f
        outlinePaint.strokeCap = Paint.Cap.ROUND
        setOnClickListener { _, _, _ -> false }
    }
    val marqueurDepart = Marker(carte).apply {
        icon = ContextCompat.getDrawable(carte.context, R.drawable.ic_aeroport)
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        setInfoWindow(null)
    }
    val marqueurArrivee = Marker(carte).apply {
        icon = ContextCompat.getDrawable(carte.context, R.drawable.ic_aeroport)
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        setInfoWindow(null)
    }
    val marqueurPosition = Marker(carte).apply {
        icon = ContextCompat.getDrawable(carte.context, R.drawable.ic_avion)
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        setInfoWindow(null)
        isFlat = true
    }

    var planAffiche: PlanDeVol? = null
    var nbPointsTrace = -1
    var cadrageInitialFait = false

    init {
        carte.overlays.add(ligneTheorique)
        carte.overlays.add(trace)
        carte.overlays.add(marqueurDepart)
        carte.overlays.add(marqueurArrivee)
        carte.overlays.add(marqueurPosition)
        // Échelle en haut à gauche, attribution en bas à gauche : les deux ne se chevauchent pas.
        carte.overlays.add(ScaleBarOverlay(carte).apply { setAlignBottom(false); setAlignRight(false); setScaleBarOffset(16, 16) })
        carte.overlays.add(CopyrightOverlay(carte.context).apply { setCopyrightNotice(TuilesOsm.ATTRIBUTION); setAlignBottom(true); setAlignRight(false) })
    }
}

/**
 * Carte OpenStreetMap du vol : fond raster en cache, ligne pointillée départ → arrivée, trace
 * parcourue, position courante orientée au cap.
 *
 * @param horsLigne si vrai, la carte ne tente aucun accès réseau (lecture du cache uniquement).
 * @param suivrePosition si vrai, la carte reste centrée sur la position courante.
 * @param demandeRecentrage compteur incrémenté par le bouton « recentrer ».
 */
@Composable
fun CarteVol(
    modifier: Modifier = Modifier,
    plan: PlanDeVol?,
    position: Coordonnee?,
    capDeg: Double?,
    trace: List<Coordonnee>,
    horsLigne: Boolean,
    suivrePosition: Boolean,
    demandeRecentrage: Int = 0,
    zoomPosition: Double = 7.0,
    interactif: Boolean = true,
    onInteractionUtilisateur: (() -> Unit)? = null,
) {
    val contexte = LocalContext.current
    val cycleDeVie = LocalLifecycleOwner.current
    val carte = remember {
        MapView(contexte).apply {
            setTileSource(TuilesOsm.SOURCE)
            tileProvider.setTileLoadFailureImage(TuilesOsm.imageTuileAbsente(contexte))
            setMultiTouchControls(interactif)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            setHorizontalMapRepetitionEnabled(true)
            setVerticalMapRepetitionEnabled(false)
            setMinZoomLevel(2.0)
            setMaxZoomLevel(12.0)
            setScrollableAreaLimitLatitude(MapView.getTileSystem().maxLatitude, MapView.getTileSystem().minLatitude, 0)
            controller.setZoom(3.0)
            controller.setCenter(GeoPoint(30.0, 0.0))
            if (!interactif) {
                setOnTouchListener { _, _ -> true }
            }
        }
    }
    // La MapView dessine ses tuiles et ses couches au-delà de ses bords ; un conteneur classique
    // (clipChildren actif par défaut) les rogne, ce que l'hôte Compose des vues ne fait pas.
    val conteneur = remember(carte) {
        FrameLayout(contexte).apply {
            clipChildren = true
            clipToPadding = true
            addView(carte, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
    }
    val couches = remember(carte) { CouchesCarte(carte) }
    val dernierRecentrage = remember { intArrayOf(-1) }

    DisposableEffect(cycleDeVie, carte) {
        val observateur = LifecycleEventObserver { _, evenement ->
            when (evenement) {
                Lifecycle.Event.ON_RESUME -> carte.onResume()
                Lifecycle.Event.ON_PAUSE -> carte.onPause()
                else -> Unit
            }
        }
        cycleDeVie.lifecycle.addObserver(observateur)
        onDispose {
            cycleDeVie.lifecycle.removeObserver(observateur)
            carte.onDetach()
        }
    }

    DisposableEffect(carte, onInteractionUtilisateur) {
        val ecouteur = if (onInteractionUtilisateur != null) {
            android.view.View.OnTouchListener { _, evenement ->
                if (evenement.action == android.view.MotionEvent.ACTION_MOVE || evenement.pointerCount > 1) {
                    onInteractionUtilisateur()
                }
                false
            }
        } else null
        if (ecouteur != null && interactif) carte.setOnTouchListener(ecouteur)
        onDispose { if (ecouteur != null && interactif) carte.setOnTouchListener(null) }
    }

    AndroidView(
        modifier = modifier.clipToBounds(),
        factory = { conteneur },
        update = { _ ->
            val vue = carte
            vue.setUseDataConnection(!horsLigne)

            if (plan != null && plan != couches.planAffiche) {
                couches.planAffiche = plan
                val points = Geo.pointsGrandCercle(plan.depart.coordonnee, plan.arrivee.coordonnee, 96)
                couches.ligneTheorique.setPoints(points.map { GeoPoint(it.lat, it.lon) })
                couches.marqueurDepart.position = GeoPoint(plan.depart.lat, plan.depart.lon)
                couches.marqueurDepart.title = plan.depart.code
                couches.marqueurArrivee.position = GeoPoint(plan.arrivee.lat, plan.arrivee.lon)
                couches.marqueurArrivee.title = plan.arrivee.code
                couches.cadrageInitialFait = false
            }

            if (trace.size != couches.nbPointsTrace) {
                couches.nbPointsTrace = trace.size
                val pts = trace.map { GeoPoint(it.lat, it.lon) }.toMutableList()
                if (position != null) pts.add(GeoPoint(position.lat, position.lon))
                couches.trace.setPoints(pts)
            } else if (position != null && couches.trace.actualPoints.isNotEmpty()) {
                val pts = couches.trace.actualPoints.toMutableList()
                pts[pts.size - 1] = GeoPoint(position.lat, position.lon)
                couches.trace.setPoints(pts)
            }

            if (position != null) {
                couches.marqueurPosition.isEnabled = true
                couches.marqueurPosition.position = GeoPoint(position.lat, position.lon)
                // osmdroid tourne l'icône dans le sens trigonométrique : on inverse le signe du cap.
                couches.marqueurPosition.rotation = -(capDeg ?: 0.0).toFloat()
            } else {
                couches.marqueurPosition.isEnabled = false
            }

            val recentrageDemande = demandeRecentrage != dernierRecentrage[0]
            if (recentrageDemande) dernierRecentrage[0] = demandeRecentrage

            if (position != null && (suivrePosition || recentrageDemande)) {
                if (recentrageDemande || !couches.cadrageInitialFait) {
                    vue.controller.setZoom(zoomPosition)
                    couches.cadrageInitialFait = true
                }
                vue.controller.setCenter(GeoPoint(position.lat, position.lon))
            } else if (position == null && plan != null && (!couches.cadrageInitialFait || recentrageDemande)) {
                couches.cadrageInitialFait = true
                val boite = BoundingBox.fromGeoPoints(
                    couches.ligneTheorique.actualPoints.ifEmpty {
                        listOf(GeoPoint(plan.depart.lat, plan.depart.lon), GeoPoint(plan.arrivee.lat, plan.arrivee.lon))
                    },
                )
                vue.post {
                    if (vue.width > 0 && vue.height > 0) {
                        runCatching { vue.zoomToBoundingBox(boite.increaseByScale(1.3f), false, 40) }
                    }
                }
            }
            vue.invalidate()
        },
    )
}

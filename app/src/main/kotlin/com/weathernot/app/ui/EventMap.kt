package com.weathernot.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * Where the event is, on OpenStreetMap tiles (osmdroid) -- no Maps API key
 * or billing account needed. It doesn't pan or zoom inside the scrolling
 * page; tapping it opens the spot in the phone's maps app instead.
 */
@Composable
fun EventMap(latitude: Double, longitude: Double, label: String, modifier: Modifier = Modifier, height: Dp = 180.dp) {
    val context = LocalContext.current
    val map = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(false)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
        }
    }
    DisposableEffect(map) {
        map.onResume()
        onDispose {
            map.onPause()
            map.onDetach()
        }
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(14.dp)),
    ) {
        AndroidView(
            factory = { map },
            update = { view ->
                val point = GeoPoint(latitude, longitude)
                view.overlays.clear()
                view.overlays.add(
                    Marker(view).apply {
                        position = point
                        title = label
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    },
                )
                view.controller.setZoom(14.0)
                view.controller.setCenter(point)
                view.invalidate()
            },
            modifier = Modifier.fillMaxSize(),
        )
        // On top of the map: takes the tap (and leaves drags to the page's scroll).
        Box(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Map of $label. Opens in your maps app." }
                .clickable {
                    val uri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude(${Uri.encode(label)})")
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    } catch (_: ActivityNotFoundException) {
                        // No maps app installed -- the map itself is all there is.
                    }
                },
        )
    }
}

package com.tji.device.product.radiodetection.map

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.TextureMapView
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.CircleOptions
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.MyLocationStyle
import com.amap.api.maps.model.PolylineOptions
import com.tji.device.product.radiodetection.model.RadioDetectionTarget
import com.tji.device.product.radiodetection.model.RadioDetectionUiState

@Composable
fun RadioDetectionAmapView(
    config: RadioDetectionMapConfig,
    state: RadioDetectionUiState,
    focusedTargetId: String?,
    focusTargetSignal: Int,
    zoomSignal: Int,
    zoomDelta: Float,
    recenterSignal: Int,
    onTargetClick: (RadioDetectionTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        TextureMapView(context).apply {
            onCreate(Bundle())
            onResume()
        }
    }
    var initialCameraApplied by remember { mutableStateOf(false) }
    var lastFocusedTargetId by remember { mutableStateOf<String?>(null) }
    var lastFocusTargetSignal by remember { mutableIntStateOf(focusTargetSignal) }
    var lastZoomSignal by remember { mutableIntStateOf(zoomSignal) }
    var lastRecenterSignal by remember { mutableIntStateOf(recenterSignal) }
    var locationLayerEnabled by remember { mutableStateOf<Boolean?>(null) }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onDestroy()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { mapView },
        update = { view ->
            view.map.apply {
                mapType = AMap.MAP_TYPE_NORMAL
                isTrafficEnabled = false
                uiSettings.isZoomControlsEnabled = false
                uiSettings.isScaleControlsEnabled = false
                uiSettings.isCompassEnabled = false
                uiSettings.isMyLocationButtonEnabled = false
                val hasLocationPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                if (locationLayerEnabled != hasLocationPermission) {
                    setLocationLayerEnabled(enabled = hasLocationPermission)
                    locationLayerEnabled = hasLocationPermission
                }
                val currentCenter = state.mapCenterOrDefault(config)
                if (!initialCameraApplied) {
                    moveCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            currentCenter,
                            config.defaultZoom
                        )
                    )
                    initialCameraApplied = true
                }

                val focusedTarget = state.targets.firstOrNull { it.id == focusedTargetId }
                if (
                    focusedTarget != null &&
                    (focusedTargetId != lastFocusedTargetId || focusTargetSignal != lastFocusTargetSignal)
                ) {
                    animateToTargetPair(focusedTarget)
                    lastFocusedTargetId = focusedTargetId
                    lastFocusTargetSignal = focusTargetSignal
                } else if (focusedTargetId == null) {
                    lastFocusedTargetId = null
                    lastFocusTargetSignal = focusTargetSignal
                }

                if (zoomSignal != lastZoomSignal) {
                    animateCamera(CameraUpdateFactory.zoomBy(zoomDelta))
                    lastZoomSignal = zoomSignal
                }

                if (recenterSignal != lastRecenterSignal) {
                    focusedTarget?.let { animateToTargetPair(it) }
                        ?: animateCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                currentCenter,
                                config.defaultZoom
                            )
                        )
                    lastRecenterSignal = recenterSignal
                }

                renderRadioDetectionOverlays(state, focusedTargetId, onTargetClick)
            }
        }
    )
}

private fun AMap.setLocationLayerEnabled(enabled: Boolean) {
    if (!enabled) {
        isMyLocationEnabled = false
        return
    }
    myLocationStyle = MyLocationStyle()
        .myLocationType(MyLocationStyle.LOCATION_TYPE_SHOW)
        .interval(30_000L)
    isMyLocationEnabled = true
}

private fun AMap.renderRadioDetectionOverlays(
    state: RadioDetectionUiState,
    focusedTargetId: String?,
    onTargetClick: (RadioDetectionTarget) -> Unit
) {
    clear()

    if (state.currentCoordinate.hasUsableCoordinate()) {
        val center = LatLng(state.currentCoordinate.latitude, state.currentCoordinate.longitude)
        addCircle(
            CircleOptions()
                .center(center)
                .radius(state.detectionRangeMeters())
                .strokeWidth(2f)
                .strokeColor(0x663B82F6)
                .fillColor(0x183B82F6)
        )
    }

    state.targets.forEach { target ->
        if (!target.hasTargetCoordinate()) return@forEach

        val pairColor = target.pairColor()
        val focused = target.id == focusedTargetId
        val targetPoint = LatLng(target.latitude, target.longitude)
        val hasPilotCoordinate = target.hasPilotCoordinate()

        if (hasPilotCoordinate) {
            val pilotPoint = LatLng(target.pilotLatitude, target.pilotLongitude)
            addPolyline(
                PolylineOptions()
                    .add(targetPoint, pilotPoint)
                    .width(if (focused) 8f else 5f)
                    .color(pairColor)
            )
        }

        val targetMarker = addMarker(
            MarkerOptions()
                .position(targetPoint)
                .title(target.name)
                .snippet("${target.serialNumber} · ${target.listStatus.label}")
                .icon(BitmapDescriptorFactory.fromBitmap(createPairMarkerBitmap("机", pairColor, focused)))
                .anchor(0.5f, 0.5f)
                .zIndex(if (focused) 2f else 1f)
        )
        targetMarker?.`object` = target.id

        if (hasPilotCoordinate) {
            val pilotPoint = LatLng(target.pilotLatitude, target.pilotLongitude)
            val pilotMarker = addMarker(
                MarkerOptions()
                    .position(pilotPoint)
                    .title("${target.pilotName} · ${target.name}")
                    .snippet("${target.pilotDistanceText} · ${target.serialNumber}")
                    .icon(BitmapDescriptorFactory.fromBitmap(createPairMarkerBitmap("飞", pairColor, focused)))
                    .anchor(0.5f, 0.5f)
                    .zIndex(if (focused) 2f else 1f)
            )
            pilotMarker?.`object` = target.id
        }
    }

    setOnMarkerClickListener { marker ->
        val targetId = marker.`object` as? String
        val target = state.targets.firstOrNull { it.id == targetId }
        if (target != null) {
            onTargetClick(target)
            true
        } else {
            false
        }
    }
}

private fun com.tji.device.product.radiodetection.model.RadioCoordinate.toLatLng(): LatLng =
    LatLng(latitude, longitude)

private fun RadioDetectionUiState.mapCenterOrDefault(config: RadioDetectionMapConfig): LatLng =
    when {
        currentCoordinate.hasUsableCoordinate() -> currentCoordinate.toLatLng()
        targets.isNotEmpty() -> LatLng(targets.first().latitude, targets.first().longitude)
        else -> LatLng(config.centerLatitude, config.centerLongitude)
    }

private fun com.tji.device.product.radiodetection.model.RadioCoordinate.hasUsableCoordinate(): Boolean =
    latitude != 0.0 || longitude != 0.0

private fun AMap.animateToTargetPair(target: RadioDetectionTarget) {
    if (!target.hasTargetCoordinate()) return

    val center = if (target.hasPilotCoordinate()) {
        LatLng(
            (target.latitude + target.pilotLatitude) / 2.0,
            (target.longitude + target.pilotLongitude) / 2.0
        )
    } else {
        LatLng(target.latitude, target.longitude)
    }
    animateCamera(CameraUpdateFactory.newLatLngZoom(center, 16.6f))
}

private fun RadioDetectionTarget.hasPilotCoordinate(): Boolean =
    RadioCoordinateTransform.isUsable(pilotLatitude, pilotLongitude)

private fun RadioDetectionTarget.hasTargetCoordinate(): Boolean =
    RadioCoordinateTransform.isUsable(latitude, longitude)

private fun RadioDetectionUiState.detectionRangeMeters(): Double =
    detectionRange.filter { it.isDigit() || it == '.' }
        .toDoubleOrNull()
        ?.let { value ->
            if (detectionRange.contains("km", ignoreCase = true)) value * 1000.0 else value
        }
        ?: 1000.0

private fun RadioDetectionTarget.pairColor(): Int = when (listStatus) {
    com.tji.device.product.radiodetection.model.RadioListStatus.Blacklist -> 0xFFE84D4F.toInt()
    com.tji.device.product.radiodetection.model.RadioListStatus.Whitelist -> 0xFF2FBF71.toInt()
    com.tji.device.product.radiodetection.model.RadioListStatus.Unknown -> 0xFFF2B31B.toInt()
}

private fun createPairMarkerBitmap(label: String, color: Int, focused: Boolean): Bitmap {
    val size = if (focused) 88 else 72
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 28f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val center = size / 2f
    if (focused) {
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            alpha = 72
            style = Paint.Style.FILL
        }
        canvas.drawCircle(center, center, 42f, halo)
    }
    val radius = if (focused) 31f else 28f
    canvas.drawCircle(center, center, radius, fill)
    canvas.drawCircle(center, center, radius, ring)
    val baseline = center - (text.descent() + text.ascent()) / 2f
    canvas.drawText(label, center, baseline, text)
    return bitmap
}

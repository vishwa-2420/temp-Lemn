package com.lemn.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices

internal sealed class LocationFetchResult {
    data class Success(val latitude: Double, val longitude: Double) : LocationFetchResult()
    object PermissionDenied : LocationFetchResult()
    object Unavailable : LocationFetchResult()
}

internal fun fetchLastLocation(
    context: Context,
    onResult: (LocationFetchResult) -> Unit
) {
    val hasFine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val hasCoarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    if (!hasFine && !hasCoarse) {
        onResult(LocationFetchResult.PermissionDenied)
        return
    }

    val client = LocationServices.getFusedLocationProviderClient(context)
    client.lastLocation
        .addOnSuccessListener { location ->
            if (location != null) {
                onResult(LocationFetchResult.Success(location.latitude, location.longitude))
            } else {
                onResult(LocationFetchResult.Unavailable)
            }
        }
        .addOnFailureListener {
            onResult(LocationFetchResult.Unavailable)
        }
}


package com.billiard.nearby

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchByTextRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Calendar
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PlacesRepository(private val placesClient: PlacesClient) {

    companion object {
        private const val TAG = "PlacesRepository"
        private const val SEARCH_RADIUS_METERS = 2000.0
        private const val MAX_RESULTS = 20
    }

    private val placeFields = listOf(
        Place.Field.ID,
        Place.Field.NAME,
        Place.Field.ADDRESS,
        Place.Field.PHONE_NUMBER,
        Place.Field.RATING,
        Place.Field.USER_RATINGS_TOTAL,
        Place.Field.CURRENT_OPENING_HOURS,
        Place.Field.LAT_LNG
    )

    /**
     * Search nearby billiard halls.
     * Throws exception on API failure so callers can show proper error messages.
     */
    suspend fun searchNearbyBilliardHalls(location: LatLng): List<BilliardHall> {
        return searchByText(location)
    }

    private fun buildRectangularBounds(location: LatLng): RectangularBounds {
        val latDelta = SEARCH_RADIUS_METERS / 111000.0
        val lngDelta = SEARCH_RADIUS_METERS / (111000.0 * Math.cos(Math.toRadians(location.latitude)))
        return RectangularBounds.newInstance(
            LatLng(location.latitude - latDelta, location.longitude - lngDelta),
            LatLng(location.latitude + latDelta, location.longitude + lngDelta)
        )
    }

    private suspend fun searchByText(location: LatLng): List<BilliardHall> =
        suspendCancellableCoroutine { cont ->
            val request = SearchByTextRequest.builder("당구장", placeFields)
                .setLocationRestriction(buildRectangularBounds(location))
                .setMaxResultCount(MAX_RESULTS)
                .build()

            placesClient.searchByText(request)
                .addOnSuccessListener { response ->
                    cont.resume(response.places.mapNotNull { mapPlaceToHall(it) })
                }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

    private fun mapPlaceToHall(place: Place): BilliardHall? {
        val latLng = place.latLng ?: return null
        val placeId = place.id ?: return null
        val name = place.name ?: return null

        val openingHoursList: List<String>? = place.currentOpeningHours?.weekdayText

        val isOpen: Boolean? = determineIsOpen(place)

        return BilliardHall(
            placeId = placeId,
            name = name,
            address = place.address ?: "주소 정보 없음",
            phoneNumber = place.phoneNumber,
            rating = place.rating,
            userRatingsTotal = place.userRatingsTotal,
            isOpen = isOpen,
            openingHours = openingHoursList,
            latitude = latLng.latitude,
            longitude = latLng.longitude
        )
    }

    private fun determineIsOpen(place: Place): Boolean? {
        return try {
            val periods = place.currentOpeningHours?.periods ?: return null
            val now = Calendar.getInstance()
            // Calendar.DAY_OF_WEEK: 1=Sunday...7=Saturday
            // Places API day: 0=Sunday...6=Saturday
            val todayNum = (now.get(Calendar.DAY_OF_WEEK) - 1)
            val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

            periods.any { period ->
                val open = period.open ?: return@any false
                val close = period.close ?: return@any false
                val openDayNum = open.day?.ordinal ?: return@any false
                val openMin = open.time.hours * 60 + open.time.minutes
                val closeDayNum = close.day?.ordinal ?: return@any false
                val closeMin = close.time.hours * 60 + close.time.minutes

                if (openDayNum == todayNum) {
                    if (closeDayNum == openDayNum) {
                        // Same-day closing
                        currentMinutes in openMin..closeMin
                    } else {
                        // Closes next day
                        currentMinutes >= openMin
                    }
                } else if (closeDayNum == todayNum) {
                    // Opened yesterday, closes today
                    currentMinutes <= closeMin
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not determine open status: ${e.message}")
            null
        }
    }
}

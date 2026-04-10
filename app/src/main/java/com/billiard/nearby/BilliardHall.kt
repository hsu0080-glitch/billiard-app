package com.billiard.nearby

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class BilliardHall(
    val placeId: String,
    val name: String,
    val address: String,
    val phoneNumber: String?,
    val rating: Double?,
    val userRatingsTotal: Int?,
    val isOpen: Boolean?,
    val openingHours: List<String>?,
    val latitude: Double,
    val longitude: Double
) : Parcelable

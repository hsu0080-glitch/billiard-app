package com.billiard.nearby

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.RatingBar
import android.widget.TextView
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.Marker

class BilliardInfoWindowAdapter(
    private val context: Context,
    private val hallMap: Map<String, BilliardHall>
) : GoogleMap.InfoWindowAdapter {

    override fun getInfoWindow(marker: Marker): View? {
        return null // Use default window frame with custom contents
    }

    override fun getInfoContents(marker: Marker): View? {
        val hall = hallMap[marker.tag as? String] ?: return null
        val view = LayoutInflater.from(context).inflate(R.layout.info_window, null)

        val tvName: TextView = view.findViewById(R.id.tvInfoName)
        val tvAddress: TextView = view.findViewById(R.id.tvInfoAddress)
        val tvStatus: TextView = view.findViewById(R.id.tvInfoStatus)
        val tvRating: TextView = view.findViewById(R.id.tvInfoRating)
        val ratingBar: RatingBar = view.findViewById(R.id.ratingBarInfo)

        tvName.text = hall.name
        tvAddress.text = hall.address

        when (hall.isOpen) {
            true -> {
                tvStatus.text = context.getString(R.string.status_open)
                tvStatus.setTextColor(context.getColor(R.color.status_open))
            }
            false -> {
                tvStatus.text = context.getString(R.string.status_closed)
                tvStatus.setTextColor(context.getColor(R.color.status_closed))
            }
            null -> {
                tvStatus.text = context.getString(R.string.status_unknown)
                tvStatus.setTextColor(context.getColor(R.color.status_unknown))
            }
        }

        if (hall.rating != null) {
            tvRating.text = String.format("%.1f", hall.rating)
            ratingBar.rating = hall.rating.toFloat()
            ratingBar.visibility = View.VISIBLE
        } else {
            tvRating.text = context.getString(R.string.no_rating)
            ratingBar.visibility = View.GONE
        }

        return view
    }
}

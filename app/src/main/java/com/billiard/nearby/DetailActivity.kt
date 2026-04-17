package com.billiard.nearby

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.billiard.nearby.databinding.ActivityDetailBinding

class DetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BILLIARD_HALL = "extra_billiard_hall"
    }

    private lateinit var binding: ActivityDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        val hall: BilliardHall? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_BILLIARD_HALL, BilliardHall::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_BILLIARD_HALL)
        }

        if (hall == null) {
            finish()
            return
        }

        displayHallDetails(hall)
    }

    private fun displayHallDetails(hall: BilliardHall) {
        supportActionBar?.title = hall.name

        // Name
        binding.tvDetailName.text = hall.name

        // Address
        binding.tvDetailAddress.text = hall.address

        // Phone number
        if (!hall.phoneNumber.isNullOrEmpty()) {
            binding.tvDetailPhone.text = hall.phoneNumber
            binding.cardPhone.visibility = View.VISIBLE
            binding.btnCall.setOnClickListener {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:${hall.phoneNumber}")
                }
                startActivity(intent)
            }
        } else {
            binding.tvDetailPhone.text = getString(R.string.no_phone)
            binding.btnCall.visibility = View.GONE
        }

        // Rating
        if (hall.rating != null) {
            binding.tvDetailRating.text = String.format("%.1f", hall.rating)
            binding.ratingBarDetail.rating = hall.rating.toFloat()
            val ratingsCount = hall.userRatingsTotal ?: 0
            binding.tvRatingsCount.text = getString(R.string.ratings_count, ratingsCount)
        } else {
            binding.tvDetailRating.text = getString(R.string.no_rating)
            binding.ratingBarDetail.visibility = View.GONE
            binding.tvRatingsCount.text = ""
        }

        // Open/Closed status
        when (hall.isOpen) {
            true -> {
                binding.tvDetailStatus.text = getString(R.string.status_open)
                binding.tvDetailStatus.setTextColor(getColor(R.color.status_open))
                binding.ivStatusIcon.setColorFilter(getColor(R.color.status_open))
            }
            false -> {
                binding.tvDetailStatus.text = getString(R.string.status_closed)
                binding.tvDetailStatus.setTextColor(getColor(R.color.status_closed))
                binding.ivStatusIcon.setColorFilter(getColor(R.color.status_closed))
            }
            null -> {
                binding.tvDetailStatus.text = getString(R.string.status_unknown)
                binding.tvDetailStatus.setTextColor(getColor(R.color.status_unknown))
                binding.ivStatusIcon.setColorFilter(getColor(R.color.status_unknown))
            }
        }

        // Opening hours
        if (!hall.openingHours.isNullOrEmpty()) {
            binding.cardHours.visibility = View.VISIBLE
            val hoursText = hall.openingHours.joinToString("\n")
            binding.tvDetailHours.text = hoursText
        } else {
            binding.cardHours.visibility = View.GONE
        }

        // Map navigation button
        binding.btnNavigate.setOnClickListener {
            val uri = Uri.parse("geo:${hall.latitude},${hall.longitude}?q=${Uri.encode(hall.name)}")
            val mapIntent = Intent(Intent.ACTION_VIEW, uri)
            mapIntent.setPackage("com.google.android.apps.maps")
            try {
                startActivity(mapIntent)
            } catch (e: ActivityNotFoundException) {
                // Fallback: open in browser
                val browserUri = Uri.parse(
                    "https://maps.google.com/?q=${hall.latitude},${hall.longitude}"
                )
                startActivity(Intent(Intent.ACTION_VIEW, browserUri))
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

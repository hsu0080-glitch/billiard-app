package com.billiard.nearby

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.billiard.nearby.databinding.ActivityMapsBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        private const val TAG = "MapsActivity"
        private const val DEFAULT_ZOOM = 14f
        // Default: Seoul city center
        private val DEFAULT_LOCATION = LatLng(37.5665, 126.9780)
    }

    private lateinit var binding: ActivityMapsBinding
    private lateinit var map: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var placesClient: PlacesClient
    private lateinit var repository: PlacesRepository

    private val hallMap = mutableMapOf<String, BilliardHall>()
    private var currentLocation: LatLng? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.app_name)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialize Places SDK
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, BuildConfig.MAPS_API_KEY)
        }
        placesClient = Places.createClient(this)
        repository = PlacesRepository(placesClient)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        binding.fabMyLocation.setOnClickListener {
            currentLocation?.let { loc ->
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(loc, DEFAULT_ZOOM))
            } ?: run {
                Toast.makeText(this, getString(R.string.locating), Toast.LENGTH_SHORT).show()
            }
        }

        binding.fabRefresh.setOnClickListener {
            currentLocation?.let { loc ->
                searchBilliardHalls(loc)
            } ?: run {
                Toast.makeText(this, getString(R.string.location_not_found), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        map.uiSettings.apply {
            isZoomControlsEnabled = true
            isCompassEnabled = true
            isMyLocationButtonEnabled = false // Using custom FAB
        }

        enableMyLocationLayer()
        getDeviceLocation()

        map.setInfoWindowAdapter(BilliardInfoWindowAdapter(this, hallMap))

        map.setOnInfoWindowClickListener { marker ->
            openDetailActivity(marker)
        }

        map.setOnMarkerClickListener { marker ->
            marker.showInfoWindow()
            true
        }
    }

    private fun enableMyLocationLayer() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            map.isMyLocationEnabled = true
        }
    }

    private fun getDeviceLocation() {
        showLoading(true)
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            showLoading(false)
            useDefaultLocation()
            return
        }

        lifecycleScope.launch {
            try {
                val cancellationTokenSource = CancellationTokenSource()
                val location: Location? = fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancellationTokenSource.token
                ).await()

                if (location != null) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    currentLocation = latLng
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_ZOOM))
                    searchBilliardHalls(latLng)
                } else {
                    // Try last known location
                    val lastLocation = fusedLocationClient.lastLocation.await()
                    if (lastLocation != null) {
                        val latLng = LatLng(lastLocation.latitude, lastLocation.longitude)
                        currentLocation = latLng
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_ZOOM))
                        searchBilliardHalls(latLng)
                    } else {
                        useDefaultLocation()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting location: ${e.message}")
                showLoading(false)
                Toast.makeText(this@MapsActivity, getString(R.string.location_error), Toast.LENGTH_SHORT).show()
                useDefaultLocation()
            }
        }
    }

    private fun useDefaultLocation() {
        currentLocation = DEFAULT_LOCATION
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_LOCATION, DEFAULT_ZOOM))
        Toast.makeText(this, getString(R.string.using_default_location), Toast.LENGTH_SHORT).show()
        searchBilliardHalls(DEFAULT_LOCATION)
    }

    private fun searchBilliardHalls(location: LatLng) {
        showLoading(true)
        clearMarkers()

        lifecycleScope.launch {
            try {
                val halls = repository.searchNearbyBilliardHalls(location)
                showLoading(false)

                if (halls.isEmpty()) {
                    Toast.makeText(
                        this@MapsActivity,
                        getString(R.string.no_billiard_halls),
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                halls.forEach { hall ->
                    addMarkerForHall(hall)
                }

                binding.tvResultCount.text = getString(R.string.result_count, halls.size)
                binding.tvResultCount.visibility = View.VISIBLE

                try {
                    val boundsBuilder = LatLngBounds.Builder()
                    halls.forEach { boundsBuilder.include(LatLng(it.latitude, it.longitude)) }
                    currentLocation?.let { boundsBuilder.include(it) }
                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 150))
                } catch (e: Exception) {
                    Log.e(TAG, "Camera bounds failed: ${e.message}")
                    currentLocation?.let {
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(it, DEFAULT_ZOOM))
                    }
                }

            } catch (e: Exception) {
                showLoading(false)
                Log.e(TAG, "Search failed: ${e.message}")
                Toast.makeText(
                    this@MapsActivity,
                    getString(R.string.search_error),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun addMarkerForHall(hall: BilliardHall) {
        val position = LatLng(hall.latitude, hall.longitude)

        val markerOptions = MarkerOptions()
            .position(position)
            .title(hall.name)
            .snippet(hall.address)
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))

        val marker: Marker = map.addMarker(markerOptions) ?: return
        marker.tag = hall.placeId
        hallMap[hall.placeId] = hall
    }

    private fun clearMarkers() {
        map.clear()
        hallMap.clear()
        binding.tvResultCount.visibility = View.GONE
    }

    private fun openDetailActivity(marker: Marker) {
        val placeId = marker.tag as? String ?: return
        val hall = hallMap[placeId] ?: return

        val intent = Intent(this, DetailActivity::class.java).apply {
            putExtra(DetailActivity.EXTRA_BILLIARD_HALL, hall)
        }
        startActivity(intent)
    }

    private fun showLoading(isLoading: Boolean) {
        binding.layoutLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
    }
}

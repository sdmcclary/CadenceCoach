package com.example.cc.ui.ride

import com.example.cc.R
import android.Manifest
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.cc.databinding.FragmentRideBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RideFragment : Fragment() {

    private var _binding: FragmentRideBinding? = null
    private val binding get() = _binding!!

    private var isTracking = false
    private var startTime: Long = 0L
    private var elapsedTime: Long = 0L
    private val rideDataList = mutableListOf<RideData>()
    private var totalSpeed = 0f
    private var frontGear = 1
    private var rearGear = 1

    private lateinit var locationManager: LocationManager
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (isTracking) {
                val speedMph = location.speed * 2.237f
                val currentTime = System.currentTimeMillis()

                val calculatedCadence = calculateCadence(location.speed)
                rideDataList.add(
                    RideData(
                        timestamp = currentTime,
                        speed = speedMph,
                        cadence = calculatedCadence,
                        frontGear = frontGear,
                        rearGear = rearGear
                    )
                )

                totalSpeed += speedMph

                binding.speedTextView.text = getString(R.string.ride_speed, speedMph)
                binding.cadenceTextView.text = getString(R.string.ride_cadence, calculatedCadence)
                binding.frontGearTextView.text = getString(R.string.front_gear, frontGear)
                binding.rearGearTextView.text = getString(R.string.rear_gear, rearGear)

            }
        }


        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRideBinding.inflate(inflater, container, false)

        locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager

        binding.startButton.setOnClickListener { checkLocationPermissionAndStartRide() }
        binding.stopButton.setOnClickListener { stopRide() }
        binding.frontGearUpButton.setOnClickListener { frontGear++ }
        binding.frontGearDownButton.setOnClickListener {
            if (frontGear > 1) frontGear--
        }

        binding.rearGearUpButton.setOnClickListener {
            rearGear++
        }

        binding.rearGearDownButton.setOnClickListener {
            if (rearGear > 1) rearGear--
        }

        return binding.root
    }

    private fun checkLocationPermissionAndStartRide() {
        when {
            requireContext().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED -> {
                startRide()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                Toast.makeText(
                    requireContext(),
                    "Location permission is required to track rides.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            else -> {
                requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    100
                )
            }
        }
    }

    private fun startRide() {
        binding.startButton.visibility = View.INVISIBLE
        binding.stopButton.visibility = View.VISIBLE

        isTracking = true
        startTime = System.currentTimeMillis()

        startTimer()
        startSpeedTracking()
    }

    private fun stopRide() {
        isTracking = false
        elapsedTime = System.currentTimeMillis() - startTime

        stopSpeedTracking()

        binding.startButton.visibility = View.VISIBLE
        binding.stopButton.visibility = View.INVISIBLE

        val avgSpeed = if (rideDataList.isNotEmpty()) {
            rideDataList.sumOf { it.speed.toDouble() }.toFloat() / rideDataList.size
        } else 0f

        val avgCadence = if (rideDataList.isNotEmpty()) {
            rideDataList.sumOf { it.cadence } / rideDataList.size
        } else 0f
        binding.speedTextView.text = getString(R.string.ride_avg_speed, avgSpeed)
        binding.cadenceTextView.text = getString(R.string.ride_avg_cadence, avgCadence)

        binding.timerTextView.text = getString(R.string.ride_duration, formatTime(elapsedTime))

        exportRideData(avgSpeed)
    }

    private fun startTimer() {
        lifecycleScope.launch {
            while (isTracking) {
                val currentTime = System.currentTimeMillis() - startTime
                binding.timerTextView.text = formatTime(currentTime)
                delay(1000L)
            }
        }
    }

    private fun startSpeedTracking() {
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                1f,
                locationListener
            )
        } catch (e: SecurityException) {
            Toast.makeText(requireContext(), "Location permission not granted.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopSpeedTracking() {
        locationManager.removeUpdates(locationListener)
    }


    private fun calculateCadence(speed: Float): Int {
        return if (frontGear > 0 && rearGear > 0) {
            (speed * 39.37 / 85.75 * 60 * (rearGear / frontGear)).toInt()
        } else {
            0
        }
    }

    private fun formatTime(milliseconds: Long): String {
        val seconds = (milliseconds / 1000) % 60
        val minutes = (milliseconds / (1000 * 60)) % 60
        val hours = (milliseconds / (1000 * 60 * 60)) % 24
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }

    private fun exportRideData(avgSpeed: Float) {
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val startTimeFormatted = dateFormatter.format(Date(startTime))
        val durationFormatted = formatTime(elapsedTime)

        val rideDataFormatted = rideDataList.joinToString("\n") { data ->
            val formattedTime = dateFormatter.format(Date(data.timestamp))
            "Time: $formattedTime, Speed: ${"%.2f".format(data.speed)} mph, Cadence: ${data.cadence} RPM, Front Gear: ${data.frontGear}, Rear Gear: ${data.rearGear}"
        }

        val rideData = """
        Start Time: $startTimeFormatted
        Duration: $durationFormatted
        Avg Speed: ${"%.2f".format(avgSpeed)} mph
        Ride Data:
        $rideDataFormatted
    """.trimIndent()

        sendRideDataByEmail(rideData)
        resetRideVariables()
    }


    private fun resetRideVariables() {
        isTracking = false
        startTime = 0L
        elapsedTime = 0L
        rideDataList.clear()
        totalSpeed = 0f
    }

    private fun sendRideDataByEmail(rideData: String) {
        val emailIntent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf("sdmcclary@wm.edu"))
            putExtra(Intent.EXTRA_SUBJECT, "Ride Data Export")
            putExtra(Intent.EXTRA_TEXT, rideData)
        }

        // Ensure an email app is available
        try {
            startActivity(Intent.createChooser(emailIntent, "Send Ride Data via:"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "No email app installed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

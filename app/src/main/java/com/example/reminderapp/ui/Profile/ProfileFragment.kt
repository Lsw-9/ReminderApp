package com.example.reminderapp.ui.Profile

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.reminderapp.R
import com.example.reminderapp.databinding.FragmentProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID
import kotlinx.coroutines.delay
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

import android.Manifest
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Timer
import java.util.TimerTask
import com.google.android.gms.location.LocationRequest

import android.widget.FrameLayout

import android.app.AlertDialog
import android.content.Context
import android.location.LocationManager

import android.os.Looper
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.storage.StorageReference
import kotlin.random.Random
import android.provider.Settings
import com.example.reminderapp.WeatherCode
import com.example.reminderapp.WeatherType
import com.example.reminderapp.WeatherResponse
import com.example.reminderapp.WeatherService

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null

    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var userRef: DocumentReference
    private lateinit var storage: FirebaseStorage

    private var imageUri: Uri? = null

    // Weather animation properties
    private var currentWeatherAnimation: WeatherType = WeatherType.SUNNY
    private lateinit var sunAnimation: Animation
    private lateinit var windAnimation: Animation
    private lateinit var moonAnimation: Animation
    private lateinit var cloudAnimation: Animation

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var weatherService: WeatherService
    private var weatherUpdateTimer: Timer? = null



    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        loadUserData()

        // Initialize weather animations
        setupWeatherAnimations()

        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        // Initialize weather service with Open-Meteo API
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        weatherService = retrofit.create(WeatherService::class.java)

        requestLocationPermission()

        binding.profileImage.setOnClickListener {
            Log.d("ProfileFragment", "Profile image clicked")
            showImageOptions()
        }
    }


    private fun loadUserData() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            safeShowToast("Please log in first")
            return
        }

        val userRef = firestore.collection("users").document(currentUser.uid)
        userRef.addSnapshotListener { snapshot, error ->
            if (!isAdded) return@addSnapshotListener

            if (error != null) {
                if (error.message?.contains("PERMISSION_DENIED") == true) {
                    return@addSnapshotListener
                }
                safeShowToast("Failed to load user data")
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val username = snapshot.getString("username") ?: ""
                val email = snapshot.getString("email") ?: ""
                val profileImageUrl = snapshot.getString("profileImageUrl")

                if (!isAdded || _binding == null) return@addSnapshotListener

                binding.tvUsername.text = username
                binding.tvEmail.text = email
                binding.tvPassword.text = "••••••••"

                if (!profileImageUrl.isNullOrEmpty()) {
                    Glide.with(this)
                        .load(profileImageUrl)
                        .centerCrop()
                        .placeholder(R.drawable.icons_person_100)
                        .error(R.drawable.icons_person_100)
                        .into(binding.profileImage)
                } else {
                    binding.profileImage.setImageResource(R.drawable.icons_person_100)
                }
            } else {
                safeShowToast("No user data found")
            }
        }
    }

    private fun setupWeatherAnimations() {
        // Load animations
        sunAnimation = AnimationUtils.loadAnimation(requireContext(), R.anim.sun_rotate)
        windAnimation = AnimationUtils.loadAnimation(requireContext(), R.anim.wind_animation)
        moonAnimation = AnimationUtils.loadAnimation(requireContext(), R.anim.moon_glow)
        cloudAnimation = AnimationUtils.loadAnimation(requireContext(), R.anim.cloud_float)

        // Make animations infinite
        sunAnimation.repeatCount = Animation.INFINITE
        windAnimation.repeatCount = Animation.INFINITE
        moonAnimation.repeatCount = Animation.INFINITE
        cloudAnimation.repeatCount = Animation.INFINITE

        // Initialize containers with proper layout parameters
        binding.rainContainer.apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.HORIZONTAL
        }

        binding.snowContainer.apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.HORIZONTAL
        }

        // Initialize rain and snow views
        setupRaindrops()
        setupSnowflakes()

        // Initially hide all weather views
        binding.sunImage.visibility = View.GONE
        binding.moonImage.visibility = View.GONE
        binding.windImage.visibility = View.GONE
        binding.cloudImage.visibility = View.GONE
        binding.rainContainer.visibility = View.GONE
        binding.snowContainer.visibility = View.GONE
    }

    private fun setupRaindrops() {
        binding.rainContainer.removeAllViews()
        repeat(20) { // Create 20 raindrops
            val raindrop = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(8, 40).apply {
                    marginStart = (Random.nextInt(20) * 20) // Random horizontal spacing
                    topMargin = Random.nextInt(100) // Random vertical start position
                }
                setBackgroundColor(Color.WHITE)
                alpha = 0.6f
            }
            binding.rainContainer.addView(raindrop)
        }
    }

    private fun setupSnowflakes() {
        binding.snowContainer.removeAllViews()
        repeat(15) { // Create 15 snowflakes
            val snowflake = TextView(context).apply {
                text = "❄"
                textSize = 20f
                setTextColor(Color.WHITE)
                alpha = 0.8f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = (Random.nextInt(20) * 20) // Random horizontal spacing
                    topMargin = Random.nextInt(100) // Random vertical start position
                }
            }
            binding.snowContainer.addView(snowflake)
        }
    }


    private fun showWeather(weatherType: WeatherType) {
        currentWeatherAnimation = weatherType

        // Hide all weather views first
        binding.sunImage.clearAnimation()
        binding.windImage.clearAnimation()
        binding.moonImage.clearAnimation()
        binding.cloudImage.clearAnimation()
        binding.sunImage.visibility = View.GONE
        binding.moonImage.visibility = View.GONE
        binding.cloudImage.visibility = View.GONE
        binding.windImage.visibility = View.GONE
        binding.rainContainer.visibility = View.GONE
        binding.snowContainer.visibility = View.GONE

        when (weatherType) {
            WeatherType.SUNNY -> {
                binding.sunImage.visibility = View.VISIBLE
                binding.sunImage.startAnimation(sunAnimation)
            }
            WeatherType.CLEAR_NIGHT -> {
                binding.moonImage.visibility = View.VISIBLE
                binding.moonImage.startAnimation(moonAnimation)
            }
            WeatherType.PARTLY_CLOUDY_DAY -> {
                binding.sunImage.visibility = View.VISIBLE
                binding.cloudImage.visibility = View.VISIBLE
                binding.sunImage.startAnimation(sunAnimation)
                binding.cloudImage.startAnimation(cloudAnimation)
            }
            WeatherType.PARTLY_CLOUDY_NIGHT -> {
                binding.moonImage.visibility = View.VISIBLE
                binding.cloudImage.visibility = View.VISIBLE
                binding.moonImage.startAnimation(moonAnimation)
                binding.cloudImage.startAnimation(cloudAnimation)
            }
            WeatherType.CLOUDY -> {
                binding.cloudImage.visibility = View.VISIBLE
                binding.cloudImage.startAnimation(cloudAnimation)
            }
            WeatherType.WINDY -> {
                binding.windImage.visibility = View.VISIBLE
                binding.windImage.startAnimation(windAnimation)
            }
            WeatherType.RAINY -> {
                binding.cloudImage.visibility = View.VISIBLE
                binding.rainContainer.visibility = View.VISIBLE
                binding.cloudImage.startAnimation(cloudAnimation)
                animateRain()
            }
            WeatherType.SNOWY -> {
                binding.cloudImage.visibility = View.VISIBLE
                binding.snowContainer.visibility = View.VISIBLE
                binding.cloudImage.startAnimation(cloudAnimation)
                animateSnow()
            }
        }
    }

    private fun animateRain() {
        binding.rainContainer.children.forEachIndexed { index, view ->
            view.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.rain_drop).apply {
                startOffset = (index * 100).toLong()
            })
        }
    }

    private fun animateSnow() {
        binding.snowContainer.children.forEachIndexed { index, view ->
            view.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.snow_fall).apply {
                startOffset = (index * 200).toLong()
            })
        }
    }



    private fun showImageOptions() {
        val options = arrayOf("Choose from Gallery", "Remove Profile Picture")

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Profile Picture Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkPermissionAndPickImage()
                    1 -> removeProfilePicture()
                }
            }
            .show()
    }

    private fun checkPermissionAndPickImage() {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED) {
            safeShowToast("Permission already granted, opening gallery")
            openGallery()
        } else {
            safeShowToast("Requesting permission")
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(permission), 100)
        }
    }


    private fun removeProfilePicture() {
        val currentUser = auth.currentUser ?: return

        val userRef = firestore.collection("users").document(currentUser.uid)
        userRef.update("profileImageUrl", "")
            .addOnSuccessListener {
                // Reset to default image
                binding.profileImage.setImageResource(R.drawable.icons_person_100)
                safeShowToast("Profile picture removed")
            }
            .addOnFailureListener {
                safeShowToast("Failed to remove profile picture")
            }
    }

    private fun openGallery() {
        Log.d("ProfileFragment", "Opening gallery")
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            imageUri = result.data!!.data

            Glide.with(this)
                .load(imageUri)
                .centerCrop()
                .placeholder(R.drawable.icons_person_100)
                .error(R.drawable.icons_person_100)
                .into(binding.profileImage)

            uploadImageToFirebase()
        } else {
            safeShowToast("Image selection failed")
        }
    }

    private fun uploadImageToFirebase() {
        val currentUser = auth.currentUser
        if (currentUser == null || imageUri == null) return

        val fileName = "profile_images/${UUID.randomUUID()}.jpg"
        val storageRef = storage.reference.child(fileName)

        storageRef.putFile(imageUri!!)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    updateProfileImageUri(uri.toString())
                }
            }
            .addOnFailureListener {
                safeShowToast("Image upload failed")
            }
    }

    private fun updateProfileImageUri(imageUrl: String) {
        val currentUser = auth.currentUser
        if (currentUser == null) return

        val userRef = firestore.collection("users").document(currentUser.uid)
        userRef.update("profileImageUrl", imageUrl)
            .addOnSuccessListener {
                safeShowToast("Profile updated successfully")
            }
            .addOnFailureListener {
                safeShowToast("Failed to update profile")
            }
    }

    private fun requestLocationPermission() {
        when {
            // Check if permission is already granted
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                Log.d("LocationPermission", "Permission already granted")
                startLocationUpdates()
            }
            // Should show permission rationale
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                Log.d("LocationPermission", "Showing permission rationale")
                AlertDialog.Builder(requireContext())
                    .setTitle("Location Permission Required")
                    .setMessage("We need location permission to show you local weather information.")
                    .setPositiveButton("Grant") { _, _ ->
                        requestPermissions(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ),
                            LOCATION_PERMISSION_REQUEST_CODE
                        )
                    }
                    .setNegativeButton("Deny") { dialog, _ ->
                        dialog.dismiss()
                        showLocationRequiredMessage()
                    }
                    .show()
            }
            else -> {
                // Request the permission directly
                Log.d("LocationPermission", "Requesting permission")
                requestPermissions(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ),
                    LOCATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    private fun showLocationRequiredMessage() {
        safeShowToast("Location permission is required for weather updates")
    }

    private fun startLocationUpdates() {
        try {
            // Check if GPS is enabled
            val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                showEnableGPSDialog()
                return
            }

            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            // Request high accuracy location updates
            val locationRequest = LocationRequest.create().apply {
                priority = Priority.PRIORITY_HIGH_ACCURACY
                interval = 300000 // 5 minutes
                fastestInterval = 60000 // 1 minute
            }

            // Create a location callback that checks if fragment is still attached
            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    // Check if fragment is still attached before processing location
                    if (!isAdded || _binding == null) {
                        Log.d("LocationUpdate", "Fragment not attached or binding is null, ignoring location update")
                        return
                    }

                    locationResult.lastLocation?.let { location ->
                        Log.d("LocationUpdate", "New location received: ${location.latitude}, ${location.longitude}")
                        updateWeatherWithLocation(location)
                    }
                }
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )

            // Get last known location immediately
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    // Check if fragment is still attached before processing location
                    if (!isAdded || _binding == null) {
                        Log.d("LocationUpdate", "Fragment not attached or binding is null, ignoring last location")
                        return@addOnSuccessListener
                    }

                    if (location != null) {
                        Log.d("LocationUpdate", "Last known location: ${location.latitude}, ${location.longitude}")
                        updateWeatherWithLocation(location)
                    } else {
                        Log.d("LocationUpdate", "Last known location is null, requesting current location")
                        showLocationLoadingUI()
                    }
                }
                .addOnFailureListener { e ->
                    // Check if fragment is still attached before showing error
                    if (!isAdded || _binding == null) return@addOnFailureListener

                    Log.e("LocationUpdate", "Error getting last location", e)
                    showLocationError("Unable to get location: ${e.localizedMessage}")
                }

        } catch (e: Exception) {
            // Check if fragment is still attached before showing error
            if (!isAdded || _binding == null) return

            Log.e("LocationUpdate", "Error starting location updates", e)
            showLocationError("Error starting location updates: ${e.localizedMessage}")
        }
    }

    private fun showEnableGPSDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Enable GPS")
            .setMessage("GPS is required for weather updates. Would you like to enable it?")
            .setPositiveButton("Yes") { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
                showLocationError("GPS is required for weather updates")
            }
            .show()
    }

    private fun showLocationLoadingUI() {
        // Check if binding is available before accessing views
        if (_binding == null) {
            Log.d("LocationUpdate", "Binding is null, can't show loading UI")
            return
        }
        binding.apply {
            weatherLoadingProgress.visibility = View.VISIBLE
            weatherCard.alpha = 0.5f
            temperatureText.text = "Loading..."
            weatherDescriptionText.text = "Fetching location..."
            weatherInfoText.text = "Please wait"
        }
    }

    private fun hideLocationLoadingUI() {
        // Check if binding is available before accessing views
        if (_binding == null) {
            Log.d("LocationUpdate", "Binding is null, can't hide loading UI")
            return
        }
        binding.apply {
            weatherLoadingProgress.visibility = View.GONE
            weatherCard.alpha = 1.0f
        }
    }

    private fun showLocationError(message: String) {
        // Check if fragment is still attached and binding is available
        if (!isAdded || _binding == null) {
            Log.d("LocationUpdate", "Fragment not attached or binding is null, can't show error")
            return
        }

        try {
            binding.apply {
                weatherLoadingProgress.visibility = View.GONE
                weatherCard.alpha = 1.0f
                temperatureText.text = "--°C"
                weatherDescriptionText.text = "Location unavailable"
                weatherInfoText.text = message
            }

            safeShowToast(message)
        } catch (e: Exception) {
            Log.e("LocationUpdate", "Error showing location error: ${e.message}")
        }
    }

    private fun updateWeatherWithLocation(location: Location) {
        // Check if binding is available before showing loading UI
        if (_binding == null) {
            Log.d("LocationUpdate", "Binding is null, can't update weather")
            return
        }
        showLocationLoadingUI()
        lifecycleScope.launch {
            try {
                val response = weatherService.getCurrentWeather(
                    latitude = location.latitude,
                    longitude = location.longitude
                )
                logWeatherResponse(response)

                // Check if fragment is still attached before updating UI
                if (!isAdded || _binding == null) {
                    Log.d("WeatherUpdate", "Fragment not attached or binding is null, can't update UI")
                    return@launch
                }

                requireActivity().runOnUiThread {
                    hideLocationLoadingUI()
                    updateWeatherUI(response)
                }
            } catch (e: Exception) {
                Log.e("WeatherUpdate", "Error fetching weather", e)

                // Check if fragment is still attached before showing error
                if (!isAdded || _binding == null) {
                    Log.d("WeatherUpdate", "Fragment not attached or binding is null, can't show error")
                    return@launch
                }

                requireActivity().runOnUiThread {
                    hideLocationLoadingUI()
                    showLocationError("Failed to update weather: ${e.message}")
                }
            }
        }
    }

    private fun logWeatherResponse(response: WeatherResponse) {
        try {
            Log.d("WeatherUpdate", "Weather data received:")
            Log.d("WeatherUpdate", "  Temperature: ${response.current.temperature_2m}°C")
            Log.d("WeatherUpdate", "  Humidity: ${response.current.relative_humidity_2m}%")
            Log.d("WeatherUpdate", "  Weather Code: ${response.current.weather_code}")
            Log.d("WeatherUpdate", "  Is Day: ${response.current.is_day}")
            Log.d("WeatherUpdate", "  Time: ${response.current.time}")
        } catch (e: Exception) {
            Log.e("WeatherUpdate", "Error logging weather data", e)
        }
    }

    private fun updateWeatherUI(weatherResponse: WeatherResponse) {
        try {
            val weatherCode = weatherResponse.current.weather_code
            val isDay = weatherResponse.current.is_day
            Log.d("WeatherUpdate", "Raw weather data - Code: $weatherCode, IsDay: $isDay")
            val weatherType = WeatherCode.getWeatherType(weatherCode, isDay)
            val description = WeatherCode.getDescription(weatherCode, isDay)

            Log.d("WeatherUpdate", "Updating UI - Code: $weatherCode, IsDay: $isDay, Type: $weatherType, Desc: $description")

            // Update text views with animation
            binding.apply {
                // Make weather card visible with fade in animation if not already visible
                if (weatherCard.visibility != View.VISIBLE) {
                    weatherCard.alpha = 0f
                    weatherCard.visibility = View.VISIBLE
                    weatherCard.animate().alpha(1f).setDuration(500).start()
                }

                temperatureText.text = "${weatherResponse.current.temperature_2m.toInt()}°C"
                weatherDescriptionText.text = description
                weatherInfoText.text = "Humidity: ${weatherResponse.current.relative_humidity_2m}%"

                // Ensure animation container is visible
                weatherAnimationContainer.visibility = View.VISIBLE
            }

            // Show weather animation
            showWeather(weatherType)

        } catch (e: Exception) {
            Log.e("WeatherUpdate", "Error updating weather UI", e)
            showLocationError("Error updating weather display")
        }
    }



    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("LocationPermission", "Permission granted")
                    startLocationUpdates()
                } else {
                    Log.d("LocationPermission", "Permission denied")
                    showLocationRequiredMessage()
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        weatherUpdateTimer?.cancel()
        weatherUpdateTimer = null

        try {
            fusedLocationClient.removeLocationUpdates(object : LocationCallback() {})
        } catch (e: Exception) {
            Log.e("LocationUpdate", "Error removing location updates", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            fusedLocationClient.removeLocationUpdates(object : LocationCallback() {})
        } catch (e: Exception) {
            Log.e("LocationUpdate", "Error removing location updates", e)
        }
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
        } catch (e: Exception) {
            Log.e("ProfileFragment", "Error in onDestroy", e)
        }
    }

    private fun safeShowToast(message: String) {
        if (!isAdded || activity == null || activity?.isFinishing == true) {
            Log.d("ProfileFragment", "Can't show toast - fragment not attached or activity finishing: $message")
            return
        }

        try {
            // Use activity context and run on main thread
            activity?.runOnUiThread {
                if (!isAdded || activity == null || activity?.isFinishing == true) return@runOnUiThread
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("ProfileFragment", "Error showing toast: ${e.message}")
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }
}
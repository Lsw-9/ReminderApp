package com.example.reminderapp


import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import com.example.reminderapp.databinding.ActivityMainBinding
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.qamar.curvedbottomnaviagtion.CurvedBottomNavigation
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import com.example.reminderapp.ui.Calendar.CalendarFragment
import com.example.reminderapp.ui.home.HomeFragment
import androidx.appcompat.widget.SearchView
import com.example.reminderapp.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {
    private lateinit var fab: FloatingActionButton
    private lateinit var viewModel: SharedViewModel
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    private lateinit var searchView: SearchView
    private var currentFragment: Fragment? = null

    // Add property to store animator reference
    private var rippleAnimator: ValueAnimator? = null

    // Add property for MediaPlayer
    private var mediaPlayer: MediaPlayer? = null

    // Flag to track if navigation items have been added
    private var navigationItemsAdded = false

    private var lastOfflineState = false


    val createReminderLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val reminderId = result.data?.getStringExtra("REMINDER_ID")
            reminderId?.let { id ->
                val navHostFragment = supportFragmentManager
                    .findFragmentById(R.id.nav_host_fragment_content_main) as? NavHostFragment
                val currentFragment = navHostFragment?.childFragmentManager?.fragments?.firstOrNull()

                (currentFragment as? CalendarFragment)?.highlightReminder(id)
            }
        }
    }




    fun launchCreateReminder(intent: Intent) {
        createReminderLauncher.launch(intent)
    }

    private fun openCreateReminder() {
        val intent = Intent(this, CreateReminder::class.java)
        viewModel.selectedDate.value?.let { date ->
            intent.putExtra(CreateReminder.EXTRA_SELECTED_DATE, date.toString())
        }
        createReminderLauncher.launch(intent)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup View Binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup Toolbar
        setSupportActionBar(binding.appBarMain.toolbar)

        // Initialize Navigation Components FIRST
        setupNavigation()

        // Initialize ViewModel
        viewModel = ViewModelProvider(this).get(SharedViewModel::class.java)

        // Setup FAB and animation
        setupFabAndAnimation()

        // Setup Bottom Navigation
        setupBottomNavigation()


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                PERMISSION_REQUEST_CODE
            )
        }

        // Initialize MediaPlayer
        initializeMediaPlayer()
    }



    private fun setupNavigation() {
        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView

        // Initialize navController
        navController = findNavController(R.id.nav_host_fragment_content_main)

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home, R.id.nav_calendar, R.id.nav_profile
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        // Listen for navigation changes to update menu
        navController.addOnDestinationChangedListener { _, destination, _ ->
            // Clear existing menu
            invalidateOptionsMenu()

            // Show/hide FAB based on destination
            when (destination.id) {
                R.id.nav_profile -> {
                    binding.appBarMain.fab.hide()
                    binding.appBarMain.expandingBackground.visibility = View.GONE
                }
                else -> {
                    binding.appBarMain.fab.show()
                    binding.appBarMain.expandingBackground.visibility = View.VISIBLE
                }
            }

            supportActionBar?.title = destination.label
        }

        // Setup Navigation Drawer listener
        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_logout -> {
                    showLogoutConfirmationDialog()
                    true
                }
                R.id.nav_about -> {
                    AboutDialogHelper().showAboutDialog(this)
                    true
                }
                else -> {
                    menuItem.isChecked = true
                    drawerLayout.closeDrawers()
                    navController.navigate(menuItem.itemId)
                    true
                }
            }
        }
    }

    private fun setupFabAndAnimation() {
        try {
            val expandingBackground = findViewById<View>(R.id.expandingBackground)
            val fab = findViewById<FloatingActionButton>(R.id.fab)

            rippleAnimator?.cancel()

            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 2000
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                interpolator = LinearInterpolator()

                addUpdateListener { animation ->
                    val value = animation.animatedValue as Float
                    expandingBackground.scaleX = 1f + 0.8f * value
                    expandingBackground.scaleY = 1f + 0.8f * value
                    expandingBackground.alpha = 0.5f * (1f - value)
                }
            }

            animator.start()

            this.rippleAnimator = animator

            fab.setOnClickListener {
                openCreateReminder()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in setupFabAndAnimation", e)
        }
    }

    private fun setupBottomNavigation() {
        try {
            val bottomNavigation = findViewById<CurvedBottomNavigation>(R.id.bottomNavigation)

            // Add navigation items only if they haven't been added yet
            if (!navigationItemsAdded) {
                bottomNavigation.add(
                    CurvedBottomNavigation.Model(R.id.nav_home, "Home", R.drawable.baseline_home_24)
                )
                bottomNavigation.add(
                    CurvedBottomNavigation.Model(R.id.nav_calendar, "Calendar", R.drawable.baseline_calendar_month_24)
                )
                bottomNavigation.add(
                    CurvedBottomNavigation.Model(R.id.nav_profile, "Profile", R.drawable.baseline_person_24)
                )
                navigationItemsAdded = true
            }

            // Set up click listener
            bottomNavigation.setOnClickMenuListener { menuItem ->
                // Close search view if it's open
                if (::searchView.isInitialized && !searchView.isIconified) {
                    searchView.isIconified = true
                }

                when (menuItem.id) {
                    R.id.nav_home -> {
                        navController.navigate(R.id.nav_home)
                        true
                    }
                    R.id.nav_calendar -> {
                        navController.navigate(R.id.nav_calendar)
                        true
                    }
                    R.id.nav_profile -> {
                        navController.navigate(R.id.nav_profile)
                        true
                    }
                    else -> false
                }
            }

            // Set up destination change listener to sync bottom nav with navigation
            navController.addOnDestinationChangedListener { _, destination, _ ->
                when (destination.id) {
                    R.id.nav_home -> bottomNavigation.show(R.id.nav_home)
                    R.id.nav_calendar -> bottomNavigation.show(R.id.nav_calendar)
                    R.id.nav_profile -> bottomNavigation.show(R.id.nav_profile)
                }
            }

            // Show home by default
            bottomNavigation.show(R.id.nav_home)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in setupBottomNavigation", e)
        }
    }




    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        when (navController.currentDestination?.id) {
            R.id.nav_home, R.id.nav_calendar -> {
                menuInflater.inflate(R.menu.main, menu)

                val searchItem = menu.findItem(R.id.action_search)
                searchView = searchItem.actionView as SearchView

                setupSearchView(searchItem)
            }
            R.id.nav_profile -> {
                menuInflater.inflate(R.menu.profile_menu, menu)
            }
        }
        return true
    }

    private fun setupSearchView(searchItem: MenuItem) {
        try {
            searchView.apply {
                maxWidth = Integer.MAX_VALUE
                queryHint = "Search reminders..."

                // Set up back button
                val closeButton = findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)
                closeButton?.setOnClickListener {
                    if (query.isEmpty()) {
                        searchItem.collapseActionView()
                    } else {
                        setQuery("", false)
                    }
                }

                setOnCloseListener {
                    // Ensure bottom navigation is visible and working when search is closed
                    findViewById<CurvedBottomNavigation>(R.id.bottomNavigation)?.visibility = View.VISIBLE
                    false
                }
            }

            searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                    // Hide bottom navigation when search is expanded
                    val bottomNavigation = findViewById<CurvedBottomNavigation>(R.id.bottomNavigation)
                    bottomNavigation?.visibility = View.GONE

                    // Get current fragment
                    currentFragment = supportFragmentManager
                        .findFragmentById(R.id.nav_host_fragment_content_main)
                        ?.childFragmentManager
                        ?.fragments
                        ?.firstOrNull()

                    when (currentFragment) {
                        is SearchableFragment -> (currentFragment as SearchableFragment).showSearchUI()
                    }
                    return true
                }

                override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                    // Show bottom navigation when search is collapsed
                    val bottomNavigation = findViewById<CurvedBottomNavigation>(R.id.bottomNavigation)
                    bottomNavigation?.visibility = View.VISIBLE

                    // Reset current fragment state
                    when (currentFragment) {
                        is SearchableFragment -> {
                            (currentFragment as SearchableFragment).hideSearchUI()
                            // Restore the current navigation state
                            val currentDestination = navController.currentDestination?.id ?: R.id.nav_home
                            bottomNavigation?.show(currentDestination)
                        }
                    }
                    return true
                }
            })

            // Set up search functionality
            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    return false
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    when (currentFragment) {
                        is SearchableFragment -> (currentFragment as SearchableFragment).searchReminders(newText ?: "")
                    }
                    return true
                }
            })
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in setupSearchView", e)
        }
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search -> {
                Snackbar.make(
                    binding.root,
                    "Pls enter the title of reminder to search",
                    Snackbar.LENGTH_SHORT
                ).setAnchorView(binding.anchorView) // Set an anchor view in the middle
                    .show()
                true
            }
            R.id.action_edit -> {
                val intent = Intent(this, editInfoActivity::class.java).apply {
                    putExtra("username", getCurrentUsername())
                    putExtra("email", getCurrentEmail())
                    putExtra("password", getCurrentPassword())
                }
                startActivityForResult(intent, EDIT_PROFILE_REQUEST)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private fun getCurrentUsername(): String {
        return FirebaseAuth.getInstance().currentUser?.displayName ?: "Unknown"
    }

    private fun getCurrentEmail(): String {
        return FirebaseAuth.getInstance().currentUser?.email ?: "Unknown"
    }

    private fun getCurrentPassword(): String {
        return "N/A" // Firebase does not allow retrieving passwords
    }

    private fun showLogoutConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Log Out")
            .setMessage("Are you sure you want to log out?")
            .setPositiveButton("Log Out") { dialog, _ ->
                // Sign out from Firebase
                FirebaseAuth.getInstance().signOut()

                // Navigate to auth activity
                startActivity(Intent(this, AuthActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showExitConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Exit App")
            .setMessage("Are you sure you want to exit?")
            .setPositiveButton("Exit") { dialog, _ ->
                finishAffinity()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun onBackPressed() {
        if (navController.currentDestination?.id == R.id.nav_home) {
            showExitConfirmationDialog()
        } else {
            super.onBackPressed()
        }
    }

    // Add MediaPlayer management methods
    private fun initializeMediaPlayer() {
        try {
            // Release any existing MediaPlayer first
            if (mediaPlayer != null) {
                try {
                    if (mediaPlayer?.isPlaying == true) {
                        mediaPlayer?.stop()
                    }
                    mediaPlayer?.release()
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error releasing existing MediaPlayer", e)
                }
                mediaPlayer = null
            }

            // Create a new MediaPlayer instance
            mediaPlayer = MediaPlayer().apply {
                setOnErrorListener { _, what, extra ->
                    Log.e("MediaPlayer", "Error occurred: what=$what, extra=$extra")
                    true
                }

                // Add prepare listener to handle preparation errors
                setOnPreparedListener {
                    Log.d("MediaPlayer", "MediaPlayer prepared successfully")
                }
            }

            Log.d("MainActivity", "MediaPlayer initialized successfully")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error initializing MediaPlayer", e)
        }
    }

    override fun onPause() {
        try {
            // Only pause if MediaPlayer is initialized and playing
            if (mediaPlayer != null && mediaPlayer?.isPlaying == true) {
                try {
                    mediaPlayer?.pause()
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error pausing MediaPlayer", e)
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onPause", e)
        }
        super.onPause()
    }

    override fun onDestroy() {
        try {
            // Release MediaPlayer
            if (mediaPlayer != null) {
                try {
                    if (mediaPlayer?.isPlaying == true) {
                        mediaPlayer?.stop()
                    }
                    mediaPlayer?.release()
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error releasing MediaPlayer", e)
                }
                mediaPlayer = null
            }

            // Cancel animations
            try {
                rippleAnimator?.cancel()
                rippleAnimator = null
            } catch (e: Exception) {
                Log.e("MainActivity", "Error canceling animations", e)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onDestroy", e)
        }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()

        val isOffline = com.example.reminderapp.NetworkUtils.shouldUseOfflineMode(this)
        lastOfflineState = isOffline
    }

    companion object {
        const val EDIT_PROFILE_REQUEST = 1
        private const val PERMISSION_REQUEST_CODE = 1001
    }
}

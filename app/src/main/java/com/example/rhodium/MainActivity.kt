package com.example.rhodium

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.room.Room
import com.example.rhodium.ui.theme.RhodiumTheme
import com.google.android.gms.location.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import androidx.compose.foundation.Canvas
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.cos

class MainActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var database: AppDatabase

    private var initialLocation: Pair<Float, Float>? = null
    private var initialLatLon: Pair<Double, Double>? = null
    private var currentLocation: Pair<Float, Float>? = null

    // MutableState to hold the route
    private val routeState = mutableStateOf<List<Pair<Float, Float>>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.ACCESS_FINE_LOCATION),
            1
        )

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "maps-database"
        ).build()

        setContent {
            RhodiumTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MapScreen(
                        database = database,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 1000L
        ).apply {
            setWaitForAccurateLocation(false)
            setMinUpdateIntervalMillis(500L)
            setMaxUpdateDelayMillis(1000L)
        }.build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let {
                    Log.d("LocationUpdate", "Received new location: ${it.latitude}, ${it.longitude}")
                    updateLocation(Pair(it.latitude, it.longitude))
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Request permissions if not granted
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            return
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private fun updateLocation(newLocation: Pair<Double, Double>) {
        currentLocation?.let {
            val pixelCoordinates = convertToPixelCoordinates(newLocation)
            currentLocation = pixelCoordinates
            updateRoute(pixelCoordinates)
            Log.d("LocationUpdate", "New Location in pixels: (${pixelCoordinates.first}, ${pixelCoordinates.second})")
        }
    }

    private fun convertToPixelCoordinates(location: Pair<Double, Double>): Pair<Float, Float> {
        val initialLat = initialLatLon?.first ?: return Pair(0f, 0f)
        val initialLon = initialLatLon?.second ?: return Pair(0f, 0f)
        val initialX = initialLocation?.first ?: return Pair(0f, 0f)
        val initialY = initialLocation?.second ?: return Pair(0f, 0f)

        // Differences in degrees
        val latDiff = location.first - initialLat
        val lonDiff = location.second - initialLon

        // Use finer scaling factors for small indoor spaces and apply a smoothing factor
        val scalingFactor = 1e6 // Fine-tune this factor based on your map's size and resolution
        val x = initialX + (lonDiff * scalingFactor).toFloat()
        val y = initialY - (latDiff * scalingFactor).toFloat() // Invert y because the origin is at the top-left

        Log.d("CoordinateConversion", "Convert lat/lon: (${location.first}, ${location.second}) to pixels: ($x, $y)")
        Log.d("CoordinateConversion", "Initial Lat/Lon: ($initialLat, $initialLon), Initial Pixels: ($initialX, $initialY)")
        Log.d("CoordinateConversion", "Lat/Lon Diff: ($latDiff, $lonDiff), Scaling Factor: $scalingFactor")

        return Pair(x, y)
    }


    @SuppressLint("MissingPermission")
    private suspend fun getCurrentLatLon(): Pair<Double, Double>? = suspendCoroutine { cont ->
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                val latLon = Pair(it.latitude, it.longitude)
                Log.d("InitialLocation", "Current location: ${it.latitude}, ${it.longitude}")
                cont.resume(latLon)
            } ?: run {
                Log.e("InitialLocation", "Location is null")
                cont.resume(null)
            }
        }.addOnFailureListener { e ->
            Log.e("MainActivity", "Failed to get current location", e)
            cont.resume(null)
        }
    }

    // Helper function to update the route state
    private fun updateRoute(newLocation: Pair<Float, Float>) {
        val routeList = routeState.value.toMutableList()
        routeList.add(newLocation)
        routeState.value = routeList
    }

    @SuppressLint("ProduceStateDoesNotAssignValue")
    @Composable
    fun MapScreen(database: AppDatabase, modifier: Modifier = Modifier) {
        var mapBitmap by remember { mutableStateOf<Bitmap?>(null) }
        val context = LocalContext.current
        var userLocation by remember { mutableStateOf<Pair<Float, Float>?>(null) }
        val scope = rememberCoroutineScope()

        var availableMaps by remember { mutableStateOf(emptyList<MapEntity>()) }

        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                availableMaps = database.mapDao().getAll()
            }
        }

        var selectedMap by remember { mutableStateOf<String?>(null) }
        var showDialog by remember { mutableStateOf(false) }
        var newMapUri by remember { mutableStateOf<Uri?>(null) }
        var newMapName by remember { mutableStateOf("") }

        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data: Intent? = result.data
                val uri = data?.data
                if (uri != null) {
                    newMapUri = uri
                    showDialog = true
                }
            }
        }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text(text = "Enter Map Name") },
                text = {
                    TextField(
                        value = newMapName,
                        onValueChange = { newMapName = it },
                        label = { Text("Map Name") }
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val uri = newMapUri ?: return@Button
                            val inputStream = context.contentResolver.openInputStream(uri)
                            mapBitmap = BitmapFactory.decodeStream(inputStream)
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    // Save map to the database with the user-provided name
                                    database.mapDao().insert(MapEntity(name = newMapName, uri = uri.toString()))
                                }
                                saveMapUri(uri.toString(), context)
                                // Update availableMaps to include the newly added map
                                availableMaps = withContext(Dispatchers.IO) { database.mapDao().getAll() }
                            }
                            showDialog = false
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    Button(onClick = { showDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (mapBitmap != null) {
                BackHandler {
                    mapBitmap = null
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                                detectTapGestures { tapOffset ->
                                    userLocation = Pair(tapOffset.x, tapOffset.y)
                                    initialLocation = Pair(tapOffset.x, tapOffset.y)
                                    currentLocation = Pair(tapOffset.x, tapOffset.y)
                                    // Launch a coroutine to get the current latitude and longitude
                                    scope.launch {
                                        initialLatLon = getCurrentLatLon()

                                        if (initialLatLon == null) {
                                            Log.e("MapScreen", "Failed to get initial GPS location")
                                        } else {
                                            Log.d("MapScreen", "Initial Lat/Lon: $initialLatLon")
                                            routeState.value = listOf(Pair(tapOffset.x, tapOffset.y))
                                            startLocationUpdates() // Start GPS updates after setting the initial location
                                        }
                                    }
                                    Log.d("MapScreen", "Tapped Location: ($tapOffset.x, $tapOffset.y)")
                                }
                            }
                            ) {
                            Image(
                                bitmap = mapBitmap!!.asImageBitmap(),
                                contentDescription = "Map",
                                modifier = Modifier.fillMaxSize()
                            )

                            DrawRoute(routeState.value)

                            userLocation?.let { (x, y) ->
                                Box(
                                    modifier = Modifier
                                        .offset(x.dp, y.dp)
                                        .size(10.dp)
                                        .background(Color.Green) // Initial location pin
                                )
                            }
                            currentLocation?.let { (x, y) ->
                                Box(
                                    modifier = Modifier
                                        .offset(x.dp, y.dp)
                                        .size(10.dp)
                                        .background(Color.Red) // Current location pin
                                )
                            }
                        }
                        } else {
                    Text("Select a map from the inventory", modifier = Modifier.padding(16.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(availableMaps) { map ->
                            MapListItem(map) {
                                // Load the selected map
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        val inputStream = context.contentResolver.openInputStream(Uri.parse(map.uri))
                                        mapBitmap = BitmapFactory.decodeStream(inputStream)
                                        routeState.value = emptyList() // Clear the route when a new map is selected
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    FloatingActionButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                            launcher.launch(intent)
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Map")
                    }
                }
            }
        }

        @Composable
        fun DrawRoute(route: List<Pair<Float, Float>>) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                route.forEach { (x, y) ->
                    drawCircle(
                        color = Color.Red,
                        radius = 5f,
                        center = Offset(x, y),
                        style = Stroke(width = 3f)
                    )
                    Log.d("DrawRoute", "Drawing circle at: ($x, $y)")
                }
            }
        }

        @Composable
        fun MapListItem(map: MapEntity, onClick: () -> Unit) {
            Text(
                text = map.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clickable { onClick() }
            )
        }
    }

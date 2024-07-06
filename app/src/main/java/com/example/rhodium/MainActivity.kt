package com.example.rhodium

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoWcdma
import android.telephony.TelephonyManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.room.Room
import com.example.rhodium.ui.theme.RhodiumTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt
import androidx.compose.foundation.Canvas
import androidx.lifecycle.lifecycleScope
import com.example.rhodium.database.AppDatabase
import com.example.rhodium.database.CustomSignalStrength
import com.example.rhodium.database.MapEntity
import com.example.rhodium.database.getSignalStrength
import com.example.rhodium.elements.DeleteConfirmationDialog
import com.example.rhodium.elements.GuideDialog
import com.example.rhodium.elements.LocationMarker
import com.example.rhodium.elements.MapListItem
import com.example.rhodium.elements.SignalStrengthBox
import com.example.rhodium.elements.saveMapUri
import com.example.rhodium.services.RouteColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import com.example.rhodium.services.RetrieveAndSaveCellInfo
import com.example.rhodium.services.getColorByIndex
import kotlinx.coroutines.flow.collectLatest
import com.google.android.gms.location.*
import android.location.Location
import android.os.Looper


class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var magnetometer: Sensor? = null
    private val signalStrengthFlow = MutableStateFlow(0)
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var database: AppDatabase

    private var initialLocation: Pair<Float, Float>? = null
    private var currentLocation: Pair<Float, Float>? = null
    private var currentsignal: Int? = null
    private var showGuide by mutableStateOf(false)
    private lateinit var locationCallback: LocationCallback
    private var locationState: MutableState<Location?> = mutableStateOf(null)

    private val locationRequest = LocationRequest.create().apply {
        interval = 700
        fastestInterval = 700
        priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    }


    // MutableState to hold the route
    private val routeState = mutableStateOf<List<Triple<Float, Float, Int>>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
            1
        )

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "maps-database"
        ).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult ?: return
                val location = locationResult.lastLocation
                locationState.value = location
                getCellInfo()
            }
        }


        startLocationUpdates()

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


        lifecycleScope.launch {
            RetrieveAndSaveCellInfo(this@MainActivity, database).collect { signalStrength ->
                Log.d("MainActivity", "Signal Strength: $signalStrength")
                signalStrengthFlow.value = signalStrength
            }
        }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.also { acc ->
            sensorManager.registerListener(this, acc, SensorManager.SENSOR_DELAY_NORMAL)
        }
        gyroscope?.also { gyro ->
            sensorManager.registerListener(this, gyro, SensorManager.SENSOR_DELAY_NORMAL)
        }
        magnetometer?.also { mag ->
            sensorManager.registerListener(this, mag, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    private var accelValues = FloatArray(3)
    private var magnetValues = FloatArray(3)
    private var smoothedAccelValues = FloatArray(3)
    private var velocityX = 0f
    private var velocityY = 0f
    private var previousTime: Long = System.currentTimeMillis()
    private val updateInterval = 20 // Update interval in milliseconds


    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private val walkingThreshold = 0.8f // Threshold to detect walking
    private val deltaMove = 1.5f // Constant delta value for movement
    private val horizontalThreshold = 9.84f // Threshold to detect if the phone is horizontal

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || initialLocation == null) return

        val currentTime = System.currentTimeMillis()
        val deltaTime = (currentTime - previousTime) / 1000f // in seconds

        if (deltaTime < updateInterval / 1000f) {
            // Debounce updates to avoid too frequent updates
            return
        }
        previousTime = currentTime

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accelValues = event.values.clone()
                Log.d(
                    "SensorChanged",
                    "Accelerometer: (${accelValues[0]}, ${accelValues[1]}, ${accelValues[2]})"
                )
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                magnetValues = event.values.clone()
                Log.d(
                    "SensorChanged",
                    "Magnetometer: (${magnetValues[0]}, ${magnetValues[1]}, ${magnetValues[2]})"
                )
            }
        }

        // Check if the phone is held horizontally (z-axis should be close to 9.8 or -9.8 for horizontal)
        if (accelValues[2] < horizontalThreshold && accelValues[2] > -horizontalThreshold) {
            Log.d("SensorChanged", "Phone is not horizontal. Ignoring movement.")
            return
        }

        SensorManager.getRotationMatrix(rotationMatrix, null, accelValues, magnetValues)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)
        val azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
        Log.d("SensorChanged", "Azimuth: $azimuth")

        // Determine direction based on azimuth
        val direction = when {
            azimuth in -45.0..45.0 -> "East"
            azimuth in 45.0..135.0 -> "South"
            azimuth < -45.0 && azimuth >= -135.0 -> "North"
            azimuth > 135.0 || azimuth < -135.0 -> "West"
            else -> "Unknown"
        }
        Log.d("SensorChanged", "Phone Direction: $direction")

        // Smooth the accelerometer data using a low-pass filter
        val alpha = 0.8f
        smoothedAccelValues[0] = alpha * smoothedAccelValues[0] + (1 - alpha) * accelValues[0]
        smoothedAccelValues[1] = alpha * smoothedAccelValues[1] + (1 - alpha) * accelValues[1]

        val accelMagnitude =
            sqrt(smoothedAccelValues[0] * smoothedAccelValues[0] + smoothedAccelValues[1] * smoothedAccelValues[1])

        if (accelMagnitude > walkingThreshold) {
            when (direction) {
                "East" -> {
                    velocityX = deltaMove
                    velocityY = 0f
                }

                "South" -> {
                    velocityX = 0f
                    velocityY = deltaMove
                }

                "North" -> {
                    velocityX = 0f
                    velocityY = -deltaMove
                }

                "West" -> {
                    velocityX = -deltaMove
                    velocityY = 0f
                }
            }

            // Update the current location based on the velocities
            currentLocation?.let { (x, y) ->
                val newX = x + velocityX
                val newY = y + velocityY
                currentLocation = Pair(newX, newY)
                val locationColor = getLocColor(currentsignal ?: 0)

                updateRoute(Triple(newX, newY, locationColor))
//                Log.d("SensorChanged", "New Location: ($newX, $newY)")
            }
        } else {
            // User is not walking, stop movement
            velocityX = 0f
            velocityY = 0f
            Log.d("SensorChanged", "User is not walking. Velocities set to zero.")
        }
    }

    private fun getLocColor(signal: Int): Int {
        return when (getSignalStrength(signal)) {
            CustomSignalStrength.EXCELLENT -> RouteColor.GREEN.ordinal
            CustomSignalStrength.GOOD -> RouteColor.YELLOW.ordinal
            CustomSignalStrength.FAIR -> RouteColor.ORANGE.ordinal
            CustomSignalStrength.POOR -> RouteColor.RED.ordinal
            CustomSignalStrength.NONE -> RouteColor.BLACK.ordinal
            else ->  RouteColor.BLACK.ordinal
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun updateRoute(newLocation: Triple<Float, Float , Int>) {
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
        var showGuide by remember { mutableStateOf(false) }
        var viewMode by remember { mutableStateOf(false) }
        var showDeleteDialog by remember { mutableStateOf(false) }
        var mapToDelete by remember { mutableStateOf<MapEntity?>(null) }


        val launcher =
            rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
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
                                    database.mapDao()
                                        .insert(MapEntity(name = newMapName, uri = uri.toString()))
                                }
                                saveMapUri(uri.toString(), context)
                                // Update availableMaps to include the newly added map
                                availableMaps =
                                    withContext(Dispatchers.IO) { database.mapDao().getAll() }
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

        if (showDeleteDialog) {
            DeleteConfirmationDialog(
                onConfirm = {
                    mapToDelete?.let { map ->
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                database.mapDao().delete(map)
                                availableMaps = database.mapDao().getAll() // Refresh the list
                            }
                            showDeleteDialog = false
                            mapToDelete = null
                        }
                    }
                },
                onDismiss = {
                    showDeleteDialog = false
                    mapToDelete = null
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
            Text(
                text = "Rhodium",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(16.dp)
            )
            if (mapBitmap != null) {
                BackHandler {
                    mapBitmap = null
                    viewMode = false
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            if (viewMode != true) {
                                detectTapGestures { tapOffset ->
                                    userLocation = Pair(tapOffset.x, tapOffset.y)
                                    initialLocation = Pair(tapOffset.x, tapOffset.y)
                                    currentLocation = Pair(tapOffset.x, tapOffset.y)
                                    routeState.value = listOf(Triple(tapOffset.x, tapOffset.y, 6))
                                    Log.d(
                                        "MapScreen",
                                        "Initial Location: ($tapOffset.x, $tapOffset.y)"
                                    )
                                }
                            }
                        }
                ) {
                    Image(
                        bitmap = mapBitmap!!.asImageBitmap(),
                        contentDescription = "Map",
                        modifier = Modifier.fillMaxSize()
                    )
                    SignalStrengthBox(currentsignal ?: 0)
                    DrawRoute(routeState.value)

                    currentLocation?.let { (x, y) ->
                        LocationMarker(x, y) // Display the current location marker
                        Log.d("MapScreen", "Current Location: ($x, $y)")
                    }
                }
            } else {
                Text("Select a map from the inventory", modifier = Modifier.padding(16.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    items(availableMaps) { map ->
                        MapListItem(
                            map = map,
                            onClickEdit = {
                                // Load the selected map in edit mode
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        val inputStream =
                                            context.contentResolver.openInputStream(Uri.parse(map.uri))
                                        mapBitmap = BitmapFactory.decodeStream(inputStream)
                                        routeState.value =
                                            emptyList()
                                        currentLocation = null
                                    }
                                }
                                showGuide = true
                            },
                            onClickView = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        val inputStream =
                                            context.contentResolver.openInputStream(Uri.parse(map.uri))
                                        mapBitmap = BitmapFactory.decodeStream(inputStream)
                                        routeState.value =
                                            emptyList()
                                        currentLocation = null
                                        viewMode = true
                                    }
                                }
                            },
                            onDelete = {
                                mapToDelete = map
                                showDeleteDialog = true
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                FloatingActionButton(
                    onClick = {
                        val intent =
                            Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                        launcher.launch(intent)
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Map")
                }
            }

        }

        if (showGuide) {
            GuideDialog {
                showGuide = false
            }
        }
    }


    @Composable
    fun DrawRoute(route: List<Triple<Float, Float, Int>>) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            route.forEach { (x, y, color) ->
                drawCircle(
                    color = getColorByIndex(color),
                    radius = 14f,
                    center = Offset(x, y),
                    style = androidx.compose.ui.graphics.drawscope.Fill
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun getCellInfo() {
        val telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        val cellInfoList = telephonyManager.allCellInfo

        cellInfoList?.forEach { cellInfo ->
            when (cellInfo) {
                is CellInfoLte -> {
                    val cellSignalStrength = cellInfo.cellSignalStrength
                    currentsignal = cellSignalStrength.dbm

                }

                is CellInfoWcdma -> {
                    val cellSignalStrength = cellInfo.cellSignalStrength
                    currentsignal = cellSignalStrength.dbm

                }

                is CellInfoGsm -> {
                    val cellSignalStrength = cellInfo.cellSignalStrength
                    currentsignal = cellSignalStrength.dbm
                }
            }
        }

    }
}

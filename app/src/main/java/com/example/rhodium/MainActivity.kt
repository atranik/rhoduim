package com.example.rhodium

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue
import kotlin.math.sqrt
import androidx.compose.foundation.Canvas


class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var magnetometer: Sensor? = null

    private lateinit var database: AppDatabase

    private var initialLocation: Pair<Float, Float>? = null
    private var currentLocation: Pair<Float, Float>? = null
    private var previousTime: Long = System.currentTimeMillis()
    private var velocityX = 0f
    private var velocityY = 0f
    private var accelValues = floatArrayOf(0f, 0f, 0f)
    private var gyroValues = floatArrayOf(0f, 0f, 0f)
    private var magnetValues = floatArrayOf(0f, 0f, 0f)

    // Constants for filtering and movement detection
    private val alpha = 0.9f // for low-pass filter
    private val movementThreshold = 0.1f // Threshold to detect movement
    private val updateInterval = 500 // Update interval in milliseconds
    private val friction = 0.9f // Friction factor to gradually reduce velocity
    private val deadZone = 0.2f // Dead zone to ignore small movements



    // MutableState to hold the route
    private val routeState = mutableStateOf<List<Pair<Float, Float>>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            1
        )

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

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
                // Apply high-pass filter to accelerometer data to remove gravity, but ignore z-axis
                accelValues[0] = alpha * accelValues[0] + (1 - alpha) * event.values[0]
                accelValues[1] = alpha * accelValues[1] + (1 - alpha) * event.values[1]
                Log.d("SensorChanged", "Filtered Accelerometer: (${accelValues[0]}, ${accelValues[1]})")
            }
            Sensor.TYPE_GYROSCOPE -> {
                gyroValues[0] = event.values[0]
                gyroValues[1] = event.values[1]
                Log.d("SensorChanged", "Gyroscope: (${gyroValues[0]}, ${gyroValues[1]})")
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                magnetValues[0] = event.values[0]
                magnetValues[1] = event.values[1]
                magnetValues[2] = event.values[2]
                Log.d("SensorChanged", "Magnetometer: (${magnetValues[0]}, ${magnetValues[1]}, ${magnetValues[2]})")
            }
        }

        // Calculate the magnitude of the accelerometer vector for x and y only
        val accelMagnitude = sqrt(accelValues[0] * accelValues[0] + accelValues[1] * accelValues[1])
        Log.d("SensorChanged", "Accelerometer Magnitude: $accelMagnitude")

        // Ignore small movements by applying a dead zone
        if (accelMagnitude < deadZone) {
            velocityX *= friction
            velocityY *= friction
            Log.d("SensorChanged", "In Dead Zone, Applying Friction: ($velocityX, $velocityY)")
        } else {
            // Update velocities
            velocityX += accelValues[0] * deltaTime
            velocityY += accelValues[1] * deltaTime
            Log.d("SensorChanged", "Updated Velocities: ($velocityX, $velocityY)")
        }

        // Apply friction to velocities
        velocityX *= friction
        velocityY *= friction

        // Apply a stop threshold to velocities
        val stopThreshold = 0.09f
        if (velocityX.absoluteValue < stopThreshold) velocityX = 0f
        if (velocityY.absoluteValue < stopThreshold) velocityY = 0f

        Log.d("SensorChanged", "Velocities after Stop Threshold: ($velocityX, $velocityY)")

        // Update the current location based on the velocities if movement exceeds the threshold
        currentLocation?.let { (x, y) ->
            val deltaX = velocityX * deltaTime
            val deltaY = velocityY * deltaTime

            if (deltaX.absoluteValue > movementThreshold || deltaY.absoluteValue > movementThreshold) {
                val newX = x + deltaX
                val newY = y + deltaY
                currentLocation = Pair(newX, newY)
                // Update the route state to add the new location
                updateRoute(Pair(newX, newY))
                Log.d("SensorChanged", "New Location: ($newX, $newY)")
            } else {
                Log.d("SensorChanged", "Movement below threshold: (deltaX: $deltaX, deltaY: $deltaY)")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

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
                                routeState.value = listOf(Pair(tapOffset.x, tapOffset.y))
                                Log.d("MapScreen", "Initial Location: ($tapOffset.x, $tapOffset.y)")
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
                    style = Stroke(width = 2f)
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

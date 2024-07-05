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
    private var showGuide by mutableStateOf(false)



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

    private var accelValues = FloatArray(3)
    private var magnetValues = FloatArray(3)
    private var smoothedAccelValues = FloatArray(3)
    private var velocityX = 0f
    private var velocityY = 0f

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)


    private val walkingThreshold = 0.94f // Threshold to detect walking
    private val deltaMove = 1.5f // Constant delta value for movement
    private val horizontalThreshold = 9.84f // Threshold to detect if the phone is horizontal

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || initialLocation == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accelValues = event.values.clone()
                Log.d("SensorChanged", "Accelerometer: (${accelValues[0]}, ${accelValues[1]}, ${accelValues[2]})")
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                magnetValues = event.values.clone()
                Log.d("SensorChanged", "Magnetometer: (${magnetValues[0]}, ${magnetValues[1]}, ${magnetValues[2]})")
            }
        }

        // Check if the phone is held horizontally (z-axis should be close to 9.8 or -9.8 for horizontal)
        if (accelValues[2] < horizontalThreshold && accelValues[2] > -horizontalThreshold) {
            Log.d("SensorChanged", "Phone is not horizontal. Ignoring movement.")
            return
        }

        // Calculate rotation matrix and orientation angles
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

        // Calculate the magnitude of the smoothed accelerometer vector for x and y only
        val accelMagnitude = sqrt(smoothedAccelValues[0] * smoothedAccelValues[0] + smoothedAccelValues[1] * smoothedAccelValues[1])
        Log.d("SensorChanged", "Smoothed Accelerometer Magnitude: $accelMagnitude")

        // Check if the user is walking
        if (accelMagnitude > walkingThreshold) {
            // Update velocities based on azimuth (direction)
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
            Log.d("SensorChanged", "User is walking. Updated Velocities: ($velocityX, $velocityY)")

            // Update the current location based on the velocities
            currentLocation?.let { (x, y) ->
                val newX = x + velocityX
                val newY = y + velocityY
                currentLocation = Pair(newX, newY)
                // Update the route state to add the new location
                updateRoute(Pair(newX, newY))
                Log.d("SensorChanged", "New Location: ($newX, $newY)")
            }
        } else {
            // User is not walking, stop movement
            velocityX = 0f
            velocityY = 0f
            Log.d("SensorChanged", "User is not walking. Velocities set to zero.")
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

//                    userLocation?.let { (x, y) ->
//                        Box(
//                            modifier = Modifier
//                                .offset(x.dp, y.dp)
//                                .size(4.dp)
//                        ) {
//                            LocationMarker() // Display the location marker
//                        }
//                    }
                    currentLocation?.let { (x, y) ->
                        LocationMarker(x ,y) // Display the current location marker
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
                        MapListItem(map) {
                            // Load the selected map
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    val inputStream = context.contentResolver.openInputStream(Uri.parse(map.uri))
                                    mapBitmap = BitmapFactory.decodeStream(inputStream)
                                    routeState.value = emptyList() // Clear the route when a new map is selected
                                    currentLocation = null
                                }
                            }
                            showGuide = true
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

        if (showGuide) {
            GuideDialog {
                showGuide = false
            }
        }
    }

    @Composable
    fun DrawRoute(route: List<Pair<Float, Float>>) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            route.forEach { (x, y) ->
                drawCircle(
                    color = Color.Red,
                    radius = 7f,
                    center = Offset(x, y),
                    style = androidx.compose.ui.graphics.drawscope.Fill
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

@Composable
fun GuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Guide") },
        text = { Text("Hold your phone with the screen facing upward, parallel to the floor.") },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

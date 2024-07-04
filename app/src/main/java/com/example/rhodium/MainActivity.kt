package com.example.rhodium

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.room.Room
import com.example.rhodium.ui.theme.RhodiumTheme
import kotlinx.coroutines.launch
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.activity.compose.BackHandler

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.Canvas

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color



class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    private lateinit var database: AppDatabase

    private var initialLocation: Pair<Float, Float>? = null
    private var currentLocation: Pair<Float, Float>? = null
    private var previousTime: Long = 0
    private var velocityX = 0f
    private var velocityY = 0f
    private val route = mutableListOf<Pair<Float, Float>>()

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
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || initialLocation == null) return

        val currentTime = System.currentTimeMillis()
        val deltaTime = (currentTime - previousTime) / 1000f // in seconds
        previousTime = currentTime

        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            // Use accelerometer data to update velocities
            val accelerationX = event.values[0]
            val accelerationY = event.values[1]

            velocityX += accelerationX * deltaTime
            velocityY += accelerationY * deltaTime
        }

        // Update the current location based on the velocities
        currentLocation?.let { (x, y) ->
            val newX = x + velocityX * deltaTime
            val newY = y + velocityY * deltaTime
            currentLocation = Pair(newX, newY)
            route.add(Pair(newX, newY))
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

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
                                route.clear()
                                route.add(Pair(tapOffset.x, tapOffset.y))
                            }
                        }
                ) {
                    Image(
                        bitmap = mapBitmap!!.asImageBitmap(),
                        contentDescription = "Map",
                        modifier = Modifier.fillMaxSize()
                    )

                    DrawRoute(route)

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

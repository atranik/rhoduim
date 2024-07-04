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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        ActivityCompat.requestPermissions(
//            this,
//            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
//            1
//        )

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
        // Handle sensor data to track user movement
        // TODO: Implement sensor data handling to update user location on the map
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

        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data: Intent? = result.data
                val uri = data?.data
                if (uri != null) {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    mapBitmap = BitmapFactory.decodeStream(inputStream)
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            // Save map to the database
                            database.mapDao().insert(MapEntity(name = uri.toString(), uri = uri.toString()))
                        }
                        saveMapUri(uri.toString(), context)
                        // Update availableMaps to include the newly added map
                        availableMaps = withContext(Dispatchers.IO) { database.mapDao().getAll() }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (mapBitmap != null) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Image(
                        bitmap = mapBitmap!!.asImageBitmap(),
                        contentDescription = "Map",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Handle user clicking on the map to set initial location
                                // TODO: Implement location setting logic
                            }
                    )
                    userLocation?.let { (x, y) ->
                        Box(
                            modifier = Modifier
                                .offset(x.dp, y.dp)
                                .size(10.dp)
                                .background(MaterialTheme.colorScheme.primary)
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

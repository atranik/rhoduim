package com.example.rhodium.database

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query


@Entity(tableName = "route_state")
data class RouteStateEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val mapName: String,
    val x: Float,
    val y: Float,
    val color: Int
)

@Dao
interface RouteStateDao {
    @Query("SELECT * FROM route_state WHERE mapName = :mapName")
    fun getRouteStatesForMap(mapName: String): List<RouteStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(routeState: RouteStateEntity)

    @Query("DELETE FROM route_state WHERE mapName = :mapName")
    fun deleteRouteStatesForMap(mapName: String)
}

fun saveRouteStates(database: AppDatabase, mapName: String, routeState: List<Triple<Float, Float, Color>>) {
    val routeStateDao = database.routeStateDao()
    routeStateDao.deleteRouteStatesForMap(mapName) // Clear existing points for the map
    routeState.forEach { (x, y, color) ->
        routeStateDao.insert(RouteStateEntity(mapName = mapName, x = x, y = y, color = color.toArgb()))
    }
}

fun loadRouteStates(database: AppDatabase, mapName: String): List<Triple<Float, Float, Color>> {
    val routeStateDao = database.routeStateDao()
    return routeStateDao.getRouteStatesForMap(mapName).map { entity ->
        Triple(entity.x, entity.y, Color(entity.color))
    }
}


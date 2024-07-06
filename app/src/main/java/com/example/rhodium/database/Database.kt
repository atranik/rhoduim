package com.example.rhodium.database

import androidx.room.Database
import androidx.room.RoomDatabase


@Database(
    entities = [MapEntity::class, CellLteData::class, CellWcdmaData::class, CellGsmData::class, RouteStateEntity::class],
    version = 1
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mapDao(): MapDao
    abstract fun cellLteDataDao(): CellLteDataDao
    abstract fun cellWcdmaDataDao(): CellWcdmaDataDao
    abstract fun cellGsmDataDao(): CellGsmDataDao
    abstract fun routeStateDao(): RouteStateDao
}


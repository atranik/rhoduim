package com.example.rhodium

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [MapEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mapDao(): MapDao
}

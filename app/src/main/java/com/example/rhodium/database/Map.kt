package com.example.rhodium.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "maps")
data class MapEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "uri") val uri: String
)

@Dao
interface MapDao {
    @Query("SELECT * FROM maps")
    fun getAll(): List<MapEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(map: MapEntity)

    @Delete
    fun delete(map: MapEntity)
}


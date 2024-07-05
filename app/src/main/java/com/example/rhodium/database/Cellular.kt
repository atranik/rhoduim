package com.example.rhodium.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query


@Entity(tableName = "cell_info_lte")
data class CellLteData(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cid: Int,
    val tac: Int,
    val rac: Int? = null,
    val nci: Long? = null,
    val rsrp: Int,
    val rsrq: Int,
    val sinr: Int? = null,
    val rss: Int,
    val signalStrength: CustomSignalStrength
)

@Entity(tableName = "cell_info_wcdma")
data class CellWcdmaData(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cid: Int,
    val lac: Int,
    val rac: Int? = null,
    val rscp: Int,
    val rss: Int,
    val signalStrength: CustomSignalStrength
)

@Entity(tableName = "cell_info_gsm")
data class CellGsmData(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cid: Int,
    val lac: Int,
    val rac: Int? = null,
    val rssi: Int,
    val rss: Int,
    val signalStrength: CustomSignalStrength
)

@Dao
interface CellLteDataDao {
    @Insert
    fun insert(cellInfoLte: CellLteData)

    @Query("SELECT * FROM cell_info_lte")
    fun getAll(): List<CellLteData>
}

@Dao
interface CellWcdmaDataDao {
    @Insert
    fun insert(cellInfoWcdma: CellWcdmaData)

    @Query("SELECT * FROM cell_info_wcdma")
    fun getAll(): List<CellWcdmaData>
}

@Dao
interface CellGsmDataDao {
    @Insert
    fun insert(cellInfoGsm: CellGsmData)

    @Query("SELECT * FROM cell_info_gsm")
    fun getAll(): List<CellGsmData>
}

enum class CustomSignalStrength {
    EXCELLENT,
    GOOD,
    FAIR,
    POOR,
    NONE
}

fun getSignalStrength(rss: Int): CustomSignalStrength {
    return when {
        rss >= -50 -> CustomSignalStrength.EXCELLENT
        rss >= -70 -> CustomSignalStrength.GOOD
        rss >= -90 -> CustomSignalStrength.FAIR
        rss >= -110 -> CustomSignalStrength.POOR
        else -> CustomSignalStrength.NONE
    }
}
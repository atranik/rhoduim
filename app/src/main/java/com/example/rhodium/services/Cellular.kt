package com.example.rhodium.services


import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.*
import android.telephony.TelephonyManager
import kotlinx.coroutines.withContext
import com.example.rhodium.database.AppDatabase
import com.example.rhodium.database.CellGsmData
import com.example.rhodium.database.CellLteData
import com.example.rhodium.database.CellWcdmaData
import com.example.rhodium.database.CustomSignalStrength
import com.example.rhodium.database.getSignalStrength
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow


@SuppressLint("MissingPermission")
public fun RetrieveAndSaveCellInfo(context: Context, database: AppDatabase): Flow<Int> = flow {
    while (true) {
    val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    val cellInfoList = telephonyManager.allCellInfo

    cellInfoList?.forEach { cellInfo ->
        when (cellInfo) {
            is CellInfoLte -> {
                val cellIdentity = cellInfo.cellIdentity
                val cellSignalStrength = cellInfo.cellSignalStrength
                val lteInfo = CellLteData(
                    cid = cellIdentity.ci,
                    tac = cellIdentity.tac,
                    rsrp = cellSignalStrength.rsrp,
                    rsrq = cellSignalStrength.rsrq,
                    sinr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cellSignalStrength.rssnr
                    } else {
                        null
                    },
                    rss = cellSignalStrength.dbm,
                    signalStrength = getSignalStrength(cellSignalStrength.dbm)
                )
                withContext(Dispatchers.IO) {
                    database.cellLteDataDao().insert(lteInfo)
                }
                if(cellIdentity.ci != 0) {
                    emit(cellSignalStrength.dbm)
                }

            }

            is CellInfoWcdma -> {
                val cellIdentity = cellInfo.cellIdentity
                val cellSignalStrength = cellInfo.cellSignalStrength
                val wcdmaInfo = CellWcdmaData(
                    cid = cellIdentity.cid,
                    lac = cellIdentity.lac,
                    rscp = cellSignalStrength.dbm,
                    rss = cellSignalStrength.dbm,
                    signalStrength = getSignalStrength(cellSignalStrength.dbm)
                )
                withContext(Dispatchers.IO) {
                    database.cellWcdmaDataDao().insert(wcdmaInfo)
                }
                if(cellIdentity.cid != 0) {
                    emit(cellSignalStrength.dbm)
                }
            }

            is CellInfoGsm -> {
                val cellIdentity = cellInfo.cellIdentity
                val cellSignalStrength = cellInfo.cellSignalStrength
                val gsmInfo = CellGsmData(
                    cid = cellIdentity.cid,
                    lac = cellIdentity.lac,
                    rssi = cellSignalStrength.dbm,
                    rss = cellSignalStrength.dbm,
                    signalStrength = getSignalStrength(cellSignalStrength.dbm)
                )
                withContext(Dispatchers.IO) {
                    database.cellGsmDataDao().insert(gsmInfo)
                }
                if(cellIdentity.cid != 0) {
                    emit(cellSignalStrength.dbm)
                    }
                }
            }
        }
        delay(300) // Delay for 100 millisecond before next read
    }
}


package com.github.zarandya.heartticks.db

import androidx.room.*
import androidx.room.OnConflictStrategy.Companion.REPLACE

import com.github.zarandya.heartticks.db.BluetoothDeviceType.PM5
import com.github.zarandya.heartticks.db.BluetoothDeviceType.HRM

@Dao
interface DevicesDao {
    @get:Query("SELECT * FROM $TABLE_BLUETOOTH_DEVICES WHERE $COLUMN_DEVICE_TYPE == $PM5")
    val ergs: MutableList<SavedBluetoothDevice>

    @get:Query("SELECT * FROM $TABLE_BLUETOOTH_DEVICES WHERE $COLUMN_DEVICE_TYPE == $HRM")
    val hrms: MutableList<SavedBluetoothDevice>

    @Query("SELECT * FROM $TABLE_BLUETOOTH_DEVICES WHERE $COLUMN_DEVICE_TYPE == :kind")
    fun devicesOfKind(kind: Int): MutableList<SavedBluetoothDevice>

    @Query("SELECT * FROM $TABLE_BLUETOOTH_DEVICES WHERE $COLUMN_ADDRESS == :address")
    operator fun get(address: String): SavedBluetoothDevice

    @Insert(onConflict = REPLACE)
    operator fun plusAssign(device: SavedBluetoothDevice)
}

@Database(entities = [SavedBluetoothDevice::class], version = 1)
abstract class AppDatabase: RoomDatabase() {
    abstract fun getDevicesDao(): DevicesDao
}

inline val AppDatabase.devices get() = getDevicesDao()
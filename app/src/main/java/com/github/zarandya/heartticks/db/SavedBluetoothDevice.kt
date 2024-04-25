package com.github.zarandya.heartticks.db

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build.VERSION
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES
import androidx.core.app.ActivityCompat
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

const val TABLE_BLUETOOTH_DEVICES = "BluetoothDevices"
const val COLUMN_NAME = "Name"
const val COLUMN_ALIAS = "Alias"
const val COLUMN_ADDRESS = "Address"
const val COLUMN_DEVICE_TYPE = "DeviceType"

@Entity(tableName = TABLE_BLUETOOTH_DEVICES)
data class SavedBluetoothDevice (
        @PrimaryKey
        @ColumnInfo(name = COLUMN_ADDRESS)
        val address: String,

        @ColumnInfo (name = COLUMN_NAME)
        val name: String,

        @ColumnInfo (name = COLUMN_ALIAS)
        val alias: String,

        // TODO this doesn't work and isn't useful
        @ColumnInfo (name = "AddressType")
        val addressType: Int,

        @ColumnInfo (name = COLUMN_DEVICE_TYPE)
        val deviceType: Int
        )

// TODO find a better place to put this.
val BluetoothDevice.aliasOrName: String
        @SuppressLint("MissingPermission") get() =
                if (SDK_INT >= VERSION_CODES.R) (alias ?: name) else name
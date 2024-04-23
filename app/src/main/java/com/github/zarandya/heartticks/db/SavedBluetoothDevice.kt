package com.github.zarandya.heartticks.db

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


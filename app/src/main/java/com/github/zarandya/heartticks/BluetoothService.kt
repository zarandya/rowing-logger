package com.github.zarandya.heartticks

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.IBinder
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.github.zarandya.heartticks.BluetoothConnectionManager.SERVICE_STATE_CONNECTED
import com.github.zarandya.heartticks.BluetoothHrmConnectionManager.ACTION_HR_VALUE_UPDATE
import com.github.zarandya.heartticks.BluetoothHrmConnectionManager.EXTRA_HR_VALUE
import com.github.zarandya.heartticks.MainActivity.EXTRA_DEVICE
import com.github.zarandya.heartticks.R.string.bluetooth_service_channel_name
import com.github.zarandya.heartticks.db.BluetoothDeviceType.BIKE
import com.github.zarandya.heartticks.db.BluetoothDeviceType.HRM
import com.github.zarandya.heartticks.db.BluetoothDeviceType.OARLOCK
import com.github.zarandya.heartticks.db.BluetoothDeviceType.PM5
import java.util.*


class BluetoothService: Service() {


    companion object {
        const val ACTION_CONNECT_DEVICE = "com.github.zarandya.heartticks.BluetoothService.ACTION_CONNECT_DEVICE"
        const val ACTION_DISCONNECT_DEVICE = "com.github.zarandya.heartticks.BluetoothService.ACTION_DISCONNECT_DEVICE"
        const val ACTION_BLUETOOTH_SERVICE_STATE_CHANGED = "com.github.zarandya.heartticks.BluetoothService.ACTION_BLUETOOTH_SERVICE_STATE_CHANGED"

        const val EXTRA_DEVICE_TYPE = "com.github.zarandya.heartticks.BluetoothService.EXTRA_DEVICE_TYPE"

        private val CHANNEL_ID = "com.github.zarandya.heartticks.BLUETOOTH_SERVICE_NOTIFICATION"
    }

    private lateinit var bluetoothAdapter: BluetoothAdapter

    private val connectionManagers = ArrayList<BluetoothConnectionManager>()

    override fun onCreate() {
        super.onCreate()
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID,
                    getString(bluetooth_service_channel_name),
                    NotificationManager.IMPORTANCE_LOW)
            channel.description = getString(bluetooth_service_channel_name)
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
        //Timer().scheduleAtFixedRate(object : TimerTask() {
        //    override fun run() {
        //        val gaf: GattoolFileWriter = gattCallback
        //        gaf.writeTime()
        //    }
        //}, getTimestampWritePeriod().toLong(), getTimestampWritePeriod().toLong()) // TODO this was 5000 for ergs and 500 for HRM, why?
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (intent.action == ACTION_CONNECT_DEVICE) {
            val device = intent.getParcelableExtra<BluetoothDevice>(MainActivity.EXTRA_DEVICE)
                    ?: return START_NOT_STICKY
            val deviceType = intent.getIntExtra(EXTRA_DEVICE_TYPE, -1)

            val connectionManager = when (deviceType) {
                PM5 -> BluetoothPM5ConnectionManager(this, device)
                HRM -> BluetoothHrmConnectionManager(this, device)
                BIKE -> BluetoothBikeConnectionManager(this, device)
                OARLOCK -> BluetoothOarlockConnectionManager(this, device)
                else -> return START_NOT_STICKY
            }
            connectionManager.startConnect()
            connectionManagers += connectionManager
        }
        else if (intent.action == ACTION_DISCONNECT_DEVICE) {
            val device = intent.getParcelableExtra<BluetoothDevice>(EXTRA_DEVICE)
            val connectionManager = connectionManagers.find { it.device.equals(device) }
            connectionManager?.startDisconnect()
        }
        else if (intent.action == BluetoothConnectionManager.ACTION_QUERY_STATE) {
            sendState()
        }
        return START_NOT_STICKY
    }

    fun sendState() {
        val broadcast = Intent(ACTION_BLUETOOTH_SERVICE_STATE_CHANGED)
        broadcast.putExtra(BluetoothConnectionManager.EXTRA_SERVICE_STATE,
                connectionManagers.map { it.state }.toIntArray())
        broadcast.putExtra(EXTRA_DEVICE_TYPE,
                connectionManagers.map { it.deviceType }.toIntArray())
        broadcast.putExtra(EXTRA_DEVICE,
                connectionManagers.map { it.device }.toTypedArray())
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast)

        if (connectionManagers.any { it.state == SERVICE_STATE_CONNECTED }) {

            val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.stat_sys_data_bluetooth)
                    .setContentText("Connected to ${connectionManagers.size} devices")
                    .setContentText("")
                    .setStyle(NotificationCompat.BigTextStyle().bigText(""))
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build()

            startForeground(83, notification)
        }
    }

    fun onDisconnectFinished(connectionManager: BluetoothConnectionManager) {
        connectionManagers -= connectionManager
        if (connectionManagers.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }

        sendState();
    }

    fun sendHRValue(hr: Int) {
        for (connectionManager in connectionManagers) {
            if (connectionManager is BluetoothPM5ConnectionManager) {
                connectionManager.receiveHeartRate(hr);
            }
        }

        val broadcast = Intent(ACTION_HR_VALUE_UPDATE)
        broadcast.putExtra(EXTRA_HR_VALUE, hr)
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast)

    }
}
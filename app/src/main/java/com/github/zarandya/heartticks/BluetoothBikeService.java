package com.github.zarandya.heartticks;

import static com.github.zarandya.heartticks.db.BluetoothDeviceType.HRM;
import static java.util.TimeZone.getTimeZone;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Intent;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

public class BluetoothBikeService extends BaseBluetoothService {

    public static final String ACTION_BIKE_SERVICE_STATE_CHANGED = "action_bike_service_state_changed";
    public static final String EXTRA_SERVICE_STATE = BaseBluetoothService.EXTRA_SERVICE_STATE;

    static final UUID BIKE_SPEED_CADENCE_SERVICE_UUID =   UUID.fromString("00001816-0000-1000-8000-00805f9b34fb");
    static final UUID BIKE_POWER_UUID = UUID.fromString("00001818-0000-1000-8000-00805f9b34fb");
    static final UUID FITNESS_MACHINE_SERVICE_UUID = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb");

    static final UUID BIKE_POWER_MEASUREMENT_UUID = UUID.fromString("00002a63-0000-1000-8000-00805f9b34fb");
    static final UUID BIKE_POWER_FEATURE_UUID = UUID.fromString("00002a65-0000-1000-8000-00805f9b34fb");
    static final UUID BIKE_CSC_MEASUREMENT = UUID.fromString("00002a5b-0000-1000-8000-00805f9b34fb2");
    static final UUID BIKE_CSC_FEATURE = UUID.fromString("00002a5c-0000-1000-8000-00805f9b34fb2");
    static final UUID BIKE_SC_CONTROL_POINT = UUID.fromString("00002a55-0000-1000-8000-00805f9b34fb");
    static final UUID BIKE_INDOOR_DATA = UUID.fromString("00002ad2-0000-1000-8000-00805f9b34fb");
    static final UUID FITNESS_MACHINE_STATUS_UUID = UUID.fromString("00002ada-0000-1000-8000-00805f9b34fb");
    static final UUID FITNESS_MACHINE_CONTROL_POINT = UUID.fromString("00002ad9-0000-1000-8000-00805f9b34fb");


    public static final String CHANNEL_ID = "io.github.zarandya.beatrate.BLUETOOTH_BIKE_SERVICE_NOTIFICATION";

    @Override
    protected String getNotificationChannelId() {
        return CHANNEL_ID;
    }

    @Override
    protected int getTimestampWritePeriod() {
        return 5000;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        registerCharacteristic(FITNESS_MACHINE_SERVICE_UUID, BIKE_INDOOR_DATA, true);
        registerCharacteristic(FITNESS_MACHINE_SERVICE_UUID, FITNESS_MACHINE_STATUS_UUID, false);

        registerCharacteristic(BIKE_SPEED_CADENCE_SERVICE_UUID, BIKE_CSC_MEASUREMENT, false);
        registerCharacteristicForReadOnce(BIKE_SPEED_CADENCE_SERVICE_UUID, BIKE_CSC_FEATURE);

        registerCharacteristic(BIKE_POWER_UUID, BIKE_POWER_MEASUREMENT_UUID, false);
        registerCharacteristicForReadOnce(BIKE_POWER_UUID, BIKE_POWER_FEATURE_UUID);
    }


    @Override
    protected String generateOutputFilename(String base) {
        @SuppressLint("SimpleDateFormat")
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss'Z'");
        df.setTimeZone(getTimeZone("UTC"));

        return base + "/BikeGatt_" + df.format(new Date()) + ".bike.gatt";
    }

    @Override
    protected long getConnectionTimeoutPeriod() {
        return 5000;
    }

    @Override
    protected int getDeviceType() { return HRM; }

    @Override
    protected void sendState() {
        Intent broadcast = new Intent(ACTION_BIKE_SERVICE_STATE_CHANGED);
        broadcast.putExtra(EXTRA_SERVICE_STATE, getState());
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

}

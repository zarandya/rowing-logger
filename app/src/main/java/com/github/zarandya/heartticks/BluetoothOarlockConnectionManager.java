package com.github.zarandya.heartticks;

import static com.github.zarandya.heartticks.db.BluetoothDeviceType.HRM;
import static com.github.zarandya.heartticks.db.BluetoothDeviceType.OARLOCK;
import static java.util.TimeZone.getTimeZone;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

public class BluetoothOarlockConnectionManager extends BluetoothConnectionManager {

    public static final String ACTION_OARLOCK_SERVICE_STATE_CHANGED = "action_oarlock_service_state_changed";

    public static final String CHANNEL_ID = "io.github.zarandya.beatrate.BLUETOOTH_OARLOCK_SERVICE_NOTIFICATION";

    static final UUID OARLOCK_SERVICE_1_UUID = UUID.fromString("85920000-0338-4b83-ae4a-ac1d217adb03");
    static final UUID OARLOCK_SERVICE_2_UUID = UUID.fromString("12630000-dd25-497d-9854-9b6c02c77054");

    static final UUID OARLOCK_1_CHARACTERISTIC_1_UUID = UUID.fromString("85920100-0338-4b83-ae4a-ac1d217adb03");
    static final UUID OARLOCK_1_CHARACTERISTIC_2_UUID = UUID.fromString("85920200-0338-4b83-ae4a-ac1d217adb03");
    static final UUID OARLOCK_1_CHARACTERISTIC_3_UUID = UUID.fromString("8592ffff-0338-4b83-ae4a-ac1d217adb03");

    static final UUID OARLOCK_2_CHARACTERISTIC_1_UUID = UUID.fromString("12630001-dd25-497d-9854-9b6c02c77054");
    static final UUID OARLOCK_2_CHARACTERISTIC_2_UUID = UUID.fromString("12630002-dd25-497d-9854-9b6c02c77054");
    static final UUID OARLOCK_2_CHARACTERISTIC_3_UUID = UUID.fromString("12630003-dd25-497d-9854-9b6c02c77054");
    static final UUID OARLOCK_2_CHARACTERISTIC_4_UUID = UUID.fromString("12630200-dd25-497d-9854-9b6c02c77054");

    public BluetoothOarlockConnectionManager(@NonNull BluetoothService service, @NonNull BluetoothDevice device) {
        super(service, device);

        //registerCharacteristic(OARLOCK_SERVICE_1_UUID, OARLOCK_1_CHARACTERISTIC_1_UUID, true);
        //registerCharacteristicForReadOnce(OARLOCK_SERVICE_1_UUID, OARLOCK_1_CHARACTERISTIC_2_UUID);
        //registerCharacteristicForReadOnce(OARLOCK_SERVICE_1_UUID, OARLOCK_1_CHARACTERISTIC_3_UUID);

        registerCharacteristic(OARLOCK_SERVICE_2_UUID, OARLOCK_2_CHARACTERISTIC_1_UUID, true);
        //registerCharacteristic(OARLOCK_SERVICE_2_UUID, OARLOCK_2_CHARACTERISTIC_2_UUID, true);
        //registerCharacteristic(OARLOCK_SERVICE_2_UUID, OARLOCK_2_CHARACTERISTIC_3_UUID, true);
        registerCharacteristicForReadOnce(OARLOCK_SERVICE_2_UUID, OARLOCK_2_CHARACTERISTIC_4_UUID);
    }

    @Override
    protected int getTimestampWritePeriod() {
        return 5000;
    }

    @Override
    protected String generateOutputFilename(String base) {
        @SuppressLint("SimpleDateFormat")
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss'Z'");
        df.setTimeZone(getTimeZone("UTC"));

        return base + "/OarlockGatt_" + df.format(new Date()) + ".oarlock.gatt";
    }

    @Override
    protected long getConnectionTimeoutPeriod() {
        return 5000;
    }

    @Override
    protected int getDeviceType() { return OARLOCK; }

}

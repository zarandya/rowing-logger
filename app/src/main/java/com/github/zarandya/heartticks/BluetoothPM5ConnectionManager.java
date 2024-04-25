package com.github.zarandya.heartticks;

import static com.github.zarandya.heartticks.BluetoothHrmConnectionManager.ACTION_HR_VALUE_UPDATE;
import static com.github.zarandya.heartticks.BluetoothHrmConnectionManager.EXTRA_HR_VALUE;
import static com.github.zarandya.heartticks.db.BluetoothDeviceType.PM5;
import static java.util.TimeZone.getTimeZone;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

public class BluetoothPM5ConnectionManager extends BluetoothConnectionManager {

    public static final String ACTION_CONNECT_BUTTON_PRESSED = "action_connect_button_pressed";
    public static final String ACTION_QUERY_STATE = "sction_query_state";
    public static final String ACTION_SERVICE_STATE_CHANGED = "action_service_change_state";
    public static final String EXTRA_SERVICE_STATE = "extra_service_state";

    static final UUID C2_PM_CONTROL_SERVICE_UUID = UUID.fromString("ce060030-43e5-11e4-916c-0800200c9a66");
    static final UUID C2_MULTIPLEXED_INFORMATION_CHARACTERISTIC_UUID = UUID.fromString("ce060080-43e5-11e4-916c-0800200c9a66");
    static final UUID C2_FORCE_CURVE_DATA_CHARACTERISTIC_UUID = UUID.fromString("ce06003d-43e5-11e4-916c-0800200c9a66");

    public static final String CHANNEL_ID = "io.github.zarandya.beatrate.BLUETOOTH_SERVICE_NOTIFICATION";

    private int hrFromBluetoothHrm = 0;

    public BluetoothPM5ConnectionManager(@NonNull BluetoothService service, @NonNull BluetoothDevice device) {
        super(service, device);

        registerCharacteristic(C2_PM_CONTROL_SERVICE_UUID, C2_MULTIPLEXED_INFORMATION_CHARACTERISTIC_UUID, true);
        registerCharacteristic(C2_PM_CONTROL_SERVICE_UUID, C2_FORCE_CURVE_DATA_CHARACTERISTIC_UUID, false);
    }

    @Override
    protected int getTimestampWritePeriod() {
        return 500;
    }

    @Override
    protected String generateOutputFilename(String base) {
        @SuppressLint("SimpleDateFormat") SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss'Z'");
        df.setTimeZone(getTimeZone("UTC"));

        return base + "/Concept2Gatt_" + df.format(new Date()) + ".c2.gatt";
    }

    @Override
    protected void editCharacteristicValueBeforeLog(BluetoothGattCharacteristic characteristic, byte[] value) {
                    if (characteristic.getUuid().equals(C2_MULTIPLEXED_INFORMATION_CHARACTERISTIC_UUID) &&
                        value.length >= 10 &&
                        value[0] == 0x32) {
                        // Update heart rate from bluetooth hrm
                        if (value[7] == (byte) 0xff) {
                            value[7] = (byte) hrFromBluetoothHrm;
                        }
                    }
                    if (characteristic.getUuid().equals(C2_MULTIPLEXED_INFORMATION_CHARACTERISTIC_UUID) &&
                            value.length >= 10 &&
                            value[0] == 0x38) {
                        // Update split/interval work heart rate from bluetooth hrm
                        if (value[5] == 0) {
                            value[5] = (byte) hrFromBluetoothHrm;
                        }
                    }
    }

    @Override
    protected long getConnectionTimeoutPeriod() {
        return 5000;
    }

    @Override
    protected int getDeviceType() { return PM5; }

    public void receiveHeartRate(int hr) {
        hrFromBluetoothHrm = hr;
    }

}

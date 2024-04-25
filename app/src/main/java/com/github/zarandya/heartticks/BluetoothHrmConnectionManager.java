package com.github.zarandya.heartticks;

import static com.github.zarandya.heartticks.db.BluetoothDeviceType.HRM;
import static java.util.TimeZone.getTimeZone;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

public class BluetoothHrmConnectionManager extends BluetoothConnectionManager {

    public static final String ACTION_CONNECT_BUTTON_PRESSED = BluetoothConnectionManager.ACTION_CONNECT_BUTTON_PRESSED;
    public static final String ACTION_QUERY_STATE = BluetoothConnectionManager.ACTION_QUERY_STATE;
    public static final String ACTION_HRM_SERVICE_STATE_CHANGED = "action_hrm_service_state_changed";
    public static final String EXTRA_SERVICE_STATE = BluetoothConnectionManager.EXTRA_SERVICE_STATE;

    public static final String ACTION_HR_VALUE_UPDATE = "action_hr_value_changed";
    public static final String EXTRA_HR_VALUE = "extra_hr_value";

    static final UUID HR_SERVICE_UUID =   UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb");
    static final UUID HR_CHARACTERISTIC_UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");

    public static final String CHANNEL_ID = "io.github.zarandya.beatrate.BLUETOOTH_HRM_SERVICE_NOTIFICATION";
    private static final int SELECT_DEVICE_REQUEST_CODE_HRM = 0;

    public BluetoothHrmConnectionManager(@NonNull BluetoothService service, @NonNull BluetoothDevice device) {
        super(service, device);
        registerCharacteristic(HR_SERVICE_UUID, HR_CHARACTERISTIC_UUID, true);
    }

    @Override
    protected int getTimestampWritePeriod() {
        return 5000;
    }

    @Override
    protected String generateOutputFilename(String base) {
        @SuppressLint("SimpleDateFormat") SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss'Z'");
        df.setTimeZone(getTimeZone("UTC"));

        return base + "/HRMGatt_" + df.format(new Date()) + ".hrm.gatt";
    }

    @Override
    protected void characteristicChanged(BluetoothGattCharacteristic characteristic) {
                    try {
                        byte[] value = characteristic.getValue();
                        int hr = 0;
                        if ((value[0] & 1) == 0) {
                            hr = ((int) value[1]) & 0xFF;
                        } else {
                            hr = ((((int) value[2]) << 8) & 0xFF) | (((int) value[1]) & 0xFF);
                        }
                        service.sendHRValue(hr);
                    }
                    catch (Exception e) {}
    }

    @Override
    protected long getConnectionTimeoutPeriod() {
        return 5000;
    }

    @Override
    protected int getDeviceType() { return HRM; }

}

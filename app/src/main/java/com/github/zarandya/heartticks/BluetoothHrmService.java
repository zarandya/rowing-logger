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

public class BluetoothHrmService extends BaseBluetoothService {

    public static final String ACTION_CONNECT_BUTTON_PRESSED = BaseBluetoothService.ACTION_CONNECT_BUTTON_PRESSED;
    public static final String ACTION_QUERY_STATE = BaseBluetoothService.ACTION_QUERY_STATE;
    public static final String ACTION_HRM_SERVICE_STATE_CHANGED = "action_hrm_service_state_changed";
    public static final String EXTRA_SERVICE_STATE = BaseBluetoothService.EXTRA_SERVICE_STATE;

    public static final String ACTION_HR_VALUE_UPDATE = "action_hr_value_changed";
    public static final String EXTRA_HR_VALUE = "extra_hr_value";

    static final UUID HR_SERVICE_UUID =   UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb");
    static final UUID HR_CHARACTERISTIC_UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");

    public static final String CHANNEL_ID = "io.github.zarandya.beatrate.BLUETOOTH_HRM_SERVICE_NOTIFICATION";
    private static final int SELECT_DEVICE_REQUEST_CODE_HRM = 0;

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

        registerCharacteristic(HR_SERVICE_UUID, HR_CHARACTERISTIC_UUID, true);
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
                        sendHRValue(hr);
                    }
                    catch (Exception e) {}
    }

    @Override
    protected long getConnectionTimeoutPeriod() {
        return 5000;
    }

    @Override
    protected int getDeviceType() { return HRM; }

    @Override
    protected void sendState() {
        Intent broadcast = new Intent(ACTION_HRM_SERVICE_STATE_CHANGED);
        broadcast.putExtra(EXTRA_SERVICE_STATE, getState());
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    private void sendHRValue(int hr) {
        Intent broadcast = new Intent(ACTION_HR_VALUE_UPDATE);
        broadcast.putExtra(EXTRA_HR_VALUE, hr);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

}

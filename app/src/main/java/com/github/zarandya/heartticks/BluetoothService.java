package com.github.zarandya.heartticks;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import static android.app.NotificationManager.IMPORTANCE_LOW;
import static android.bluetooth.BluetoothGatt.GATT_SUCCESS;
import static android.bluetooth.BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
import static android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
import static android.bluetooth.BluetoothProfile.STATE_CONNECTED;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTED;
import static android.os.Build.VERSION.SDK_INT;
import static androidx.core.app.NotificationCompat.PRIORITY_LOW;
import static com.github.zarandya.heartticks.BluetoothHrmService.ACTION_HR_VALUE_UPDATE;
import static com.github.zarandya.heartticks.BluetoothHrmService.EXTRA_HR_VALUE;
import static com.github.zarandya.heartticks.MainActivity.ACTION_SET_FILE_TO_SEND;
import static com.github.zarandya.heartticks.MainActivity.EXTRA_DEVICE_NAME;
import static com.github.zarandya.heartticks.MainActivity.EXTRA_FILE_TO_SEND;
import static com.github.zarandya.heartticks.R.string.bluetooth_service_channel_name;
import static java.util.TimeZone.getTimeZone;

public class BluetoothService extends Service {

    public static final int SERVICE_STATE_IDLE = 0;
    public static final int SERVICE_STATE_CONNECTING = 1;
    public static final int SERVICE_STATE_SUBSCRIBING = 2;
    public static final int SERVICE_STATE_CONNECTED = 3;
    public static final int SERVICE_STATE_DISCONNECTING = 4;

    public static final String ACTION_CONNECT_BUTTON_PRESSED = "action_connect_button_pressed";
    public static final String ACTION_QUERY_STATE = "sction_query_state";
    public static final String ACTION_SERVICE_STATE_CHANGED = "action_service_change_state";
    public static final String EXTRA_SERVICE_STATE = "extra_service_state";
    
    public static final int NOTIFICATION_ID = 83;
    public static final String CHANNEL_ID = "io.github.zarandya.beatrate.BLUETOOTH_SERVICE_NOTIFICATION";

    private int state = SERVICE_STATE_IDLE;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private GattoolFileWriter gattCallback;
    private String filename;

    private String deviceName = null;

    private int hrFromBluetoothHrm = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        
        if (SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, getString(bluetooth_service_channel_name), IMPORTANCE_LOW);
            channel.setDescription(getString(bluetooth_service_channel_name));
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(channel);
        }

        new Timer().scheduleAtFixedRate(new TimerTask() {
            @SuppressLint("SimpleDateFormat")
            final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

            {df.setTimeZone(getTimeZone("UTC"));}

            @Override
            public void run() {
                final GattoolFileWriter gaf = gattCallback;
                if (gaf != null) {
                    gaf.writeTime("time "+ df.format(new Date()));
                }
            }
        }, 5000, 5000);

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_HR_VALUE_UPDATE);
        registerReceiver(receiver, filter);
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getAction().equals(ACTION_CONNECT_BUTTON_PRESSED)) {
            if (state == SERVICE_STATE_IDLE) {
                deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME);
                startConnect();
            }
            else if (state == SERVICE_STATE_CONNECTED) {
                startDisconnect();
            }
        }
        else if (intent.getAction().equals(ACTION_QUERY_STATE)) {
            sendState();
        }
        return START_NOT_STICKY;
    }
    
    private void startConnect() {
        setState(SERVICE_STATE_CONNECTING);
        try {
            @SuppressLint("SimpleDateFormat") SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss'Z'");
            df.setTimeZone(getTimeZone("UTC"));

            File externalGatt = new File(ContextCompat.getExternalFilesDirs(this, null)[0].getPath() + "/gatt");
            if (!externalGatt.isDirectory()) {
                externalGatt.mkdir();
            }
            filename = externalGatt.getPath() + "/Concept2Gatt_" + df.format(new Date()) + ".c2.gatt";


            Set<BluetoothDevice> devices = bluetoothAdapter.getBondedDevices();
            for (BluetoothDevice dev : devices) {
                String name = null;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    name = dev.getAlias();
                }
                if (name == null || name.length() == 0)
                    name = dev.getName();
                if ((deviceName == null) ? dev.getName().startsWith("PM5") : deviceName.equals(name)) {
                    String mac = dev.getAddress();
                    createGattCallback(filename, mac);
                    connect(dev);
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            errDisconnect();
        }
    }
    
    private void startDisconnect() {
        setState(SERVICE_STATE_DISCONNECTING);
        try {
            unsubscribe(bluetoothGatt, C2_PM_CONTROL_SERVICE_UUID, C2_MULTIPLEXED_INFORMATION_CHARACTERISTIC_UUID);
        }
        catch (Exception e) {
            errDisconnect();
        }
    }

    private void errDisconnect() {
        final BluetoothGatt g = bluetoothGatt;
        if (g != null) {
            g.close();
        }
        bluetoothGatt = null;
        final GattoolFileWriter gt = gattCallback;
        if (gt != null) {
            gt.close();
        }
        gattCallback = null;
        sendFileToShare();
        setState(SERVICE_STATE_IDLE);
    }

    private boolean connected = false;
    static final UUID HR_SERVICE_UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb");
    static final UUID GATT_CLIENT_CONFIGURATION_CHARACTERISTIC_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    static final UUID HR_CHARACTERISTIC_UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");

    static final UUID C2_PM_CONTROL_SERVICE_UUID = UUID.fromString("ce060030-43e5-11e4-916c-0800200c9a66");
    static final UUID C2_MULTIPLEXED_INFORMATION_CHARACTERISTIC_UUID = UUID.fromString("ce060080-43e5-11e4-916c-0800200c9a66");
    static final UUID C2_FORCE_CURVE_DATA_CHARACTERISTIC_UUID = UUID.fromString("ce06003d-43e5-11e4-916c-0800200c9a66");


    private synchronized void connect(BluetoothDevice device) {
        if (gattCallback == null)
            return;
        bluetoothGatt = device.connectGatt(this, false, gattCallback);
        setState(SERVICE_STATE_SUBSCRIBING);
    }

    private void subscribe(BluetoothGatt gatt, UUID serviceId, UUID characteristicId) {
        final BluetoothGattService service = gatt.getService(serviceId);
        final BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicId);
        final BluetoothGattDescriptor descriptor = characteristic.getDescriptor(GATT_CLIENT_CONFIGURATION_CHARACTERISTIC_UUID);
        gatt.setCharacteristicNotification(characteristic, true);
        descriptor.setValue(ENABLE_NOTIFICATION_VALUE);
        gatt.writeDescriptor(descriptor);
    }

    private void unsubscribe(BluetoothGatt gatt, UUID serviceId, UUID characteristicId) {
        final BluetoothGattService service = gatt.getService(serviceId);
        final BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicId);
        final BluetoothGattDescriptor descriptor = characteristic.getDescriptor(GATT_CLIENT_CONFIGURATION_CHARACTERISTIC_UUID);
        gatt.setCharacteristicNotification(characteristic, false);
        descriptor.setValue(DISABLE_NOTIFICATION_VALUE);
        gatt.writeDescriptor(descriptor);
    }

    private void createGattCallback(String filename, String mac) {
        try {
            gattCallback = new GattoolFileWriter(filename, "[" + mac.toLowerCase() + "][LE]> ") {
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
                public void onPhyUpdate(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
                    super.onPhyUpdate(gatt, txPhy, rxPhy, status);
                    Log.d("GATT", "UPDATE tx: " + txPhy + " rx: " + rxPhy + " status: " + status);
                }

                @Override
                public void onPhyRead(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
                    super.onPhyRead(gatt, txPhy, rxPhy, status);
                    Log.d("GATT", "READ tx: " + txPhy + " rx: " + rxPhy + " status: " + status);
                }

                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    super.onConnectionStateChange(gatt, status, newState);
                    Log.d("GATT", "CONNECTION STATE CHANGE newState: " + newState + " status: " + status);
                    if (newState == STATE_CONNECTED) {
                        gatt.discoverServices();
                    }
                    if (newState == STATE_DISCONNECTED && (state == SERVICE_STATE_CONNECTED || state == SERVICE_STATE_SUBSCRIBING)) {
                        connect(gatt.getDevice());
                    }
                }

                @Override
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    super.onServicesDiscovered(gatt, status);
                    Log.d("GATT", "SERVICES DISCOVERED status: " + status);
                    Log.d("Discovery", "listing services");
                    for (BluetoothGattService service : gatt.getServices()) {
                        Log.d("Discovery", "SERVICE " +  service.getUuid().toString() + " " + service.getInstanceId());
                        for (BluetoothGattCharacteristic c : service.getCharacteristics()) {
                            Log.d("Discovery", "CHARACTERISTIC " + c.getUuid().toString() + " " + c.getInstanceId());
                            for (BluetoothGattDescriptor d : c.getDescriptors()) {
                                Log.d("Discovery", "DESCRIPTOR " + d.getUuid().toString() + " " + d.describeContents());
                            }
                        }
                    }

                    setState(SERVICE_STATE_SUBSCRIBING);
                    subscribe(gatt, C2_PM_CONTROL_SERVICE_UUID, C2_MULTIPLEXED_INFORMATION_CHARACTERISTIC_UUID);
                }

                @Override
                public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                    super.onCharacteristicRead(gatt, characteristic, status);
                    Log.d("GATT", "CHARACTERISTIC READ: " + characteristic + " status: " + status);
                    Log.d("CHARACTERISTIC", characteristic.getStringValue(0));
                }

                @Override
                public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                    super.onCharacteristicWrite(gatt, characteristic, status);
                    Log.d("GATT", "CHARACTERISTIC WRITE: " + characteristic + " status: " + status);
                }

                @Override
                public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                    super.onCharacteristicChanged(gatt, characteristic);
                    StringBuilder b = new StringBuilder();
                    for (byte v : characteristic.getValue()) {
                        b.append(String.format("%2x ", v));
                    }
                    Log.d("GATT", "CHARACTERISTIC CHANGED: " + characteristic.getInstanceId() + " " + b.toString());
                }

                @Override
                public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                    super.onDescriptorRead(gatt, descriptor, status);
                    Log.d("GATT", "DESCRIPTOR READ: " + descriptor + " status: " + status);
                }

                @Override
                public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                    super.onDescriptorWrite(gatt, descriptor, status);
                    Log.d("GATT", "DESCRIPTOR WRITE: " + descriptor.getUuid() + "value: " + Arrays.toString(descriptor.getValue()) + " status: " + status);
                    if (status == GATT_SUCCESS) {
                        if (state == SERVICE_STATE_SUBSCRIBING) {
                            UUID uuid = descriptor.getCharacteristic().getUuid();
                            if (uuid.equals(C2_MULTIPLEXED_INFORMATION_CHARACTERISTIC_UUID)) {
                                subscribe(gatt, C2_PM_CONTROL_SERVICE_UUID, C2_FORCE_CURVE_DATA_CHARACTERISTIC_UUID);
                            }
                            else if (uuid.equals(C2_FORCE_CURVE_DATA_CHARACTERISTIC_UUID)) {
                                setState(SERVICE_STATE_CONNECTED);
                            }
                        }
                        else if (state == SERVICE_STATE_DISCONNECTING) {
                            UUID uuid = descriptor.getCharacteristic().getUuid();
                            if (uuid.equals(C2_MULTIPLEXED_INFORMATION_CHARACTERISTIC_UUID)) {
                                unsubscribe(gatt, C2_PM_CONTROL_SERVICE_UUID, C2_FORCE_CURVE_DATA_CHARACTERISTIC_UUID);
                            }
                            else if (uuid.equals(C2_FORCE_CURVE_DATA_CHARACTERISTIC_UUID)) {
                                bluetoothGatt.disconnect();
                                bluetoothGatt.close();
                                bluetoothGatt = null;
                                gattCallback.close();
                                gattCallback = null;
                                sendFileToShare();
                                setState(SERVICE_STATE_IDLE);
                            }
                        }
                    }
                    else {
                        errDisconnect();
                    }
                }

                @Override
                public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
                    super.onReliableWriteCompleted(gatt, status);
                }

                @Override
                public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
                    super.onReadRemoteRssi(gatt, rssi, status);
                }

                @Override
                public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
                    super.onMtuChanged(gatt, mtu, status);
                }
            };
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int getState() {
        return state;
    }
    
    private volatile long stateChangeCounter = 0;

    private void setState(final int s) {
        Log.d("STATE", String.valueOf(s));
        try {
            state = s;
            ++stateChangeCounter;
            final long stateChangeCount = stateChangeCounter;
            if (s == SERVICE_STATE_CONNECTED) {
                Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                        .setContentText("Connected to " + bluetoothGatt.getDevice().getName())
                        .setContentText("")
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(""))
                        .setPriority(PRIORITY_LOW)
                        .build();
                startForeground(83, notification);
            } else {
                stopForeground(true);
            }
            if (s != SERVICE_STATE_CONNECTED && s != SERVICE_STATE_IDLE) {
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        Log.d("TIMEOUT", "state: " + state + " s: " + s + " counter: " + stateChangeCounter + " count: " + stateChangeCount);
                        if (stateChangeCounter == stateChangeCount) {
                            errDisconnect();
                        }
                    }
                }, 5000);
            }
            sendState();
        }
        catch (Exception e) {
            e.printStackTrace();
            errDisconnect();
        }
    }

    private void sendState() {
        Intent broadcast = new Intent(ACTION_SERVICE_STATE_CHANGED);
        broadcast.putExtra(EXTRA_SERVICE_STATE, state);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    private void sendFileToShare() {
        Intent broadcast = new Intent(ACTION_SET_FILE_TO_SEND);
        broadcast.putExtra(EXTRA_FILE_TO_SEND, filename);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Don't forget to unregister the ACTION_FOUND receiver.
        unregisterReceiver(receiver);
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ACTION_HR_VALUE_UPDATE)) {
                hrFromBluetoothHrm = intent.getIntExtra(EXTRA_HR_VALUE, 0);
            }
        }
    };
}
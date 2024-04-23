package com.github.zarandya.heartticks;

import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.app.NotificationManager.IMPORTANCE_LOW;
import static android.bluetooth.BluetoothGatt.GATT_SUCCESS;
import static android.bluetooth.BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
import static android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
import static android.bluetooth.BluetoothProfile.STATE_CONNECTED;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.Build.VERSION.SDK_INT;
import static androidx.core.app.NotificationCompat.PRIORITY_LOW;
import static com.github.zarandya.heartticks.MainActivity.ACTION_SET_FILE_TO_SEND;
import static com.github.zarandya.heartticks.MainActivity.EXTRA_DEVICE;
import static com.github.zarandya.heartticks.MainActivity.EXTRA_FILE_TO_SEND;
import static com.github.zarandya.heartticks.R.string.bluetooth_service_channel_name;
import static java.util.TimeZone.getTimeZone;

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
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.github.zarandya.heartticks.db.DevicesDao;
import com.github.zarandya.heartticks.db.SavedBluetoothDevice;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import kotlin.Pair;

public abstract class BaseBluetoothService extends Service {
    public static final int SERVICE_STATE_IDLE = 0;
    public static final int SERVICE_STATE_CONNECTING = 1;
    public static final int SERVICE_STATE_SUBSCRIBING = 2;
    public static final int SERVICE_STATE_CONNECTED = 3;
    public static final int SERVICE_STATE_DISCONNECTING = 4;

    public static final String ACTION_CONNECT_BUTTON_PRESSED = "action_connect_button_pressed";
    public static final String ACTION_QUERY_STATE = "sction_query_state";
    public static final String EXTRA_SERVICE_STATE = "extra_service_state";

    public static final int NOTIFICATION_ID = 83;

    private static final UUID GATT_CLIENT_CONFIGURATION_CHARACTERISTIC_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");


    private int state = SERVICE_STATE_IDLE;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private GattoolFileWriter gattCallback;
    private String filename;

    private BluetoothDevice device = null;

    protected abstract String getNotificationChannelId();

    protected abstract int getTimestampWritePeriod();

    @Override
    public void onCreate() {
        super.onCreate();

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(getNotificationChannelId(), getString(bluetooth_service_channel_name), IMPORTANCE_LOW);
            channel.setDescription(getString(bluetooth_service_channel_name));
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(channel);
        }

        new Timer().scheduleAtFixedRate(new TimerTask() {
            @SuppressLint("SimpleDateFormat")
            final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

            {
                df.setTimeZone(getTimeZone("UTC"));
            }

            @Override
            public void run() {
                final GattoolFileWriter gaf = gattCallback;
                if (gaf != null) {
                    gaf.writeTime("time " + df.format(new Date()));
                }
            }
        }, getTimestampWritePeriod(), getTimestampWritePeriod()); // TODO this was 5000 for ergs and 500 for HRM, why?
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getAction().equals(ACTION_CONNECT_BUTTON_PRESSED)) {
            if (state == SERVICE_STATE_IDLE) {
                device = intent.getParcelableExtra(EXTRA_DEVICE);
                startConnect();
            } else if (state == SERVICE_STATE_CONNECTED) {
                startDisconnect();
            }
        } else if (intent.getAction().equals(ACTION_QUERY_STATE)) {
            sendState();
        }
        return START_NOT_STICKY;
    }

    protected abstract String generateOutputFilename(String Base);

    private void startConnect() {
        if (device == null)
            return;
        setState(SERVICE_STATE_CONNECTING);
        try {
            File externalGatt = new File(ContextCompat.getExternalFilesDirs(this, null)[0].getPath() + "/gatt");
            if (!externalGatt.isDirectory()) {
                externalGatt.mkdir();
            }
            filename = generateOutputFilename(externalGatt.getPath());

            createGattCallback(filename, device.getAddress());
            connect(device);

        } catch (Exception e) {
            e.printStackTrace();
            if (gattCallback != null) gattCallback.debugLog(e);
            errDisconnect();
        }
    }

    private List<Pair<UUID, UUID>> requiredCharacteristics = new ArrayList<>();
    private List<Pair<UUID, UUID>> optionalCharacteristics = new ArrayList<>();
    private List<Pair<UUID, UUID>> connectedCharacteristics = new ArrayList<>();
    private int requiredSubscribingIndx = 0;
    private int optionalSubscribingIndx = 0;

    protected void registerCharacteristic(UUID serviceUUID, UUID uuid, boolean required) {
        if (required)
            requiredCharacteristics.add(new Pair(serviceUUID, uuid));
        else
            optionalCharacteristics.add(new Pair(serviceUUID, uuid));
    }

    protected void registerCharacteristicForReadOnce(UUID serviceUUID, UUID uuid) {
        // TODO implement this
    }

    private void startDisconnect() {
        setState(SERVICE_STATE_DISCONNECTING);
        try {
            Pair<UUID, UUID> ch = connectedCharacteristics.get(0);
            unsubscribe(bluetoothGatt, ch.getFirst(), ch.getSecond());
        } catch (Exception e) {
            if (gattCallback != null) gattCallback.debugLog(e);
            errDisconnect();
        }
    }

    private void errDisconnect() {
        final BluetoothGatt g = bluetoothGatt;
        if (g != null) {
            if (ActivityCompat.checkSelfPermission(this, BLUETOOTH_CONNECT) != PERMISSION_GRANTED)
                throw new RuntimeException("The service should not have started with missing permissions");
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


    private synchronized void connect(BluetoothDevice device) {
        if (gattCallback == null)
            return;
        if (ActivityCompat.checkSelfPermission(this, BLUETOOTH_CONNECT) != PERMISSION_GRANTED)
            throw new RuntimeException("The service should not have started with missing permissions");
        bluetoothGatt = device.connectGatt(this, false, gattCallback);
        setState(SERVICE_STATE_SUBSCRIBING);
    }

    protected void startSubscribe(BluetoothGatt gatt) {
        setState(SERVICE_STATE_SUBSCRIBING);
        requiredSubscribingIndx = 0;
        optionalSubscribingIndx = 0;
        subscribeToNext(gatt);
    }

    private void onSuccessfulSubscribe(BluetoothGatt gatt, UUID serviceUUID, UUID uuid) {
        if (requiredSubscribingIndx < requiredCharacteristics.size()) {
            Pair<UUID, UUID> ch = requiredCharacteristics.get(requiredSubscribingIndx);
            if (serviceUUID.equals(ch.getFirst()) && uuid.equals(ch.getSecond())) {
                connectedCharacteristics.add(ch);
                ++requiredSubscribingIndx;
                subscribeToNext(gatt);
            }
        } else {
            Pair<UUID, UUID> ch = optionalCharacteristics.get(optionalSubscribingIndx);
            if (serviceUUID.equals(ch.getFirst()) && uuid.equals(ch.getSecond())) {
                connectedCharacteristics.add(ch);
                ++optionalSubscribingIndx;
                subscribeToNext(gatt);
            }
        }
    }

    private void subscribeToNext(BluetoothGatt gatt) {
        if (requiredSubscribingIndx < requiredCharacteristics.size()) {
            Pair<UUID, UUID> ch = requiredCharacteristics.get(requiredSubscribingIndx);
            subscribe(gatt, ch.getFirst(), ch.getSecond());
        } else {
            boolean characteristicExists = false;
            while (optionalSubscribingIndx < optionalCharacteristics.size() &&
                    !characteristicExists) {
                Pair<UUID, UUID> ch = optionalCharacteristics.get(optionalSubscribingIndx);
                characteristicExists =
                        subscribe(gatt, ch.getFirst(), ch.getSecond());
                if (!characteristicExists)
                    ++optionalSubscribingIndx;
            }

            if (!characteristicExists) {
                setState(SERVICE_STATE_CONNECTED);
            }
        }
    }

    private boolean subscribe(BluetoothGatt gatt, UUID serviceId, UUID characteristicId) {
        final BluetoothGattService service = gatt.getService(serviceId);
        if (service == null)
            return false;
        final BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicId);
        if (characteristic == null)
            return false;
        final BluetoothGattDescriptor descriptor = characteristic.getDescriptor(GATT_CLIENT_CONFIGURATION_CHARACTERISTIC_UUID);
        if (descriptor == null)
            return false;
        if (ActivityCompat.checkSelfPermission(this, BLUETOOTH_CONNECT) != PERMISSION_GRANTED)
            throw new RuntimeException("The service should not have started with missing permissions");
        gatt.setCharacteristicNotification(characteristic, true);
        descriptor.setValue(ENABLE_NOTIFICATION_VALUE);
        gatt.writeDescriptor(descriptor);
        return true;
    }

    private boolean onSuccessfulUnsubscribe(BluetoothGatt gatt, UUID serviceUUID, UUID uuid) {
        Pair<UUID, UUID> ch = connectedCharacteristics.get(0);
        if (serviceUUID.equals(ch.getFirst()) && uuid.equals(ch.getSecond())) {
            connectedCharacteristics.remove(0);

            if (!connectedCharacteristics.isEmpty()) {
                ch = connectedCharacteristics.get(0);
                unsubscribe(gatt, ch.getFirst(), ch.getSecond());
            } else
                return true;    // we are done
        }
        return false;
    }

    private boolean unsubscribe(BluetoothGatt gatt, UUID serviceId, UUID characteristicId) {
        final BluetoothGattService service = gatt.getService(serviceId);
        if (service == null)
            return false;
        final BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicId);
        if (characteristic == null)
            return false;
        final BluetoothGattDescriptor descriptor = characteristic.getDescriptor(GATT_CLIENT_CONFIGURATION_CHARACTERISTIC_UUID);
        if (descriptor == null)
            return false;
        if (ActivityCompat.checkSelfPermission(this, BLUETOOTH_CONNECT) != PERMISSION_GRANTED)
            throw new RuntimeException("The service should not have started with missing permissions");
        gatt.setCharacteristicNotification(characteristic, false);
        descriptor.setValue(DISABLE_NOTIFICATION_VALUE);
        gatt.writeDescriptor(descriptor);
        return true;
    }

    protected void editCharacteristicValueBeforeLog(BluetoothGattCharacteristic characteristic, byte[] value) {
        // Do nothing by default
    }

    protected void characteristicChanged(BluetoothGattCharacteristic characteristic) {
    }

    private void createGattCallback(String filename, String mac) {
        try {
            gattCallback = new GattoolFileWriter(filename, "[" + mac.toLowerCase() + "][LE]> ") {
                @Override
                protected void editCharacteristicValueBeforeLog(BluetoothGattCharacteristic characteristic, byte[] value) {
                    BaseBluetoothService.this.editCharacteristicValueBeforeLog(characteristic, value);
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
                        if (ActivityCompat.checkSelfPermission(BaseBluetoothService.this, BLUETOOTH_CONNECT) != PERMISSION_GRANTED)
                            throw new RuntimeException("The service should not have started with missing permissions");
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
                        Log.d("Discovery", "SERVICE " + service.getUuid().toString() + " " + service.getInstanceId());
                        for (BluetoothGattCharacteristic c : service.getCharacteristics()) {
                            Log.d("Discovery", "CHARACTERISTIC " + c.getUuid().toString() + " " + c.getInstanceId());
                            for (BluetoothGattDescriptor d : c.getDescriptors()) {
                                Log.d("Discovery", "DESCRIPTOR " + d.getUuid().toString() + " " + d.describeContents());
                            }
                        }
                    }

                    startSubscribe(gatt);

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
                    characteristicChanged(characteristic);
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
                            UUID serviceUUID = descriptor.getCharacteristic().getService().getUuid();
                            UUID uuid = descriptor.getCharacteristic().getUuid();
                            onSuccessfulSubscribe(gatt, serviceUUID, uuid);
                        } else if (state == SERVICE_STATE_DISCONNECTING) {
                            UUID serviceUUID = descriptor.getCharacteristic().getService().getUuid();
                            UUID uuid = descriptor.getCharacteristic().getUuid();
                            boolean finishDisconnect = onSuccessfulUnsubscribe(gatt, serviceUUID, uuid);
                            if (finishDisconnect) {
                                if (ActivityCompat.checkSelfPermission(BaseBluetoothService.this, BLUETOOTH_CONNECT) != PERMISSION_GRANTED)
                                    throw new RuntimeException("The service should not have started with missing permissions");
                                bluetoothGatt.disconnect();
                                bluetoothGatt.close();
                                bluetoothGatt = null;
                                gattCallback.close();
                                gattCallback = null;
                                sendFileToShare();
                                setState(SERVICE_STATE_IDLE);
                            }
                        }
                    } else {
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
            if (gattCallback != null) gattCallback.debugLog(e);
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
                if (ActivityCompat.checkSelfPermission(this, BLUETOOTH_CONNECT) != PERMISSION_GRANTED)
                    throw new RuntimeException("The service should not have started with missing permissions");
                Notification notification = new NotificationCompat.Builder(this, getNotificationChannelId())
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
                }, getConnectionTimeoutPeriod());
            }
            if (s == SERVICE_STATE_CONNECTED) {
                saveDeviceToDatabase();
            }
            sendState();
        } catch (Exception e) {
            e.printStackTrace();
            if (gattCallback != null) gattCallback.debugLog(e);
            errDisconnect();
        }
    }

    protected abstract long getConnectionTimeoutPeriod();

    protected abstract int getDeviceType();

    protected abstract void sendState();

    private void sendFileToShare() {
        Intent broadcast = new Intent(ACTION_SET_FILE_TO_SEND);
        broadcast.putExtra(EXTRA_FILE_TO_SEND, filename);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    private void saveDeviceToDatabase() {
        //if (ActivityCompat.checkSelfPermission(this, BLUETOOTH_CONNECT) != PERMISSION_GRANTED)
        //    throw new RuntimeException("The service should not have started with missing permissions");

        //device.createBond();

        new Thread(() -> {
            if (ActivityCompat.checkSelfPermission(this, BLUETOOTH_CONNECT) != PERMISSION_GRANTED)
                throw new RuntimeException("The service should not have started with missing permissions");
            int addressType;
            try {
                Method meth = BluetoothDevice.class.getMethod("getAddressType");
                meth.setAccessible(true);
                addressType = (int) meth.invoke(device);
            }
            catch (Exception e) {
                addressType = -3;
            }

            try {
                DevicesDao dao = App.getDB().getDevicesDao();

                String alias = (SDK_INT >= Build.VERSION_CODES.R) ? device.getAlias() : null;
                if (alias == null)
                    alias = device.getName();

                dao.removeDevicesWithDisplayName(device.getName());

                dao.plusAssign(new SavedBluetoothDevice(
                        device.getAddress(),
                        device.getName(),
                        alias,
                        addressType,
                        getDeviceType()
                ));
            }
            catch (Exception e) {
                if (gattCallback != null) gattCallback.debugLog(e);
            }
        }).start();
    }
}

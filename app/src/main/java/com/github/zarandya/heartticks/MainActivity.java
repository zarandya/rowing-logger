package com.github.zarandya.heartticks;

import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.companion.AssociationRequest;
import android.companion.BluetoothDeviceFilter;
import android.companion.CompanionDeviceManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.TextView;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static android.Manifest.permission.BLUETOOTH_ADVERTISE;
import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.Manifest.permission.BLUETOOTH_SCAN;
import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.Build.VERSION.SDK_INT;
import static com.github.zarandya.heartticks.AccelerometerService.ACTION_ACCEL_LOG_BUTTON_PRESSED;
import static com.github.zarandya.heartticks.AccelerometerService.ACTION_RATE_UPDATE;
import static com.github.zarandya.heartticks.AccelerometerService.EXTRA_RATE;
import static com.github.zarandya.heartticks.BluetoothBikeConnectionManager.ACTION_BIKE_SERVICE_STATE_CHANGED;
import static com.github.zarandya.heartticks.BluetoothHrmConnectionManager.ACTION_HRM_SERVICE_STATE_CHANGED;
import static com.github.zarandya.heartticks.BluetoothHrmConnectionManager.EXTRA_HR_VALUE;
import static com.github.zarandya.heartticks.BluetoothOarlockConnectionManager.ACTION_OARLOCK_SERVICE_STATE_CHANGED;
import static com.github.zarandya.heartticks.BluetoothPM5ConnectionManager.ACTION_CONNECT_BUTTON_PRESSED;
import static com.github.zarandya.heartticks.BluetoothPM5ConnectionManager.ACTION_QUERY_STATE;
import static com.github.zarandya.heartticks.BluetoothPM5ConnectionManager.ACTION_SERVICE_STATE_CHANGED;
import static com.github.zarandya.heartticks.BluetoothPM5ConnectionManager.EXTRA_SERVICE_STATE;
import static com.github.zarandya.heartticks.BluetoothPM5ConnectionManager.SERVICE_STATE_CONNECTED;
import static com.github.zarandya.heartticks.BluetoothPM5ConnectionManager.SERVICE_STATE_IDLE;
import static com.github.zarandya.heartticks.BluetoothHrmConnectionManager.ACTION_HR_VALUE_UPDATE;
import static com.github.zarandya.heartticks.BluetoothService.ACTION_BLUETOOTH_SERVICE_STATE_CHANGED;
import static com.github.zarandya.heartticks.BluetoothService.ACTION_CONNECT_DEVICE;
import static com.github.zarandya.heartticks.BluetoothService.ACTION_DISCONNECT_DEVICE;
import static com.github.zarandya.heartticks.BluetoothService.EXTRA_DEVICE_TYPE;
import static com.github.zarandya.heartticks.db.BluetoothDeviceType.BIKE;
import static com.github.zarandya.heartticks.db.BluetoothDeviceType.HRM;
import static com.github.zarandya.heartticks.db.BluetoothDeviceType.OARLOCK;
import static com.github.zarandya.heartticks.db.BluetoothDeviceType.PM5;
import static com.github.zarandya.heartticks.db.SavedBluetoothDeviceKt.getAliasOrName;
import static java.util.TimeZone.getTimeZone;

import com.github.zarandya.heartticks.db.SavedBluetoothDevice;
import com.github.zarandya.heartticks.db.SavedBluetoothDeviceKt;

public class MainActivity extends AppCompatActivity {

    public static final String SCAN_NEW_DEVICE = "-1";
    private Button button;
    private Button disconnectButton;
    private Button accelLogButton;
    private Button shareButton;
    private TextView rateTextView;
    private TextView statusTextView;

    private static final int REQUEST_ENABLE_BT = 201;
    private static final int REQUEST_STORAGE_PERMISSION = 201;
    private static final int REQUEST_BT_PERMISSION = 202;


    public static final String ACTION_SET_FILE_TO_SEND = "action_set_file_to_snd";
    public static final String EXTRA_FILE_TO_SEND = "extra_file_to_send";
    public static final String EXTRA_DEVICE = "extra_device_name";

    private String fileToSend = null;

    private BluetoothAdapter bluetoothAdapter;

    private BluetoothDevice[] devicesConnected = null;
    private BluetoothDevice[] devicesConnectedListedInMenu = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(ACTION_BLUETOOTH_SERVICE_STATE_CHANGED);
        filter.addAction(ACTION_RATE_UPDATE);
        filter.addAction(ACTION_SET_FILE_TO_SEND);
        filter.addAction(ACTION_HR_VALUE_UPDATE);
        registerReceiver(receiver, filter);
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter);

        if (ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) != PERMISSION_GRANTED) {
            requestPermissions(new String[]{READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
        }

        button = findViewById(R.id.button);
        button.setOnClickListener((v) -> {
            PopupMenu menu = new PopupMenu(this, button);
            Menu menuBuilder = menu.getMenu();

            menuBuilder.add(0, PM5, 0, "PM5");
            menuBuilder.add(0, HRM, 1, "HRM");
            menuBuilder.add(0, BIKE, 2, "Bike");
            menuBuilder.add(0, OARLOCK, 3, "Oarlock");

            menu.setOnMenuItemClickListener((item) -> {
                new Thread(() -> {
                    final List<SavedBluetoothDevice> savedDevices =
                            App.getDB().getDevicesDao().devicesOfKind(item.getItemId());
                    runOnUiThread(() ->
                            popupBluetoothDeviceSelector(button, savedDevices, item.getItemId()));
                }).start();
                return true;
            });

            menu.show();
        });

        disconnectButton = findViewById(R.id.disconnect_button);
        disconnectButton.setOnClickListener((v) -> {
            // This works because when the list of devices changes a new array is allocated
            devicesConnectedListedInMenu = devicesConnected;
            Log.d("DEVICES CONNECTED", devicesConnected.toString());
            if (devicesConnectedListedInMenu == null) return;

            PopupMenu menu = new PopupMenu(this, button);
            Menu menuBuilder = menu.getMenu();

            for (int i = 0; i < devicesConnectedListedInMenu.length; ++i) {
                String name;

                menuBuilder.add(0, i, i, getAliasOrName(devicesConnectedListedInMenu[i]));
            }

            menu.setOnMenuItemClickListener((item) -> {
                disconnectFromDevice(devicesConnectedListedInMenu[item.getItemId()]);
                return true;
            });

            menu.show();
        });

        accelLogButton = findViewById(R.id.accel_log_btn);
        accelLogButton.setOnClickListener((v) -> {
            Intent intent = new Intent(this, AccelerometerService.class);
            intent.setAction(ACTION_ACCEL_LOG_BUTTON_PRESSED);
            startService(intent);
        });

        shareButton = findViewById(R.id.share_btn);
        shareButton.setOnClickListener((v) -> {
            if (fileToSend != null) {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_STREAM,
                        FileProvider.getUriForFile(this,
                                "com.github.zarandya.heartticks.GattAclProvider",
                                new File(fileToSend)));
                intent.setType(fileToSend.endsWith(".gatt") ? "application/gatt" : "application/accellog");
                Intent shareIntent = Intent.createChooser(intent, null);
                startActivity(shareIntent);
            }
        });

        rateTextView = findViewById(R.id.rate_text_view);
        statusTextView = findViewById(R.id.status_text_view);

        Intent queryState = new Intent(this, BluetoothPM5ConnectionManager.class);
        queryState.setAction(ACTION_QUERY_STATE);
        startService(queryState);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    private boolean ensureBluetoothPermission() {
        if (SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, BLUETOOTH_CONNECT)   != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, BLUETOOTH_SCAN)      != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(new String[]{BLUETOOTH_CONNECT, BLUETOOTH_SCAN, BLUETOOTH_ADVERTISE}, REQUEST_BT_PERMISSION);
                return false;
            }
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Don't forget to unregister the ACTION_FOUND receiver.
        unregisterReceiver(receiver);
    }

    @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
    private void popupBluetoothDeviceSelector(View view, List<SavedBluetoothDevice> savedDevices, int kind) {
        final String prefix;
        switch (kind) {
            case PM5:
                prefix = "";
                break;
            case OARLOCK:
                prefix = ""; // EmPower
                break;
            default:
                prefix = "";
        }

        PopupMenu menu = new PopupMenu(this, view);
        Menu menuBuilder = menu.getMenu();

        List<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices()
                .stream().filter((dev) -> dev.getName().startsWith(prefix))
                .collect(Collectors.toList());

        int iid = 0;
        for (SavedBluetoothDevice dev : savedDevices) {
            menuBuilder.add(0, iid++, Menu.NONE, dev.getAlias());
        }

        final int scanIID = iid;
        menuBuilder.add(1, iid++, Menu.NONE, "... Scan for devices \n(Location must be enabled)");

        final int bondedBaseIID = iid;
        for (BluetoothDevice dev : bondedDevices) {
            String name = null;
            if (SDK_INT >= Build.VERSION_CODES.R) {
                name = dev.getAlias();
            }
            if (name == null || name.length() == 0)
                name = dev.getName();
            menuBuilder.add(2, iid++, Menu.NONE, name);
        }
        menu.setOnMenuItemClickListener((item) -> {
            final int selected = item.getItemId();
            boolean needsScan = false;
            String scanAddress = SCAN_NEW_DEVICE;
            BluetoothDevice dev = null;

            if (selected < scanIID) {
                SavedBluetoothDevice savedDevice = savedDevices.get(selected);
                for (BluetoothDevice dev2: bondedDevices) {
                   if (dev2.getAddress().equals(savedDevice.getAddress())) {
                       dev = dev2;
                       break;
                   }
                }
                if (dev == null) {
                    if (SDK_INT >= Build.VERSION_CODES.TIRAMISU && savedDevice.getAddressType() != -3) {
                        dev = bluetoothAdapter.getRemoteLeDevice(savedDevice.getAddress(),
                                savedDevice.getAddressType());
                    } else {
                        dev = bluetoothAdapter.getRemoteDevice(savedDevice.getAddress());
                        int type;
                        try {
                            Method meth = BluetoothDevice.class.getMethod("getAddressType");
                            meth.setAccessible(true);
                            type = (int) meth.invoke(dev);
                        } catch (Exception e) {
                            type = -2;
                        }
                        if (type != savedDevice.getAddressType()) {
                            needsScan = true;
                            scanAddress = savedDevice.getAddress();
                        }
                    }
                }
            } else if (selected >= bondedBaseIID) {
                dev = bondedDevices.get(selected - bondedBaseIID);
            } else {
                needsScan = true;
            }
            if (!needsScan) {
                connectToDevice(dev, kind);
            } else {
                if (SDK_INT >= Build.VERSION_CODES.O) {
                    scanDevice(scanAddress, kind, view);
                }
            }
            return true;
        });
        menu.show();
    }

    private void connectToDevice(BluetoothDevice device, int type) {
        Intent intent = new Intent(this, BluetoothService.class);
        intent.setAction(ACTION_CONNECT_DEVICE);
        intent.putExtra(EXTRA_DEVICE, device);
        intent.putExtra(EXTRA_DEVICE_TYPE, type);
        startService(intent);
    }

    private void disconnectFromDevice(BluetoothDevice device) {
        Intent intent = new Intent(this, BluetoothService.class);
        intent.setAction(ACTION_DISCONNECT_DEVICE);
        intent.putExtra(EXTRA_DEVICE, device);
        startService(intent);
    }

    private int companionScanNextDeviceType = -1;
    private PopupMenu scanDevicePopupMenu = null;
    private int scanDevicePopupMenuIID = 0;
    private ArrayList<BluetoothDevice> scannedDevices = null;
    private String scanAddress = null;

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void scanDevice(String deviceAddress, int type, View view) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        synchronized (this) {
            if (companionScanNextDeviceType != -1) {
                Log.e("Companion", "A companion scan is already running");
                return;
            }
            companionScanNextDeviceType = type;
        }

        if (SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, BLUETOOTH_CONNECT) != PERMISSION_GRANTED)
                throw new RuntimeException("The function should not have been called with missing permissions");

            scanAddress = deviceAddress;

            synchronized (this) {
                scanDevicePopupMenu = new PopupMenu(this, view);
            }
            Menu menu = scanDevicePopupMenu.getMenu();
            menu.add(0, 0, Menu.NONE, "Scanning...");
            menu.setGroupEnabled(0, false);
            scanDevicePopupMenuIID = 1;
            scannedDevices = new ArrayList<>();

            scanDevicePopupMenu.setOnDismissListener((m) -> {
                synchronized (this) {
                    companionScanNextDeviceType = -1;
                    scanDevicePopupMenu = null;
                }
                bluetoothAdapter.cancelDiscovery();
            });

            scanDevicePopupMenu.setOnMenuItemClickListener((item) -> {
                BluetoothDevice device = scannedDevices.get(item.getItemId() - 1);
                synchronized (this) {
                    scanDevicePopupMenu = null;
                }
                bluetoothAdapter.cancelDiscovery();
                connectDeviceAfterCompanionScan(device);
                return true;
            });

            scanDevicePopupMenu.show();
            bluetoothAdapter.startDiscovery();
            return;
        }

        BluetoothDeviceFilter.Builder builder = new BluetoothDeviceFilter.Builder();

        if (!deviceAddress.equals(SCAN_NEW_DEVICE)) {
            builder.setAddress(deviceAddress);
        }

        AssociationRequest.Builder builder2 = new AssociationRequest.Builder()
                .addDeviceFilter(builder.build());

        if (!deviceAddress.equals(SCAN_NEW_DEVICE)) {
            builder2.setSingleDevice(true);
        }

        CompanionDeviceManager deviceManager =
                (CompanionDeviceManager) getSystemService(Context.COMPANION_DEVICE_SERVICE);

        CompanionDeviceManager.Callback callback = new CompanionDeviceManager.Callback() {
            @Override
            public void onDeviceFound(IntentSender chooserLauncher) {
                try {
                    startIntentSenderForResult(
                            chooserLauncher, SELECT_DEVICE_REQUEST_CODE, null, 0, 0, 0
                    );
                } catch (IntentSender.SendIntentException e) {
                    Log.e("MainActivity", "Failed to send intent");
                }
            }

            @Override
            public void onFailure(CharSequence error) {
                // Handle the failure.
                synchronized (this) {
                    companionScanNextDeviceType = -1;
                }
            }
        };

        if (SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            deviceManager.associate(builder2.build(), Runnable::run, callback);
        }
        else {
            deviceManager.associate(builder2.build(), callback, null);
        }


    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d("BLUETOOTH", action);
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Discovery has found a device. Get the BluetoothDevice
                // object and its info from the Intent.
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address
                Log.d("BLUETOOTH", deviceName + " " + deviceHardwareAddress);
                synchronized (this) {
                    if (scanDevicePopupMenu != null &&
                            scanAddress.equals(SCAN_NEW_DEVICE) &&
                            device.getName() != null &&
                            !device.getName().isEmpty()) {
                        scannedDevices.add(device);
                        Menu menu = scanDevicePopupMenu.getMenu();
                        menu.add(1, scanDevicePopupMenuIID++, Menu.NONE, device.getName());
                    }
                }
                if (device.getAddress().equals(scanAddress)) {
                    connectDeviceAfterCompanionScan(device);
                    scanDevicePopupMenu.dismiss();
                }
            } else if (action.equals(ACTION_BLUETOOTH_SERVICE_STATE_CHANGED)) {
                int[] states = intent.getIntArrayExtra(EXTRA_SERVICE_STATE);
                int[] deviceTypes = intent.getIntArrayExtra(EXTRA_DEVICE_TYPE);
                Parcelable[] devices = intent.getParcelableArrayExtra(EXTRA_DEVICE);

                statusTextView.setText(String.format("Connected %d devices", states.length));

                // Allocates a new array when the list of devices changes.
                // If this changes, see assignment of devicesConnectedListedInMenu
                devicesConnected = Arrays.stream(devices).map(d -> (BluetoothDevice) d)
                        .toArray(BluetoothDevice[]::new);
            } else if (action.equals(ACTION_RATE_UPDATE)) {
                rateTextView.setText(String.valueOf(intent.getIntExtra(EXTRA_RATE, 0)));
            } else if (action.equals(ACTION_HR_VALUE_UPDATE)) {
                rateTextView.setText(String.valueOf(intent.getIntExtra(EXTRA_HR_VALUE, 0)));
            } else if (action.equals(ACTION_SET_FILE_TO_SEND)) {
                fileToSend = intent.getStringExtra(EXTRA_FILE_TO_SEND);
                shareButton.setText("Share " + fileToSend);
            }
        }
    };

    private void connectDeviceAfterCompanionScan(BluetoothDevice device) {
        final int type;
        synchronized (this) {
            type = companionScanNextDeviceType;
            companionScanNextDeviceType = -1;
        }

        if (type != -1) {
            connectToDevice(device, type);
        }
    }

    private static final int SELECT_DEVICE_REQUEST_CODE = 42;

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SELECT_DEVICE_REQUEST_CODE &&
                resultCode == Activity.RESULT_OK) {
            // User has chosen to pair with the Bluetooth device.
            BluetoothDevice deviceToPair =
                    data.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE);

            // ... Continue interacting with the paired device.
            connectDeviceAfterCompanionScan(deviceToPair);
        }
    }

}

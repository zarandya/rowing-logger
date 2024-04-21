package com.github.zarandya.heartticks;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.Manifest;
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
import static com.github.zarandya.heartticks.BluetoothBikeService.ACTION_BIKE_SERVICE_STATE_CHANGED;
import static com.github.zarandya.heartticks.BluetoothHrmService.ACTION_HRM_SERVICE_STATE_CHANGED;
import static com.github.zarandya.heartticks.BluetoothHrmService.EXTRA_HR_VALUE;
import static com.github.zarandya.heartticks.BluetoothPM5Service.ACTION_CONNECT_BUTTON_PRESSED;
import static com.github.zarandya.heartticks.BluetoothPM5Service.ACTION_QUERY_STATE;
import static com.github.zarandya.heartticks.BluetoothPM5Service.ACTION_SERVICE_STATE_CHANGED;
import static com.github.zarandya.heartticks.BluetoothPM5Service.EXTRA_SERVICE_STATE;
import static com.github.zarandya.heartticks.BluetoothPM5Service.SERVICE_STATE_CONNECTED;
import static com.github.zarandya.heartticks.BluetoothPM5Service.SERVICE_STATE_IDLE;
import static com.github.zarandya.heartticks.BluetoothHrmService.ACTION_HR_VALUE_UPDATE;
import static com.github.zarandya.heartticks.db.BluetoothDeviceType.BIKE;
import static com.github.zarandya.heartticks.db.BluetoothDeviceType.HRM;
import static com.github.zarandya.heartticks.db.BluetoothDeviceType.PM5;
import static java.util.TimeZone.getTimeZone;

import com.github.zarandya.heartticks.db.SavedBluetoothDevice;

public class MainActivity extends AppCompatActivity {

    public static final String SCAN_NEW_DEVICE = "-1";
    private Button button;
    private Button accelLogButton;
    private Button buttonHrm;
    private Button buttonBike;
    private Button shareButton;
    private TextView rateTextView;

    private static final int REQUEST_ENABLE_BT = 201;
    private static final int REQUEST_STORAGE_PERMISSION = 201;
    private static final int REQUEST_BT_PERMISSION = 202;


    public static final String ACTION_SET_FILE_TO_SEND = "action_set_file_to_snd";
    public static final String EXTRA_FILE_TO_SEND = "extra_file_to_send";
    public static final String EXTRA_DEVICE = "extra_device_name";

    private String fileToSend = null;

    private BluetoothAdapter bluetoothAdapter;

    private boolean pm5BluetoothConnected = false;
    private boolean hrmBluetoothConnected = false;
    private boolean bikeBluetoothConnected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(ACTION_SERVICE_STATE_CHANGED);
        filter.addAction(ACTION_HRM_SERVICE_STATE_CHANGED);
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
            if (!ensureBluetoothPermission()) return;
            Intent intent = new Intent(this, BluetoothPM5Service.class);
            intent.setAction(ACTION_CONNECT_BUTTON_PRESSED);
            if (pm5BluetoothConnected) {
                startService(intent);
            } else {
                new Thread(() -> {
                    final List<SavedBluetoothDevice> savedDevices =
                            App.getDB().getDevicesDao().devicesOfKind(PM5);
                    runOnUiThread(() -> {
                        popupBluetoothDeviceSelector(button, intent, savedDevices, "");
                    });
                }).start();
            }
        });

        accelLogButton = findViewById(R.id.accel_log_btn);
        accelLogButton.setOnClickListener((v) -> {
            Intent intent = new Intent(this, AccelerometerService.class);
            intent.setAction(ACTION_ACCEL_LOG_BUTTON_PRESSED);
            startService(intent);
        });

        buttonHrm = findViewById(R.id.connect_hrm_button);
        buttonHrm.setOnClickListener((v) -> {
            if (!ensureBluetoothPermission()) return;
            Intent intent = new Intent(this, BluetoothHrmService.class);
            intent.setAction(ACTION_CONNECT_BUTTON_PRESSED);
            if (hrmBluetoothConnected) {
                startService(intent);
            } else {
                new Thread(() -> {
                    final List<SavedBluetoothDevice> savedDevices =
                            App.getDB().getDevicesDao().devicesOfKind(HRM);
                    runOnUiThread(() -> {
                        popupBluetoothDeviceSelector(buttonHrm, intent, savedDevices, "");
                    });
                }).start();
            }
        });

        buttonBike = findViewById(R.id.connect_bike_button);
        buttonBike.setOnClickListener((v) -> {
            if (!ensureBluetoothPermission()) return;
            Intent intent = new Intent(this, BluetoothBikeService.class);
            intent.setAction(ACTION_CONNECT_BUTTON_PRESSED);
            if (bikeBluetoothConnected) {
                startService(intent);
            } else {
                new Thread(() -> {
                    final List<SavedBluetoothDevice> savedDevices =
                            App.getDB().getDevicesDao().devicesOfKind(BIKE);
                    runOnUiThread(() -> {
                        popupBluetoothDeviceSelector(buttonBike, intent, savedDevices, "");
                    });
                }).start();
            }
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

        Intent queryState = new Intent(this, BluetoothPM5Service.class);
        queryState.setAction(ACTION_QUERY_STATE);
        startService(queryState);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        companionScanNextServiceIntent = null;

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

    private final void popupBluetoothDeviceSelector(View view, Intent intent, List<SavedBluetoothDevice> savedDevices, String prefix) {
        PopupMenu menu = new PopupMenu(this, view);
        Menu menuBuilder = menu.getMenu();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PERMISSION_GRANTED) {
            return;
        }

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

            if (selected < scanIID) {
                SavedBluetoothDevice savedDevice = savedDevices.get(selected);
                BluetoothDevice dev = null;
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
                if (!needsScan) {
                    intent.putExtra(EXTRA_DEVICE, dev);
                }
            } else if (selected >= bondedBaseIID) {
                intent.putExtra(EXTRA_DEVICE, bondedDevices.get(selected - bondedBaseIID));
            } else {
                needsScan = true;
            }
            if (!needsScan) {
                startService(intent);
            } else {
                if (SDK_INT >= Build.VERSION_CODES.O) {
                    scanDevice(scanAddress, intent, view);
                }
            }
            return true;
        });
        menu.show();
    }

    private Intent companionScanNextServiceIntent = null;
    private PopupMenu scanDevicePopupMenu = null;
    private int scanDevicePopupMenuIID = 0;
    private ArrayList<BluetoothDevice> scannedDevices = null;
    private String scanAddress = null;

    @RequiresApi(api = Build.VERSION_CODES.O)
    private final void scanDevice(String deviceAddress, Intent intent, View view) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, BLUETOOTH_CONNECT) != PERMISSION_GRANTED)
            throw new RuntimeException("The function should not have been called with missing permissions");

        synchronized (this) {
            if (companionScanNextServiceIntent != null) {
                Log.e("Companion", "A companion scan is already running");
                return;
            }
            companionScanNextServiceIntent = intent;
        }

        if (SDK_INT >= Build.VERSION_CODES.S) {
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
                    companionScanNextServiceIntent = null;
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
                    companionScanNextServiceIntent = null;
                }
            }
        };

        if (SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            deviceManager.associate(builder2.build(), (r) -> {r.run();}, callback);
        }
        else {
            deviceManager.associate(builder2.build(), callback, null);
        }


    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d("BLUETOOTH", action);
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                    return;
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
            } else if (action.equals(ACTION_SERVICE_STATE_CHANGED)) {
                int state = intent.getIntExtra(EXTRA_SERVICE_STATE, -1);
                if (state == SERVICE_STATE_IDLE) {
                    button.setText(R.string.connect);
                    pm5BluetoothConnected = false;
                } else if (state == SERVICE_STATE_CONNECTED) {
                    button.setText(R.string.disconnect);
                    pm5BluetoothConnected = true;
                } else {
                    button.setText(R.string.connecting);
                    pm5BluetoothConnected = false;
                }
            } else if (action.equals(ACTION_HRM_SERVICE_STATE_CHANGED)) {
                int state = intent.getIntExtra(EXTRA_SERVICE_STATE, -1);
                if (state == SERVICE_STATE_IDLE) {
                    buttonHrm.setText(R.string.connect_hrm_btn_text);
                    hrmBluetoothConnected = false;
                } else if (state == SERVICE_STATE_CONNECTED) {
                    buttonHrm.setText(R.string.disconnect);
                    hrmBluetoothConnected = true;
                } else {
                    buttonHrm.setText(R.string.connecting);
                    hrmBluetoothConnected = false;
                }
            } else if (action.equals(ACTION_BIKE_SERVICE_STATE_CHANGED)) {
                int state = intent.getIntExtra(EXTRA_SERVICE_STATE, -1);
                if (state == SERVICE_STATE_IDLE) {
                    buttonBike.setText(R.string.connect_bike_btn_text);
                    bikeBluetoothConnected = false;
                } else if (state == SERVICE_STATE_CONNECTED) {
                    buttonBike.setText(R.string.disconnect);
                    bikeBluetoothConnected = true;
                } else {
                    buttonBike.setText(R.string.connecting);
                    bikeBluetoothConnected = false;
                }
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
        Intent intent;
        synchronized (this) {
            intent = companionScanNextServiceIntent;
            companionScanNextServiceIntent = null;
        }


        if (intent != null) {
            intent.putExtra(EXTRA_DEVICE, device);
            startService(intent);
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
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            // ... Continue interacting with the paired device.
            connectDeviceAfterCompanionScan(deviceToPair);
        }
    }

}

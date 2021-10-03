package com.github.zarandya.heartticks;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.companion.AssociationRequest;
import android.companion.BluetoothDeviceFilter;
import android.companion.BluetoothLeDeviceFilter;
import android.companion.CompanionDeviceManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Parcel;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.bluetooth.BluetoothGatt.GATT_SUCCESS;
import static android.bluetooth.BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
import static android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.Build.VERSION.SDK_INT;
import static com.github.zarandya.heartticks.AccelerometerService.ACTION_ACCEL_LOG_BUTTON_PRESSED;
import static com.github.zarandya.heartticks.AccelerometerService.ACTION_RATE_UPDATE;
import static com.github.zarandya.heartticks.AccelerometerService.EXTRA_RATE;
import static com.github.zarandya.heartticks.BluetoothService.ACTION_CONNECT_BUTTON_PRESSED;
import static com.github.zarandya.heartticks.BluetoothService.ACTION_QUERY_STATE;
import static com.github.zarandya.heartticks.BluetoothService.ACTION_SERVICE_STATE_CHANGED;
import static com.github.zarandya.heartticks.BluetoothService.EXTRA_SERVICE_STATE;
import static com.github.zarandya.heartticks.BluetoothService.STATE_CONNECTED;
import static com.github.zarandya.heartticks.BluetoothService.STATE_IDLE;
import static java.util.TimeZone.getTimeZone;

public class MainActivity extends AppCompatActivity {

    private Button button;
    private Button accelLogButton;
    private Button shareButton;
    private TextView rateTextView;

    private static final int REQUEST_ENABLE_BT = 201;
    private static final int REQUEST_STORAGE_PERMISSION = 201;

    public static final String ACTION_SET_FILE_TO_SEND = "action_set_file_to_snd";
    public static final String EXTRA_FILE_TO_SEND = "extra_file_to_send";

    private String fileToSend = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /* // Initializes Bluetooth adapter.
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }*/
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(ACTION_SERVICE_STATE_CHANGED);
        filter.addAction(ACTION_RATE_UPDATE);
        filter.addAction(ACTION_SET_FILE_TO_SEND);
        registerReceiver(receiver, filter);
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter);

        if (ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) != PERMISSION_GRANTED) {
            requestPermissions(new String[]{READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
        }

        button = findViewById(R.id.button);
        button.setOnClickListener((v) -> {
            Intent intent = new Intent(this, BluetoothService.class);
            intent.setAction(ACTION_CONNECT_BUTTON_PRESSED);
            startService(intent);
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
                intent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(this, "io.github.zarandya.rowing.GattAclProvider", new File(fileToSend)));
                intent.setType(fileToSend.endsWith(".gatt") ? "application/gatt" : "application/accellog");
                Intent shareIntent = Intent.createChooser(intent, null);
                startActivity(shareIntent);
            }
        });

        rateTextView = findViewById(R.id.rate_text_view);

        Intent queryState = new Intent(this, BluetoothService.class);
        queryState.setAction(ACTION_QUERY_STATE);
        startService(queryState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Don't forget to unregister the ACTION_FOUND receiver.
        unregisterReceiver(receiver);
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d("BLUETOOTH", action);
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Discovery has found a device. Get the BluetoothDevice
                // object and its info from the Intent.
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address
                Log.d("BLUETOOTH", deviceName+" "+deviceHardwareAddress);
            }
            else if (action.equals(ACTION_SERVICE_STATE_CHANGED)) {
                int state = intent.getIntExtra(EXTRA_SERVICE_STATE, -1);
                if (state == STATE_IDLE) {
                    button.setText(R.string.connect);
                }
                else if (state == STATE_CONNECTED) {
                    button.setText(R.string.disconnect);
                }
                else {
                    button.setText(R.string.connecting);
                }
            }
            else if (action.equals(ACTION_RATE_UPDATE)) {
                rateTextView.setText(String.valueOf(intent.getIntExtra(EXTRA_RATE, 0)));
            }
            else if (action.equals(ACTION_SET_FILE_TO_SEND)) {
                fileToSend = intent.getStringExtra(EXTRA_FILE_TO_SEND);
                shareButton.setText("Share " + fileToSend);
            }
        }
    };

    private static final int SELECT_DEVICE_REQUEST_CODE = 42;
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SELECT_DEVICE_REQUEST_CODE &&
                resultCode == Activity.RESULT_OK) {
            // User has chosen to pair with the Bluetooth device.
            BluetoothDevice deviceToPair =
                    data.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE);
            deviceToPair.createBond();

            // ... Continue interacting with the paired device.
        }
    }

}
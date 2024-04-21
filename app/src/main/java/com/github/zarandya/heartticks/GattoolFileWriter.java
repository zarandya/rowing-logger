package com.github.zarandya.heartticks;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;

import static android.bluetooth.BluetoothGatt.GATT_CONNECTION_CONGESTED;
import static android.bluetooth.BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION;
import static android.bluetooth.BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION;
import static android.bluetooth.BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH;
import static android.bluetooth.BluetoothGatt.GATT_INVALID_OFFSET;
import static android.bluetooth.BluetoothGatt.GATT_READ_NOT_PERMITTED;
import static android.bluetooth.BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED;
import static android.bluetooth.BluetoothGatt.GATT_SUCCESS;
import static android.bluetooth.BluetoothGatt.GATT_WRITE_NOT_PERMITTED;
import static android.bluetooth.BluetoothProfile.STATE_CONNECTED;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTED;

public class GattoolFileWriter extends BluetoothGattCallback {

    private BufferedWriter out;
    private String prompt;

    public GattoolFileWriter(String filename, String prompt) throws IOException {
        out = new BufferedWriter(new FileWriter(new File(filename)));
        this.prompt = prompt;
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        super.onCharacteristicChanged(gatt, characteristic);
        byte[] value = characteristic.getValue();
        editCharacteristicValueBeforeLog(characteristic, value);
        synchronized (out) {
            try {
                out.write(String.format("Notification handle = 0x%04x value: ", characteristic.getInstanceId()));
                for (byte v : value) {
                    out.write(String.format("%02x ", v));
                }
                out.write("\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    protected void editCharacteristicValueBeforeLog(BluetoothGattCharacteristic characteristic, byte[] value) {}

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicRead(gatt, characteristic, status);
        synchronized (out) {
            try {
                out.write(prompt);
                out.write(String.format("char-read-hnd 0x%04x\n", characteristic.getInstanceId()));
                if (status == GATT_SUCCESS) {
                    out.write("Characteristic value/descriptor: ");
                    for (byte v : characteristic.getValue()) {
                        out.write(String.format("%02x ", v));
                    }
                    out.write("\n");
                } else {
                    out.write("Characteristic value/descriptor read failed: ");
                    out.write(attEcode2str(status));
                    out.write("\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicWrite(gatt, characteristic, status);
        synchronized (out) {
            try {
                out.write(prompt);
                out.write(String.format("char-write-req 0x%04x ", characteristic.getInstanceId()));
                for (byte v : characteristic.getValue()) {
                    out.write(String.format("%02x", v));
                }
                out.write("\n");
                if (status == GATT_SUCCESS) {
                    out.write("Characteristic value was written successfully\n");
                } else {
                    out.write("Characteristic Write Request failed: ");
                    out.write(attEcode2str(status));
                    out.write("\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        super.onConnectionStateChange(gatt, status, newState);
        if (newState == STATE_CONNECTED) {
            synchronized (out) {
                try {
                    out.write(prompt);
                    out.write("connect\nConnection Successful\n");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else if (newState == STATE_DISCONNECTED) {
            synchronized (out) {
                try {
                    out.write(prompt);
                    out.write("disconnect\n");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        super.onDescriptorRead(gatt, descriptor, status);
        synchronized (out) {
            try {
                out.write(prompt);
                out.write(String.format("char-read-hnd 0x%04x\n", getDescriptorInstanceId(descriptor)));
                if (status == GATT_SUCCESS) {
                    out.write("Characteristic value/descriptor: ");
                    for (byte v : descriptor.getValue()) {
                        out.write(String.format("%02x ", v));
                    }
                    out.write("\n");
                } else {
                    out.write("Characteristic value/descriptor read failed: ");
                    out.write(attEcode2str(status));
                    out.write("\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        super.onDescriptorWrite(gatt, descriptor, status);
        synchronized (out) {
            try {
                out.write(prompt);
                out.write(String.format("char-write-req 0x%04x ", getDescriptorInstanceId(descriptor)));
                for (byte v : descriptor.getValue()) {
                    out.write(String.format("%02x", v));
                }
                out.write("\n");
                if (status == GATT_SUCCESS) {
                    out.write("Characteristic value was written successfully\n");
                } else {
                    out.write("Characteristic Write Request failed: ");
                    out.write(attEcode2str(status));
                    out.write("\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
        super.onMtuChanged(gatt, mtu, status);
    }

    @Override
    public void onPhyRead(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
        super.onPhyRead(gatt, txPhy, rxPhy, status);
    }

    @Override
    public void onPhyUpdate(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
        super.onPhyUpdate(gatt, txPhy, rxPhy, status);
    }

    @Override
    public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
        super.onReadRemoteRssi(gatt, rssi, status);
    }

    @Override
    public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
        super.onReliableWriteCompleted(gatt, status);
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        super.onServicesDiscovered(gatt, status);
        synchronized (out) {
            try {
                out.write(prompt);
                out.write("char-desc\n");
                for (BluetoothGattService service : gatt.getServices()) {
                    out.write("#service");
                    out.write(String.format("handle: 0x%04x, uuid: ", service.getInstanceId()));
                    out.write(service.getUuid().toString());
                    out.write("\n");
                    for (BluetoothGattCharacteristic c : service.getCharacteristics()) {
                        out.write(String.format("handle: 0x%04x, uuid: ", c.getInstanceId()));
                        out.write(c.getUuid().toString());
                        out.write("\n");
                        for (BluetoothGattDescriptor d : c.getDescriptors()) {
                            out.write(String.format("handle: 0x%04x, uuid: ", getDescriptorInstanceId(d)));
                            out.write(d.getUuid().toString());
                            out.write("\n");
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private int getDescriptorInstanceId(BluetoothGattDescriptor descriptor) {
        Class cls = BluetoothGattDescriptor.class;
        try {
            Method meth = cls.getMethod("getInstanceId");
            meth.setAccessible(true);
            return (int) meth.invoke(descriptor);
        } catch (Exception e) {
            // make up data
            return descriptor.getCharacteristic().getInstanceId() + 1 + descriptor.getCharacteristic().getDescriptors().indexOf(descriptor);
        }
    }

    private static String attEcode2str(int status) {
        switch (status) {
            case GATT_READ_NOT_PERMITTED:
                return "Attribute can't be read";
            case GATT_WRITE_NOT_PERMITTED:
                return "Attribute can't be written";
            case GATT_INSUFFICIENT_AUTHENTICATION:
                return "Attribute requires authentication before read/write";
            case GATT_REQUEST_NOT_SUPPORTED:
                return "Server doesn't support the request received";
            case GATT_INVALID_OFFSET:
                return "Offset past the end of the attribute";
            case GATT_INVALID_ATTRIBUTE_LENGTH:
                return "Attribute value length is invalid";
            case GATT_INSUFFICIENT_ENCRYPTION:
                return "Encryption required before read/write";
            case GATT_CONNECTION_CONGESTED:
                return "A timeout occured";
            default:
                return "Unexpected error code";
        }
    }
    
    public void writeTime(String timeStr) {
        synchronized (out) {
            try {
                out.write(prompt);
                out.write("#");
                out.write(timeStr);
                out.write("\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void close() {
        synchronized (out) {
            try {
                out.write(prompt);
                out.write("exit\n");
                out.flush();
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

}

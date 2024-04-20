package com.github.zarandya.heartticks;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static android.app.NotificationManager.IMPORTANCE_LOW;
import static android.os.Build.VERSION.SDK_INT;
import static androidx.core.app.NotificationCompat.PRIORITY_LOW;
import static com.github.zarandya.heartticks.MainActivity.ACTION_SET_FILE_TO_SEND;
import static com.github.zarandya.heartticks.MainActivity.EXTRA_FILE_TO_SEND;
import static com.github.zarandya.heartticks.R.string.accel_log_service_name;
import static java.util.TimeZone.getTimeZone;

public class AccelerometerService extends Service implements SensorEventListener {

    public static final String ACTION_ACCEL_LOG_BUTTON_PRESSED = "action_acceleration_log_button_pressed";
    public static final String ACTION_QUERY_ACCEL_LOG_STATE = "action_acceleration_log_query_state";
    public static final String ACTION_RATE_UPDATE = "action_rate_update";
    public static final String EXTRA_RATE = "extra_rate";

    private SensorManager sensorManager;
    private Sensor sensor;
    
    private int state;
    
    public static final int STATE_IDLE = BluetoothPM5Service.SERVICE_STATE_IDLE; // same as STATE_IDLE in BluetoothService
    public static final int STATE_RECORDING_ACCEL_DATA = 1;
    public static final int STATE_SENSOR_FAILED = 2;
    
    public static final int ROWING_STATE_INIT = 0;
    public static final int ROWING_STATE_RECOVERY = 1;
    public static final int ROWING_STATE_DRIVE = 2;
    
    public static final long SHORT_TIME_LEN_NANOS = 200000000L;
    public static final long LONG_TIME_LEN_NANOS = 10000000000L;
    public static final long MIN_TIME_OFFSET = 40L; // MILLISECONDS

    private static final int BUFFER_LEN = 8192;
    private static final int BUF_LEN_MASK = BUFFER_LEN - 1;

    private int rowingState = ROWING_STATE_INIT;
    private long prevPeak = 0;
    private long currentPeak = 0;
    private float currentPeakAmplitude = 0;
    private long[] yAccel = new long[BUFFER_LEN];
    private long[] time = new long[BUFFER_LEN];
    private int head;
    private int longTail;
    private int shortTail;
    private long positiveSum;
    private long negativeSum;
    private long shortSum;
    private int shortLen;
    private int longLen;
    private long logTimeEllapsedBaseNanos;
    private long logTimeBaseMillis;
    private long logNextTimeMillis;

    private String filename = null;
    
    private DataOutputStream out;

    public static final String CHANNEL_ID = "io.github.zarandya.beatrate.ACCEL_LOG_SERVICE_NOTIFICATION";

    private boolean firstEvent = true;

    @Override
    public void onCreate() {
        super.onCreate();
        state = STATE_IDLE;
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        if (sensor == null) {
            Log.d("ACCEL", "Sensor absent");
            state = STATE_SENSOR_FAILED;
        }
        if (SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, getString(accel_log_service_name), IMPORTANCE_LOW);
            channel.setDescription(getString(accel_log_service_name));
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getAction().equals(ACTION_ACCEL_LOG_BUTTON_PRESSED)) {
            switch (state) {
                case STATE_IDLE:
                    startDumping();
                    break;
                case STATE_RECORDING_ACCEL_DATA:
                    stopDumping();
                    break;
                case STATE_SENSOR_FAILED:
                    break;
            }
        }
        return START_NOT_STICKY;
    }

    private void startDumping() {
        try {
            @SuppressLint("SimpleDateFormat") SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss'Z'");
            df.setTimeZone(getTimeZone("UTC"));

            File externalAcl = new File(ContextCompat.getExternalFilesDirs(this, null)[0].getPath() + "/acl");
            if (!externalAcl.isDirectory()) {
                externalAcl.mkdir();
            }
            Date date = new Date();
            filename = externalAcl.getPath() + "/RateMonitor" + df.format(date) + ".acc";
            final long time_unix = date.getTime();
            Log.d("ACL_TIME", "First event unix time: " + time_unix);
            Log.d("ACL_TIME", "First event formatted time: " + df.format(date));

            out = new DataOutputStream(new FileOutputStream(new File(filename)));

            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST);

            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                    .setContentText("Logging acceleration")
                    .setContentText("")
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(""))
                    .setPriority(PRIORITY_LOW)
                    .build();
            startForeground(84, notification);
            
            state = STATE_RECORDING_ACCEL_DATA;
            rowingState = ROWING_STATE_INIT;
            head = 0;
            longTail = 0;
            shortTail = 0;
            positiveSum = 0;
            negativeSum = 0;
            time[BUF_LEN_MASK] = SystemClock.elapsedRealtimeNanos();
            firstEvent = true;
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void stopDumping() {
        try {
            stopForeground(true);
            sensorManager.unregisterListener(this, sensor);
            synchronized (out) {
                out.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        Intent broadcast = new Intent(ACTION_SET_FILE_TO_SEND);
        broadcast.putExtra(EXTRA_FILE_TO_SEND, filename);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);

        state = STATE_IDLE;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if ((event.timestamp - time[(head-1) & BUF_LEN_MASK]) * BUFFER_LEN * 2 > LONG_TIME_LEN_NANOS) {
            time[head] = event.timestamp;
            final long y = (int) (event.values[1] * 1000000);
            yAccel[head] = y;
            shortSum += y;
            if (y > 0)
                positiveSum += y;
            else
                negativeSum += y;
            ++shortLen;
            ++longLen;
            while (event.timestamp > time[shortTail] + SHORT_TIME_LEN_NANOS) {
                shortSum -= yAccel[shortTail];
                ++shortTail;
                shortTail &= BUF_LEN_MASK;
                --shortLen;
            }
            while (event.timestamp > time[longTail] + LONG_TIME_LEN_NANOS) {
                if (yAccel[longTail] > 0)
                    positiveSum -= yAccel[longTail];
                else
                    negativeSum -= yAccel[longTail];
                ++longTail;
                longTail &= BUF_LEN_MASK;
                --longLen;
            }
            if (rowingState == ROWING_STATE_DRIVE) {
                if (shortSum * longLen < 2.0 * negativeSum * shortLen) {
                    rowingState = ROWING_STATE_RECOVERY;
                    updateRate(60000000000. / (currentPeak - prevPeak));
                    prevPeak = currentPeak;
                    currentPeakAmplitude = 0;
                }
                if (y > currentPeakAmplitude) {
                    currentPeakAmplitude = y;
                    currentPeak = event.timestamp;
                }
            } else {
                if (shortSum * longLen > 2.0 * positiveSum * shortLen) {
                    rowingState = ROWING_STATE_DRIVE;
                }
            }
            ++head;
            head &= BUF_LEN_MASK;
            Log.d("ACCEL", "y=" + y + " shortAvg=" + (shortSum / shortLen) +
                    " avg=" + (positiveSum / longLen) + " avgn=" + (negativeSum / longLen) +
                    " rowingState=" + rowingState + " head=" + head + " time=" + event.timestamp +
                    " currentPeak=" + currentPeak + " prevPeak=" + prevPeak +
                    " shortTail=" + shortTail + " longTail=" + longTail);
        }
        try {
            synchronized (out) {
                if (firstEvent) {
                    firstEvent = false;
                    out.writeChar(60001);
                    out.writeChar(2);
                    final long elapsedTimeNanos = SystemClock.elapsedRealtimeNanos();
                    final long currentTimeMillis = System.currentTimeMillis();
                    final long time = ((currentTimeMillis * 1000000) + event.timestamp - elapsedTimeNanos) / 1000000;
                    out.writeChar(60002);
                    out.writeChar((int) (time >> 48) & 0xffff);
                    out.writeChar(60002);
                    out.writeChar((int) (time >> 32) & 0xffff);
                    out.writeChar(60002);
                    out.writeChar((int) (time >> 16) & 0xffff);
                    out.writeChar(60002);
                    out.writeChar((int) time & 0xffff);
                    logTimeBaseMillis = time;
                    logTimeEllapsedBaseNanos = event.timestamp;
                    logNextTimeMillis = 0;
                }
                long timeOffset = (event.timestamp - logTimeEllapsedBaseNanos) / 1000000;
                if (timeOffset >= logNextTimeMillis) {
                    logNextTimeMillis += MIN_TIME_OFFSET;
                    if (timeOffset >= 60000) {
                        timeOffset -= 60000;
                        logTimeEllapsedBaseNanos += 60000000000L;
                        logNextTimeMillis -= 60000;
                    }
                    out.writeChar((int) timeOffset);
                    long amplitude = shortSum / shortLen / 1000;
                    if (amplitude > Short.MAX_VALUE) {
                        amplitude = Short.MAX_VALUE;
                    }
                    if (amplitude < Short.MIN_VALUE) {
                        amplitude = Short.MIN_VALUE;
                    }
                    out.writeShort((int) amplitude);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateRate(double v) {
        Intent broadcast = new Intent(ACTION_RATE_UPDATE);
        broadcast.putExtra(EXTRA_RATE, (int) v);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        try {
            synchronized (out) {
                out.writeChar(60003);
                out.writeShort(accuracy);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

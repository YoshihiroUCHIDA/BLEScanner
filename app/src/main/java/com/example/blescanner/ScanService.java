package com.example.blescanner;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class ScanService extends Service {
    private Timer timer;
    private TimerTask timerTask;
    private BluetoothLeScanner bluetoothLeScanner;
    private LocalDate currentDate;
    private File file;
    private FileOutputStream fileOutputStream;
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            // スキャン中の処理
            ScanRecord scanRecord = result.getScanRecord(); // スキャンデータ
            if (scanRecord != null) {
                int rssi = result.getRssi(); // RSSI
                // RSSI が -100 以上の場合
                if (-100 <= rssi) {
                    long timestamp = System.currentTimeMillis(); // 時刻
                    String address = result.getDevice().getAddress(); // MAC アドレス
                    String record = bytesToHexString(scanRecord.getBytes()); // スキャンレコードの生バイト
                    String scanLog = timestamp + "," + address + "," + rssi + "," + record; // ログ
                    saveFile(scanLog);  // ファイルへ保存
                }
            }
        }
    };

    /* -------------------------------------------------- */
    @Override
    public void onCreate() {
        super.onCreate();

        // 通知チャネルの作成と管理
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String name = "BLE Scanner";
        String channelId = "ble_scanner_channel";
        String description = "Notifications for BLE device scanning";

        if (notificationManager.getNotificationChannel(channelId) == null) {
            NotificationChannel notificationChannel = new NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_HIGH);
            notificationChannel.setDescription(description);
            notificationManager.createNotificationChannel(notificationChannel);
        }

        // 通知の作成
        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("BLE Scan Service")
                .setContentText("Scanning for BLE devices...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build();

        startForeground(1, notification); // フォアグラウンド処理の開始

        timer = new Timer();
        timerTask = new TimerTask() {
            @Override
            public void run() {
                stopScan();
                startScan();
            }
        };
        timer.scheduleAtFixedRate(timerTask, 0, 30 * 1000); // 30秒毎に実行
    }

    /* -------------------------------------------------- */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    /* -------------------------------------------------- */
    @Override
    public void onDestroy() {
        super.onDestroy();
        stopScan();
        timer.cancel();
        timerTask.cancel();
    }

    /* -------------------------------------------------- */
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /* -------------------------------------------------- */
    private String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", (b & 0xff))); // バイト列 → 16進数の文字列型変換
        }
        return sb.toString();
    }

    /* -------------------------------------------------- */
    // スキャンデータの保存
    private void saveFile(String scanLog) {
        LocalDate date = LocalDate.now(); // 現在の日付を取得

        // 日付が変わった場合、新しいファイルを作成
        if (currentDate == null || !currentDate.equals(date)) {
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close(); // ファイルを閉じる
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            currentDate = date;
            String fileName = "BLE_Log_" + date.getYear() + "_" + date.getMonthValue() + "_" + date.getDayOfMonth() + ".csv";

            // 新しいファイルを作成
            File path = getApplicationContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            file = new File(path, fileName);
        }
        // ファイルへの書き込み
        try {
            fileOutputStream = new FileOutputStream(file, true);
            fileOutputStream.write((scanLog + "\n").getBytes()); // 引数の文字列を、改行コードと共に書き込み
            fileOutputStream.close(); // ファイルを閉じる
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /* -------------------------------------------------- */
    private void startScan() {
        BluetoothManager bluetoothManager = (BluetoothManager) getApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        List<ScanFilter> filters = new ArrayList<>();

        try {
            bluetoothLeScanner.startScan(filters, settings, scanCallback); // BLE スキャンを開始
            Log.d("ScanService", "Start Scan OK");
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    /* -------------------------------------------------- */
    private void stopScan() {
        if (bluetoothLeScanner != null) {
            try {
                bluetoothLeScanner.stopScan(scanCallback);
                Log.d("ScanService", "Stop Scan OK");
            } catch (SecurityException e) {
                e.printStackTrace();
            }
        }
    }
}

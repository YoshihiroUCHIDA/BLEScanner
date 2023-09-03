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

import org.apache.commons.codec.digest.DigestUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class ScanService extends Service {
    private int timeCounter = 0;
    private int fileNumber = 1;
    private static final String randomUUID = UUID.randomUUID().toString(); // ユニークな識別番号
    private Timer timer;
    private TimerTask timerTask;
    private BluetoothLeScanner bluetoothLeScanner;
    private LocalDate date;
    private File file;
    private File previousFile;
    private FileOutputStream fileOutputStream;
    private final FileUploader fileUploader = new FileUploader();

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);

            // スキャン中の処理
            ScanRecord scanRecord = result.getScanRecord(); // スキャンデータ
            if (scanRecord != null) {
                long timestamp = System.currentTimeMillis(); // 時刻
                String address = result.getDevice().getAddress(); // MAC アドレス
                address = hashAddress(address); // ハッシュ値を求める
                int rssi = result.getRssi(); // RSSI
                String record = bytesToHexString(scanRecord.getBytes()); // スキャンレコードの生バイト
                String scanLog = timestamp + "," + address + "," + rssi + "," + record; // ログ
                saveFile(scanLog);  // ファイルへ保存
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
                .setSmallIcon(R.drawable.ic_launcher_background)
                .build();

        startForeground(1, notification); // フォアグラウンド処理を開始

        timer = new Timer();
        timerTask = new TimerTask() {
            @Override
            public void run() {
                stopScan();
                startScan();
            }
        };
        timer.scheduleAtFixedRate(timerTask, 0, 30 * 1000); // 30 秒毎に実行
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
        new Thread(() -> {
            fileUploader.uploadFile(getApplicationContext(), file); // Google Cloud Storage へアップロード
        }).start();
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
            sb.append(String.format("%02x", (b & 0xff))); // 型変換（バイト列 → 16進数の文字列型）
        }
        return sb.toString();
    }

    /* -------------------------------------------------- */
    private String hashAddress(String address) {
        return DigestUtils.sha256Hex(address); // ハッシュ値へ変換
    }

    /* -------------------------------------------------- */
    private File createCsvFile() {
        String fileName = String.format(Locale.US, "BLE_Log_%s_%d_%d_%d_%d.csv", randomUUID, date.getYear(), date.getMonthValue(), date.getDayOfMonth(), fileNumber);
        File path = getApplicationContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        return new File(path, fileName);
    }

    /* -------------------------------------------------- */
    // スキャンデータの保存
    private void saveFile(String scanLog) {
        LocalDate currentDate = LocalDate.now(); // 現在の日付を取得

        // 初回スキャン時 or 日付が変わった場合
        if (date == null || !date.equals(currentDate)) {
            // 初回スキャン時以降の場合
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                previousFile = file;
                new Thread(() -> {
                    fileUploader.uploadFile(getApplicationContext(), previousFile); // Google Cloud Storage へアップロード
                }).start();

                fileNumber = 1; // ファイル番号のリセット
            }
            date = currentDate; // 現在の日付を保存
            file = createCsvFile(); // 新しいファイルを作成
        }
        // ファイルへ書き込む
        try {
            fileOutputStream = new FileOutputStream(file, true);
            fileOutputStream.write((scanLog + "\n").getBytes()); // 引数の文字列を、改行コードと共に書き込む
            fileOutputStream.close();
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
            bluetoothLeScanner.startScan(filters, settings, scanCallback);
            Log.d("ScanService", "Start Scan OK");
            timeCounter++;
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

                // 30 分以上が経過した場合
                if (60 <= timeCounter) {
                    previousFile = file;
                    new Thread(() -> {
                        fileUploader.uploadFile(getApplicationContext(), previousFile); // Google Cloud Storage へアップロード
                    }).start();

                    fileNumber++;
                    file = createCsvFile(); // 新しいファイルを作成
                    timeCounter = 0; // カウントをリセット
                }
            } catch (SecurityException e) {
                e.printStackTrace();
            }
        }
    }
}

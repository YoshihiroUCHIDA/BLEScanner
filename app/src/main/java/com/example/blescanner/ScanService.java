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
import java.util.UUID;
import java.security.MessageDigest;

public class ScanService extends Service {
    private int timeCounter = 0;
    private int fileNum = 0;
    private static final String randomUUID = UUID.randomUUID().toString(); // ユニーク識別番号
    private Timer timer;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private File file;
    private File prevFile;
    private final FileUploader fileUploader = new FileUploader();
    private LocalDate currentDate = null;
    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();
    private File filesDir;
    private MessageDigest messageDigest;
    private final StringBuilder buffer = new StringBuilder(); // ログデータを保持するためのバッファ
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            // スキャンレコードの取得
            ScanRecord scanRecord = result.getScanRecord();
            if (scanRecord != null) {
                // RSSI値
                int rssi = result.getRssi();
                if (-98 <= rssi) {
                    // 現在の日付
                    LocalDate date = LocalDate.now();
                    // 初回の場合 or 日付が変わった場合
                    if (currentDate == null || !currentDate.isEqual(date)) {
                        // バッファにデータがある場合
                        if (0 < buffer.length()) {
                            // バッファのデータをファイルに保存
                            saveFile(buffer.toString());
                            buffer.setLength(0);
                        }
                        // 日付が変わった場合
                        if (currentDate != null && !currentDate.isEqual(date)) {
                            // ファイルをアップロード
                            prevFile = file;
                            new Thread(() -> fileUploader.uploadFile(getApplicationContext(), prevFile)).start();
                            // ファイル番号をリセット
                            fileNum = 0;
                        }
                        // 現在の日付を更新
                        currentDate = date;
                        // 新しいファイルを作成
                        file = createCsvFile();
                    }
                    // バッファが一定の文字数を超えた場合
                    if (2048 <= buffer.length()) {
                        // バッファのデータをファイルに保存
                        saveFile(buffer.toString());
                        buffer.setLength(0);
                    }
                    // 現在の時刻（ミリ秒）
                    long timestamp = System.currentTimeMillis();
                    // アドレスのハッシュ値
                    String address = hashAddress(result.getDevice().getAddress());
                    // スキャンレコードの生バイト（16進数文字列）
                    String record = bytesToHexString(scanRecord.getBytes());
                    // ログデータをバッファに追加
                    buffer.append(timestamp).append(",").append(address).append(",").append(rssi).append(",").append(record).append("\n");
                }
            }
        }
    };

    /* ------------------------------------------------------------ */
    @Override
    public void onCreate() {
        super.onCreate();
        // ログファイルのフォルダ
        filesDir = getApplicationContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);

        // Bluetooth 関連のインスタンス
        BluetoothManager bluetoothManager = (BluetoothManager) getApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        // ハッシュ処理（SHA-256）関連のインスタンス
        try {
            messageDigest = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            e.printStackTrace();
        }

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
        // フォアグラウンド処理の開始
        startForeground(1, notification);

        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                stopScan();
                startScan();
            }
        }, 0, 30 * 1000); // 30秒間ループ実行
    }

    /* ------------------------------------------------------------ */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    /* ------------------------------------------------------------ */
    @Override
    public void onDestroy() {
        super.onDestroy();

        stopScan();
        // ファイルをアップロード
        new Thread(() -> fileUploader.uploadFile(getApplicationContext(), file)).start();
        timer.cancel();
        Log.d("ScanService", "Scan Finish");
    }

    /* ------------------------------------------------------------ */
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /* ------------------------------------------------------------ */
    private String bytesToHexString(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xff;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0f];
        }
        return new String(hexChars);
    }

    /* ------------------------------------------------------------ */
    private String hashAddress(String address) {
        String output = "";
        try {
            messageDigest.update(address.getBytes());
            byte[] digest = messageDigest.digest();
            output = bytesToHexString(digest);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return output;
    }

    /* ------------------------------------------------------------ */
    private File createCsvFile() {
        String fileName = "BLE_Log_" +
                randomUUID +
                "_" +
                currentDate.getYear() +
                "_" +
                currentDate.getMonthValue() +
                "_" +
                currentDate.getDayOfMonth() +
                "_" +
                fileNum +
                ".csv";
        return new File(filesDir, fileName);
    }

    /* ------------------------------------------------------------ */
    private void saveFile(String scanLog) {
        try (FileOutputStream fileOutputStream = new FileOutputStream(file, true)) {
            fileOutputStream.write(scanLog.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /* ------------------------------------------------------------ */
    private void startScan() {
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

    /* ------------------------------------------------------------ */
    private void stopScan() {
        if (bluetoothLeScanner == null) {
            return;
        }
        try {
            bluetoothLeScanner.stopScan(scanCallback);
            Log.d("ScanService", "Stop Scan OK");
            // バッファにデータがある場合
            if (0 < buffer.length()) {
                saveFile(buffer.toString());
                buffer.setLength(0);
            }
            // 一定時間が経過した場合
            if (30 <= timeCounter) {
                prevFile = file;
                // 前のファイルをアップロード
                new Thread(() -> fileUploader.uploadFile(getApplicationContext(), prevFile)).start();
                fileNum++;
                file = createCsvFile();
                timeCounter = 0;
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }
}

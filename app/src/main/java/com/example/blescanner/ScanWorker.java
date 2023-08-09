package com.example.blescanner;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class ScanWorker extends Worker {
    // 変数の定義
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
    public ScanWorker(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
        super(context, workerParameters);
    }

    /* -------------------------------------------------- */
    @NonNull
    @Override
    public Result doWork() {
        startScan();
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                stopScan();
                timer.cancel();
            }
        }, 29990);

        // 30秒毎の定期実行
        OneTimeWorkRequest scanRequest = new OneTimeWorkRequest.Builder(ScanWorker.class)
                .setInitialDelay(30, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(getApplicationContext()).enqueue(scanRequest);

        return Result.success();
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
            try {
                fileOutputStream = new FileOutputStream(file, true);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        // ファイルへの書き込み
        try {
            fileOutputStream.write((scanLog + "\n").getBytes()); // 引数の文字列を、改行コードと共に書き込み
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /* -------------------------------------------------- */
    private void startScan() {
        if (file != null) {
            try {
                fileOutputStream = new FileOutputStream(file, true);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        BluetoothManager bluetoothManager = (BluetoothManager) getApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        List<ScanFilter> filters = new ArrayList<>();

        try {
            bluetoothLeScanner.startScan(filters, settings, scanCallback); // BLE スキャンを開始
            Log.d("ScanWorker", "Start Scan OK");
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    /* -------------------------------------------------- */
    private void stopScan() {
        try {
            bluetoothLeScanner.stopScan(scanCallback);
            Log.d("ScanWorker", "Stop Scan OK");
        } catch (SecurityException e) {
            e.printStackTrace();
        }

        if (fileOutputStream != null) {
            try {
                fileOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

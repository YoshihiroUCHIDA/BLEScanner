package com.example.blescanner;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
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
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.WindowManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity {
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private final ScanCallback scanCallback = new ScanCallback() {
        /* スキャン中の処理 */
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            /* スキャンデータの処理 */
            ScanRecord scanRecord = result.getScanRecord();
            if (scanRecord != null) {
                int rssi = result.getRssi(); /* RSSI */
                /* RSSI が -100 以上の場合 */
                if (-100 <= rssi) {
                    long timestamp = System.currentTimeMillis(); /* 時刻 */
                    String address = result.getDevice().getAddress(); /* MAC アドレス */
                    String record = bytesToHexString(scanRecord.getBytes()); /* スキャンレコードの生バイト */
                    String scanLog = timestamp + "," + address + "," + rssi + "," + record; /* ログ出力 */
                    appendToBleLog(scanLog);  /* ファイルへ保存 */
                }
            }
        }
    };
    private final Timer timer = new Timer();
    private TimerTask scanTask;
    private LocalDate currentDate = null;
    private File file = null;
    private FileOutputStream fos = null;

    /* -------------------------------------------------- */
    private String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        /* バイト列 → 16進数の文字列型変換 */
        for (byte b : bytes) {
            sb.append(String.format("%02x", (b & 0xff)));
        }
        return sb.toString();
    }

    /* -------------------------------------------------- */
    /* スキャンデータの保存 */
    private void appendToBleLog(String scanLog) {
        /* 現在の日付を取得 */
        LocalDate date = LocalDate.now();

        /* 日付が変わった場合、新しいファイルを作成 */
        if (currentDate == null || !currentDate.equals(date)) {
            if (fos != null) {
                try {
                    fos.close(); /* ファイルを閉じる */
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            currentDate = date;
            String fileName = "BLE_Log_" + date.getYear() + "_" + date.getMonthValue() + "_" + date.getDayOfMonth() + ".csv";

            /* 新しいファイルを作成 */
            File path = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            file = new File(path, fileName);
            try {
                fos = new FileOutputStream(file, true);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        /* ファイルへの書き込み */
        try {
            fos.write((scanLog + "\n").getBytes()); /* 引数の文字列を、改行コードと共に書き込み */
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /* -------------------------------------------------- */
    /* パーミッションの確認 */
    private void checkAndRequestPermissions() {
        List<String> permissions = new ArrayList<>(); /* パーミッションのリストを作成 */
        /* API レベルが 31 以上の場合 */
        if (Build.VERSION_CODES.S <= Build.VERSION.SDK_INT) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
        }
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);

        /* パーミッションのリストから、許可されていないものを抽出 */
        List<String> permissionsToRequest = permissions.stream()
                .filter(permission -> ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED)
                .collect(Collectors.toList());

        /* 許可されていないパーミッションがある場合、リクエストを送信 */
        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new String[0]), 1);
        }
    }

    /* -------------------------------------------------- */
    /* Bluetooth の有効化 */
    private void enableBluetooth() {
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        /* Bluetooth が無効の場合 */
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            /* Activity Result API を使用し、Bluetooth の有効化を要求 */
            ActivityResultLauncher<Intent> startForResult = registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                    }
            );
            startForResult.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
        }
    }

    /* -------------------------------------------------- */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); /* 画面をオンのままにする */

        /* スイッチの処理 */
        SwitchCompat switchCompat = findViewById(R.id.switchCompat);
        switchCompat.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                startScan();
            } else {
                stopScan();
            }
        });

        checkAndRequestPermissions(); /* パーミッションの確認 */
        enableBluetooth(); /* Bluetooth の有効化 */
    }

    /* -------------------------------------------------- */
    /* スキャン開始 */
    private void startScan() {
        if (file != null) {
            try {
                fos = new FileOutputStream(file, true);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        List<ScanFilter> filters = new ArrayList<>();

        try {
            bluetoothLeScanner.startScan(filters, settings, scanCallback);
            Log.d("startScan", "OK");
        } catch (SecurityException e) {
            checkAndRequestPermissions();
            enableBluetooth();
        }

        if (scanTask != null) {
            scanTask.cancel();
            timer.purge();
        }
        scheduleScanTask();
    }

    /* -------------------------------------------------- */
    private void scheduleScanTask() {
        scanTask = new TimerTask() {
            @Override
            public void run() {
                stopScan();
                startScan();
            }
        };
        timer.schedule(scanTask, 1000 * 30, 1000 * 30); /* 30秒毎に実行 */
    }

    /* -------------------------------------------------- */
    /* スキャン停止 */
    private void stopScan() {
        if (bluetoothLeScanner != null) {
            try {
                bluetoothLeScanner.stopScan(scanCallback);
                Log.d("stopScan", "OK");
            } catch (SecurityException e) {
                checkAndRequestPermissions();
                enableBluetooth();
            }
        }

        if (fos != null) {
            try {
                fos.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        if (scanTask != null) {
            scanTask.cancel();
            timer.purge();
        }
    }

    /* -------------------------------------------------- */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            fos.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
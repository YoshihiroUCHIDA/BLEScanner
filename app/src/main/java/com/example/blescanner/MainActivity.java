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
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.WindowManager;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private boolean isSwitchOn = false;
    private Intent bleScanServiceIntent;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // スリープ状態に移行しない
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        checkPermissions();
        enableWifi();
        enableBluetooth();

        bleScanServiceIntent = new Intent(this, ScanService.class);

        // スイッチの処理
        SwitchCompat switchCompat = findViewById(R.id.switchCompat);
        switchCompat.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isSwitchOn = isChecked;
            if (isChecked) { // ON
                startForegroundService(bleScanServiceIntent);
            } else { // OFF
                stopService(bleScanServiceIntent);
            }
        });
    }

    /* ------------------------------------------------------------ */
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (isSwitchOn) {
            stopService(bleScanServiceIntent);
        }
    }

    /* ------------------------------------------------------------ */
    private void checkPermissions() {
        // 権限のリストを作成
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION_CODES.S <= Build.VERSION.SDK_INT) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
        }
        // リクエストが必要な権限のリストを作成
        List<String> permissionsToRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }
        // リクエストが必要な権限がある場合
        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new String[0]), 1);
        }
    }

    /* ------------------------------------------------------------ */
    private void enableBluetooth() {
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        // Bluetooth が無効の場合
        if (!bluetoothAdapter.isEnabled()) {
            // Activity Result API を使用し、Bluetooth の有効化を要求
            ActivityResultLauncher<Intent> activityResultLauncher = registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK) {
                            Toast.makeText(this, "Bluetooth ON", Toast.LENGTH_SHORT).show();
                        }
                    }
            );
            activityResultLauncher.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
        }
    }

    /* ------------------------------------------------------------ */
    private void enableWifi() {
        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        // Wi-Fi が無効の場合
        if (!wifiManager.isWifiEnabled()) {
            // Wi-Fi の有効化を要求
            ActivityResultLauncher<Intent> activityResultLauncher = registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK) {
                            Toast.makeText(this, "Wi-Fi ON", Toast.LENGTH_SHORT).show();
                        }
                    }
            );
            activityResultLauncher.launch(new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY));
        }
    }
}

package com.example.blescanner;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity {
    // パーミッションの確認
    private void checkAndRequestPermissions() {
        List<String> permissions = new ArrayList<>(); // パーミッションのリストを作成
        // API レベルが 31 以上の場合
        if (Build.VERSION_CODES.S <= Build.VERSION.SDK_INT) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
        }
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);

        // パーミッションのリストから、許可されていないものを抽出
        List<String> permissionsToRequest = permissions.stream()
                .filter(permission -> ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED)
                .collect(Collectors.toList());

        // 許可されていないパーミッションがある場合、リクエストを送信
        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new String[0]), 1);
        }
    }

    /* -------------------------------------------------- */
    // Bluetooth の有効化
    private void enableBluetooth() {
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

        // Bluetooth が無効の場合
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            // Activity Result API を使用し、Bluetooth の有効化を要求
            ActivityResultLauncher<Intent> startForResult = registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK) {
                            Toast.makeText(this, "Bluetooth ON", Toast.LENGTH_SHORT).show();
                        }
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
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); // 画面はオンのまま

        // スイッチの処理
        SwitchCompat switchCompat = findViewById(R.id.switchCompat);
        switchCompat.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) { // ON
                OneTimeWorkRequest scanRequest = new OneTimeWorkRequest.Builder(ScanWorker.class).build();
                WorkManager.getInstance(this).enqueue(scanRequest);
            } else { // OFF
                WorkManager.getInstance(this).cancelAllWork();
            }
        });

        enableBluetooth(); // Bluetooth の有効化
        checkAndRequestPermissions(); // パーミッションの確認
    }
}
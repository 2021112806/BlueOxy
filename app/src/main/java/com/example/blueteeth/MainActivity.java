package com.example.blueteeth;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_LOCATION_PERMISSION = 2;
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 3;

    private BluetoothAdapter bluetoothAdapter;
    private ArrayList<BluetoothDevice> deviceList = new ArrayList<>();
    private DeviceListAdapter deviceAdapter;

    private Button scanButton;
    private TextView statusTextView;
    private ListView deviceListView;
    private ProgressBar progressBar;

    private boolean isScanning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化UI控件
        initializeUI();

        // 初始化蓝牙
        initializeBluetooth();

        // 设置设备列表适配器
        deviceAdapter = new DeviceListAdapter(this, deviceList);
        deviceListView.setAdapter(deviceAdapter);

        // 设置点击监听
        setClickListeners();
    }

    private void initializeUI() {
        scanButton = findViewById(R.id.btn_scan);
        statusTextView = findViewById(R.id.txt_status);
        deviceListView = findViewById(R.id.list_devices);
        progressBar = findViewById(R.id.progress_scan);
    }

    private void initializeBluetooth() {
        // 获取蓝牙管理器
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
        }

        // 检查设备是否支持蓝牙
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "设备不支持蓝牙", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 注册蓝牙广播接收器
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(bluetoothReceiver, filter);
    }

    private void setClickListeners() {
        // 扫描按钮点击事件
        scanButton.setOnClickListener(v -> {
            if (isScanning) {
                stopScan();
            } else {
                startScan();
            }
        });

        // 设备列表项点击事件
        deviceListView.setOnItemClickListener((parent, view, position, id) -> {
            // 停止扫描
            if (isScanning) {
                stopScan();
            }

            BluetoothDevice device = deviceList.get(position);
            connectToDevice(device);
        });
    }

    private void startScan() {
        // 检查蓝牙是否开启
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestBluetoothPermissions();
                return;
            }
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return;
        }

        // 检查位置权限（Android 6.0及以上扫描蓝牙需要位置权限）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.ACCESS_FINE_LOCATION },
                        REQUEST_LOCATION_PERMISSION);
                return;
            }
        }

        // 清空设备列表
        deviceList.clear();

        // 添加已配对设备
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            if (pairedDevices.size() > 0) {
                for (BluetoothDevice device : pairedDevices) {
                    if (!deviceList.contains(device)) {
                        deviceList.add(device);
                    }
                }
                deviceAdapter.notifyDataSetChanged();
            }
        }

        // 开始扫描
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            bluetoothAdapter.startDiscovery();
        } else {
            requestBluetoothPermissions();
        }
    }

    private void stopScan() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
        }

        isScanning = false;
        scanButton.setText(R.string.scan_bluetooth);
        progressBar.setVisibility(View.GONE);
    }

    private void connectToDevice(BluetoothDevice device) {
        statusTextView.setText(R.string.connecting);

        // 启动数据显示界面，并传递设备信息
        Intent intent = new Intent(MainActivity.this, DataDisplayActivity.class);
        intent.putExtra("device_address", device.getAddress());
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            intent.putExtra("device_name",
                    device.getName() != null ? device.getName() : getString(R.string.unknown_device));
        } else {
            intent.putExtra("device_name", getString(R.string.unknown_device));
        }
        startActivity(intent);
    }

    private void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[] {
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT
                    },
                    REQUEST_BLUETOOTH_PERMISSIONS);
        }
    }

    // 蓝牙广播接收器
    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                // 开始扫描
                isScanning = true;
                scanButton.setText(R.string.cancel);
                progressBar.setVisibility(View.VISIBLE);
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                // 扫描结束
                stopScan();
            } else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // 找到设备
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null && !deviceList.contains(device)) {
                    deviceList.add(device);
                    deviceAdapter.notifyDataSetChanged();
                }
            }
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                startScan();
            } else {
                Toast.makeText(this, R.string.enable_bluetooth, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case REQUEST_LOCATION_PERMISSION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startScan();
                } else {
                    Toast.makeText(this, R.string.permission_required, Toast.LENGTH_SHORT).show();
                }
                break;

            case REQUEST_BLUETOOTH_PERMISSIONS:
                boolean allGranted = true;
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        allGranted = false;
                        break;
                    }
                }

                if (allGranted) {
                    startScan();
                } else {
                    Toast.makeText(this, "需要蓝牙权限才能扫描和连接设备", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // 注销广播接收器
        unregisterReceiver(bluetoothReceiver);

        // 停止扫描
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
        }
    }
}
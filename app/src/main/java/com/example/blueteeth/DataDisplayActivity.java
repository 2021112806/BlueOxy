package com.example.blueteeth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import android.content.BroadcastReceiver;

public class DataDisplayActivity extends AppCompatActivity {

    private static final String TAG = "DataDisplayActivity";
    private static final int MESSAGE_READ = 1;
    private static final int MESSAGE_STATUS = 2;
    private static final int MAX_DISPLAYED_DATA = 100; // 最大显示数据条数
    private static final float OXYGEN_THRESHOLD = 16.0f; // 氧气浓度阈值

    private TextView deviceInfoTextView;
    private TextView connectionStatusTextView;
    private TextView dataValuesTextView;
    private TextView oxygenLevelTextView; // 显示最新氧气浓度的大号文本
    private ImageView oxygenIndicator; // 氧气浓度指示灯
    private Button startMeasureButton;
    private Button viewHistoryButton;
    private Button viewChartButton;

    private String deviceName;
    private String deviceAddress;

    private float lastOxygenLevel = 0.0f; // 最后一次记录的氧气浓度

    private BluetoothService bluetoothService;
    private boolean isServiceBound = false;
    
    // 定时刷新UI的Handler
    private final Handler uiUpdateHandler = new Handler(Looper.getMainLooper());
    private Runnable uiUpdateRunnable;
    private static final int UI_UPDATE_INTERVAL = 500; // 500毫秒更新一次UI

    // 不再需要数据请求的广播接收器，数据由服务直接管理

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BluetoothService.LocalBinder binder = (BluetoothService.LocalBinder) service;
            bluetoothService = binder.getService();
            isServiceBound = true;

            // 重要：设置Handler，确保能接收状态更新
            bluetoothService.setHandler(handler);

            // 连接蓝牙设备
            if (deviceAddress != null) {
                // 当服务绑定成功时，立即更新状态为"正在连接"
                connectionStatusTextView.setText(R.string.connecting);
                // 尝试连接
                bluetoothService.connect(deviceAddress);
            }
            
            // 开始定时刷新UI
            startUiUpdateTimer();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bluetoothService = null;
            isServiceBound = false;
            
            // 停止UI更新
            stopUiUpdateTimer();
        }
    };

    // 处理从蓝牙服务接收到的消息
    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case MESSAGE_READ:
                    // 不需要在这里处理数据，数据会由服务保存，我们只需定期刷新UI即可
                    break;

                case MESSAGE_STATUS:
                    int status = msg.arg1;
                    String statusText = "未知状态";

                    switch (status) {
                        case BluetoothService.STATE_CONNECTED:
                            statusText = "已连接";
                            connectionStatusTextView.setText(R.string.connected);
                            Log.i(TAG, "蓝牙已连接");
                            break;

                        case BluetoothService.STATE_CONNECTING:
                            statusText = "连接中";
                            connectionStatusTextView.setText(R.string.connecting);
                            Log.i(TAG, "蓝牙连接中");
                            break;

                        case BluetoothService.STATE_DISCONNECTED:
                            statusText = "已断开";
                            connectionStatusTextView.setText(R.string.disconnected);
                            Log.i(TAG, "蓝牙已断开");
                            
                            // 如果蓝牙断开，更新UI显示
                            startMeasureButton.setText(R.string.start_measure);
                            Toast.makeText(DataDisplayActivity.this, "蓝牙连接已断开，测量已停止", Toast.LENGTH_SHORT).show();
                            break;

                        case BluetoothService.STATE_CONNECTION_FAILED:
                            statusText = "连接失败";
                            connectionStatusTextView.setText(R.string.connection_failed);
                            Log.i(TAG, "蓝牙连接失败");
                            
                            // 连接失败也更新UI
                            startMeasureButton.setText(R.string.start_measure);
                            Toast.makeText(DataDisplayActivity.this, R.string.connection_failed, Toast.LENGTH_SHORT)
                                    .show();
                            break;
                    }

                    Log.d(TAG, "蓝牙状态更新: " + statusText);
                    break;
            }
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_display);

        // 启用返回按钮
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // 初始化UI控件
        initializeUI();

        // 获取设备信息
        deviceName = getIntent().getStringExtra("device_name");
        deviceAddress = getIntent().getStringExtra("device_address");

        // 显示设备信息
        if (deviceName != null && deviceAddress != null) {
            String deviceInfo = deviceName + " (" + deviceAddress + ")";
            deviceInfoTextView.setText(deviceInfo);
        }

        // 绑定蓝牙服务
        Intent serviceIntent = new Intent(this, BluetoothService.class);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        // 设置点击监听
        setClickListeners();
    }

    private void initializeUI() {
        deviceInfoTextView = findViewById(R.id.txt_device_info);
        connectionStatusTextView = findViewById(R.id.txt_connection_status);
        dataValuesTextView = findViewById(R.id.txt_data_values);
        oxygenLevelTextView = findViewById(R.id.txt_oxygen_level); // 初始化氧气浓度显示
        oxygenIndicator = findViewById(R.id.img_oxygen_indicator); // 初始化氧气浓度指示灯
        startMeasureButton = findViewById(R.id.btn_start_measure);
        viewHistoryButton = findViewById(R.id.btn_view_history);
        viewChartButton = findViewById(R.id.btn_view_chart);
    }

    private void setClickListeners() {
        // 开始/停止测量按钮
        startMeasureButton.setOnClickListener(v -> {
            // 检查蓝牙服务是否已连接
            if (isServiceBound && bluetoothService != null) {
                int connectionState = bluetoothService.getState();
                if (connectionState != BluetoothService.STATE_CONNECTED) {
                    // 如果未连接，显示提示信息
                    Toast.makeText(this, "蓝牙未连接，请先连接设备", Toast.LENGTH_SHORT).show();
                    connectionStatusTextView.setText(R.string.disconnected);
                    return;
                }
            } else {
                // 如果蓝牙服务未绑定或为null，显示提示信息
                Toast.makeText(this, "蓝牙服务未就绪，请稍后再试", Toast.LENGTH_SHORT).show();
                return;
            }

            if (bluetoothService.isMeasuring()) {
                // 停止测量
                bluetoothService.stopMeasuring();
                startMeasureButton.setText(R.string.start_measure);
            } else {
                // 开始测量
                bluetoothService.startMeasuring();
                startMeasureButton.setText(R.string.stop_measure);
            }
        });

        // 查看历史按钮
        viewHistoryButton.setOnClickListener(v -> {
            Intent intent = new Intent(DataDisplayActivity.this, HistoryActivity.class);
            startActivity(intent);
        });

        // 查看图表按钮
        viewChartButton.setOnClickListener(v -> {
            if (isServiceBound && bluetoothService != null) {
                Intent intent = new Intent(DataDisplayActivity.this, ChartActivity.class);
                // 不需要传递数据，ChartActivity将直接从服务获取
                startActivity(intent);
            } else {
                Toast.makeText(this, "蓝牙服务未就绪，请稍后再试", Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    // 开始定时刷新UI
    private void startUiUpdateTimer() {
        uiUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (isServiceBound && bluetoothService != null) {
                    // 从服务获取最新数据并更新UI
                    updateDataDisplay();
                }
                
                // 继续定时执行
                uiUpdateHandler.postDelayed(this, UI_UPDATE_INTERVAL);
            }
        };
        
        // 立即开始第一次更新
        uiUpdateHandler.post(uiUpdateRunnable);
    }
    
    // 停止定时刷新UI
    private void stopUiUpdateTimer() {
        if (uiUpdateHandler != null && uiUpdateRunnable != null) {
            uiUpdateHandler.removeCallbacks(uiUpdateRunnable);
        }
    }

    // 更新数据显示
    private void updateDataDisplay() {
        if (!isServiceBound || bluetoothService == null) {
            return;
        }
        
        ArrayList<DataPoint> dataPoints = bluetoothService.getDataPoints();
        if (dataPoints.isEmpty()) {
            return;
        }
        
        // 如果是在测量中，更新按钮文本（防止状态不同步）
        boolean isMeasuring = bluetoothService.isMeasuring();
        if (isMeasuring) {
            startMeasureButton.setText(R.string.stop_measure);
        } else {
            startMeasureButton.setText(R.string.start_measure);
        }

        StringBuilder stringBuilder = new StringBuilder();

        // 如果数据点超过最大显示数，只显示最新的数据
        int startIndex = Math.max(0, dataPoints.size() - MAX_DISPLAYED_DATA);

        for (int i = startIndex; i < dataPoints.size(); i++) {
            DataPoint point = dataPoints.get(i);
            String typePrefix = "";

            switch (point.getType()) {
                case DataPoint.TYPE_RAW:
                    typePrefix = "ADC: ";
                    break;
                case DataPoint.TYPE_PERCENTAGE:
                    typePrefix = "氧浓度: ";
                    // 实时更新氧气浓度显示
                    updateOxygenDisplay(point.getValue());
                    break;
                case DataPoint.TYPE_VOLTAGE:
                    typePrefix = "电压: ";
                    break;
            }

            stringBuilder.append(point.getTimestamp())
                    .append(" ")
                    .append(typePrefix)
                    .append(point.getFormattedValue())
                    .append("\n");
        }

        dataValuesTextView.setText(stringBuilder.toString());
    }

    // 更新氧气浓度显示和指示灯
    private void updateOxygenDisplay(float oxygenLevel) {
        // 更新大号文本显示
        String oxygenText = String.format(Locale.getDefault(), "%.1f%%", oxygenLevel);
        oxygenLevelTextView.setText(oxygenText);

        // 根据氧气浓度阈值更新指示灯颜色
        if (oxygenLevel >= OXYGEN_THRESHOLD) {
            // 氧气浓度大于等于16%，显示蓝色指示灯
            oxygenIndicator.setImageResource(R.drawable.ic_indicator_blue);
            oxygenLevelTextView.setTextColor(Color.rgb(0, 102, 204)); // 蓝色文本
        } else {
            // 氧气浓度小于16%，显示红色指示灯
            oxygenIndicator.setImageResource(R.drawable.ic_indicator_red);
            oxygenLevelTextView.setTextColor(Color.rgb(204, 0, 0)); // 红色文本
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // 返回按钮点击事件
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 恢复时重新开始UI更新
        if (isServiceBound && bluetoothService != null) {
            startUiUpdateTimer();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // 暂停时停止UI更新
        stopUiUpdateTimer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // 停止UI更新
        stopUiUpdateTimer();
        
        // 解绑服务
        if (isServiceBound) {
            unbindService(serviceConnection);
            isServiceBound = false;
        }
    }
}
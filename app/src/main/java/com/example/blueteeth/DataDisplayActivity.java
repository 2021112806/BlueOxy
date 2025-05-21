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

    private boolean isMeasuring = false;
    private ArrayList<DataPoint> dataPoints = new ArrayList<>();
    private float lastOxygenLevel = 0.0f; // 最后一次记录的氧气浓度

    private BluetoothService bluetoothService;
    private boolean isServiceBound = false;

    // 添加刷新数据的广播接收器
    private final BroadcastReceiver dataRequestReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.example.blueteeth.REQUEST_DATA".equals(intent.getAction())) {
                Log.d("DataDisplayActivity", "收到图表刷新请求");

                // 直接将当前数据发送回去，而不是向蓝牙设备请求
                Intent dataIntent = new Intent("com.example.blueteeth.DATA_UPDATED");
                dataIntent.putParcelableArrayListExtra("data_points", dataPoints);
                context.sendBroadcast(dataIntent);

                Log.d("DataDisplayActivity", "已将当前数据发送给图表页面");
            }
        }
    };

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
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bluetoothService = null;
            isServiceBound = false;
        }
    };

    // 处理从蓝牙服务接收到的消息
    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case MESSAGE_READ:
                    if (isMeasuring) {
                        byte[] readBuffer = (byte[]) msg.obj;
                        int readBufferPosition = msg.arg1;

                        // 将接收到的字节转换为字符串
                        String receivedData = new String(readBuffer, 0, readBufferPosition);

                        // STM32发送的格式是完整的一行，不需要额外处理
                        processReceivedData(receivedData.trim());
                    }
                    break;

                case MESSAGE_STATUS:
                    int status = msg.arg1;
                    String statusText = "未知状态";

                    switch (status) {
                        case BluetoothService.STATE_CONNECTED:
                            statusText = "已连接";
                            connectionStatusTextView.setText(R.string.connected);
                            Log.i("DataDisplay", "蓝牙已连接");
                            break;

                        case BluetoothService.STATE_CONNECTING:
                            statusText = "连接中";
                            connectionStatusTextView.setText(R.string.connecting);
                            Log.i("DataDisplay", "蓝牙连接中");
                            break;

                        case BluetoothService.STATE_DISCONNECTED:
                            statusText = "已断开";
                            connectionStatusTextView.setText(R.string.disconnected);
                            Log.i("DataDisplay", "蓝牙已断开");
                            // 如果断开连接且正在测量，则停止测量
                            if (isMeasuring) {
                                isMeasuring = false;
                                startMeasureButton.setText(R.string.start_measure);
                                Toast.makeText(DataDisplayActivity.this, "蓝牙连接已断开，测量已停止", Toast.LENGTH_SHORT).show();
                            }
                            break;

                        case BluetoothService.STATE_CONNECTION_FAILED:
                            statusText = "连接失败";
                            connectionStatusTextView.setText(R.string.connection_failed);
                            Log.i("DataDisplay", "蓝牙连接失败");
                            // 如果连接失败且正在测量，则停止测量
                            if (isMeasuring) {
                                isMeasuring = false;
                                startMeasureButton.setText(R.string.start_measure);
                            }
                            Toast.makeText(DataDisplayActivity.this, R.string.connection_failed, Toast.LENGTH_SHORT)
                                    .show();
                            break;
                    }

                    Log.d("DataDisplay", "蓝牙状态更新: " + statusText);
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

        // 注册广播接收器
        IntentFilter filter = new IntentFilter("com.example.blueteeth.REQUEST_DATA");
        registerReceiver(dataRequestReceiver, filter, Context.RECEIVER_NOT_EXPORTED);

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

            if (isMeasuring) {
                // 停止测量
                isMeasuring = false;
                startMeasureButton.setText(R.string.start_measure);
            } else {
                // 开始测量
                isMeasuring = true;
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
            Intent intent = new Intent(DataDisplayActivity.this, ChartActivity.class);
            // 传递当前数据点列表
            intent.putParcelableArrayListExtra("data_points", dataPoints);
            startActivity(intent);
        });
    }

    // 处理接收到的数据
    private void processReceivedData(String data) {
        // 获取当前时间戳
        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());

        // 检查数据格式
        if (data.startsWith("Data1:")) {
            // 处理原始ADC值
            try {
                String valueStr = data.substring(data.indexOf(":") + 1).trim();
                float value = Float.parseFloat(valueStr);

                // 创建数据点
                DataPoint dataPoint = new DataPoint(timestamp, value, DataPoint.TYPE_RAW);

                // 添加到数据列表
                dataPoints.add(dataPoint);

                // 保存到数据库
                saveDataPointToDB(dataPoint);

                // 更新显示
                updateDataDisplay();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "数据格式错误: " + data, Toast.LENGTH_SHORT).show();
            }
        } else if (data.startsWith("Data2:")) {
            // 处理氧浓度数据
            try {
                // 提取数值，去掉末尾的%符号
                String valueStr = data.substring(data.indexOf(":") + 1, data.indexOf("%")).trim();
                float value = Float.parseFloat(valueStr);

                // 保存最新的氧气浓度值
                lastOxygenLevel = value;

                // 更新氧气浓度显示和指示灯
                updateOxygenDisplay(value);

                // 创建数据点
                DataPoint dataPoint = new DataPoint(timestamp, value, DataPoint.TYPE_PERCENTAGE);

                // 添加到数据列表
                dataPoints.add(dataPoint);

                // 保存到数据库
                saveDataPointToDB(dataPoint);

                // 更新显示
                updateDataDisplay();
            } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
                Toast.makeText(this, "数据格式错误: " + data, Toast.LENGTH_SHORT).show();
            }
        } else if (data.startsWith("Data3:")) {
            // 处理电压数据
            try {
                // 提取数值，去掉末尾的V符号
                String valueStr = data.substring(data.indexOf(":") + 1, data.indexOf("V")).trim();
                float value = Float.parseFloat(valueStr);

                // 创建数据点
                DataPoint dataPoint = new DataPoint(timestamp, value, DataPoint.TYPE_VOLTAGE);

                // 添加到数据列表
                dataPoints.add(dataPoint);

                // 保存到数据库
                saveDataPointToDB(dataPoint);

                // 更新显示
                updateDataDisplay();
            } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
                Toast.makeText(this, "数据格式错误: " + data, Toast.LENGTH_SHORT).show();
            }
        }
    }

    // 保存数据点到数据库
    private void saveDataPointToDB(DataPoint dataPoint) {
        DataDBHelper dbHelper = DataDBHelper.getInstance(this);
        boolean success = dbHelper.addDataPoint(dataPoint);
        if (!success) {
            Log.e("DataDisplayActivity", "保存数据点失败");
        }

        // 清理旧数据（超过一周的）
        int deleted = dbHelper.deleteOldData();
        if (deleted > 0) {
            Log.d("DataDisplayActivity", "已删除 " + deleted + " 条旧数据");
        }
    }

    // 更新数据显示
    private void updateDataDisplay() {
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
                    typePrefix = "呼吸气体氧浓度: ";
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
    protected void onDestroy() {
        super.onDestroy();

        // 解绑服务
        if (isServiceBound) {
            unbindService(serviceConnection);
            isServiceBound = false;
        }

        // 注销广播接收器
        unregisterReceiver(dataRequestReceiver);
    }
}
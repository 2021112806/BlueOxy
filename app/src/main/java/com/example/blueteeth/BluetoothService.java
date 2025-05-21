package com.example.blueteeth;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class BluetoothService extends Service {
    private static final String TAG = "BluetoothService";

    // 蓝牙连接状态常量
    public static final int STATE_DISCONNECTED = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_CONNECTED = 2;
    public static final int STATE_CONNECTION_FAILED = 3;

    // 消息类型
    private static final int MESSAGE_READ = 1;
    private static final int MESSAGE_STATUS = 2;

    // 前台服务相关常量
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "BluetoothServiceChannel";

    // 重连相关常量
    private static final int MAX_RECONNECT_ATTEMPTS = 3; // 最大重连次数
    private static final long RECONNECT_DELAY = 3000; // 重连延迟时间(毫秒)
    private int reconnectAttempts = 0; // 当前重连尝试次数
    private String lastConnectedDeviceAddress; // 最后连接的设备地址
    private boolean autoReconnect = true; // 是否开启自动重连

    // 蓝牙UUID - 使用SPP（串口）协议的UUID
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final IBinder binder = new LocalBinder();
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket socket;
    private ConnectThread connectThread;
    private ConnectedThread connectedThread;
    private Handler handler;
    private int state;
    
    // 数据管理相关
    private boolean isMeasuring = false;
    private ArrayList<DataPoint> dataPoints = new ArrayList<>();
    private static final int MAX_DATA_POINTS = 1000; // 最大数据点数量限制
    private DataDBHelper dbHelper; // 数据库帮助类

    @Override
    public void onCreate() {
        super.onCreate();
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        state = STATE_DISCONNECTED;
        dbHelper = DataDBHelper.getInstance(this);
        
        // 创建通知通道（仅在Android 8.0及以上需要）
        createNotificationChannel();
        
        // 启动前台服务
        startForeground(NOTIFICATION_ID, createNotification("蓝牙服务正在运行", "未连接设备"));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "蓝牙服务",
                    NotificationManager.IMPORTANCE_LOW);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification(String title, String text) {
        // 创建点击通知后的意图（打开主活动）
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        // 构建通知
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_bluetooth)
                .setContentIntent(pendingIntent);

        return builder.build();
    }

    @SuppressLint("NotificationPermission")
    private void updateNotification(String text) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(NOTIFICATION_ID, createNotification("蓝牙服务正在运行", text));
    }

    public void setHandler(Handler handler) {
        this.handler = handler;
    }

    // 管理测量状态的方法
    public void startMeasuring() {
        if (state != STATE_CONNECTED) {
            return;
        }
        isMeasuring = true;
        updateNotification("正在测量数据...");
    }

    public void stopMeasuring() {
        isMeasuring = false;
        updateNotification(state == STATE_CONNECTED ? "已连接设备" : "未连接设备");
    }

    public boolean isMeasuring() {
        return isMeasuring;
    }

    // 获取数据的方法
    public ArrayList<DataPoint> getDataPoints() {
        return new ArrayList<>(dataPoints); // 返回副本以避免并发修改
    }

    // 清除数据的方法
    public void clearDataPoints() {
        dataPoints.clear();
    }

    public void connect(String address) {
        // 记录设备地址以便重连
        lastConnectedDeviceAddress = address;
        reconnectAttempts = 0;

        // 添加日志，便于调试
        Log.d(TAG, "尝试连接到设备: " + address);
        
        // 更新通知
        updateNotification("正在连接到设备...");

        // 如果正在连接，先停止连接线程
        if (state == STATE_CONNECTING) {
            if (connectThread != null) {
                connectThread.cancel();
                connectThread = null;
            }
        }

        // 如果已连接，先断开连接
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        // 通过地址获取设备对象
        try {
            // 检查蓝牙适配器是否初始化
            if (bluetoothAdapter == null) {
                Log.e(TAG, "蓝牙适配器未初始化");
                updateState(STATE_CONNECTION_FAILED);
                return;
            }

            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);

            // 添加日志确认设备获取成功
            Log.d(TAG, "成功获取设备对象: " + (device.getName() != null ? device.getName() : "未知设备"));

            // 启动连接线程
            connectThread = new ConnectThread(device);
            connectThread.start();

            // 更新状态
            updateState(STATE_CONNECTING);
        } catch (SecurityException e) {
            Log.e(TAG, "缺少蓝牙权限", e);
            updateState(STATE_CONNECTION_FAILED);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "设备地址无效: " + address, e);
            updateState(STATE_CONNECTION_FAILED);
        } catch (Exception e) {
            Log.e(TAG, "连接过程中发生意外错误", e);
            updateState(STATE_CONNECTION_FAILED);
        }
    }

    public void disconnect() {
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        updateState(STATE_DISCONNECTED);
    }

    private void updateState(int newState) {
        int oldState = this.state;
        this.state = newState;

        // 记录状态变化
        Log.d(TAG, "蓝牙状态变化: " + stateToString(oldState) + " -> " + stateToString(newState));
        
        // 更新通知
        switch (newState) {
            case STATE_CONNECTED:
                updateNotification("已连接设备");
                break;
            case STATE_CONNECTING:
                updateNotification("正在连接设备...");
                break;
            case STATE_DISCONNECTED:
                updateNotification("未连接设备");
                stopMeasuring(); // 断开连接时停止测量
                break;
            case STATE_CONNECTION_FAILED:
                updateNotification("连接失败");
                break;
        }

        if (handler != null) {
            Message msg = handler.obtainMessage(MESSAGE_STATUS, newState, -1);
            msg.sendToTarget();
            Log.d(TAG, "状态更新消息已发送");
        } else {
            Log.e(TAG, "Handler未设置，无法发送状态更新");
        }
    }

    public int getState() {
        return state;
    }

    // 设置是否自动重连
    public void setAutoReconnect(boolean autoReconnect) {
        this.autoReconnect = autoReconnect;
    }

    // 尝试重新连接
    private void tryReconnect() {
        if (!autoReconnect || lastConnectedDeviceAddress == null) {
            return;
        }

        // 如果超过最大重连次数，放弃重连
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.w(TAG, "达到最大重连次数: " + MAX_RECONNECT_ATTEMPTS);
            return;
        }

        // 增加重连次数
        reconnectAttempts++;

        // 延迟一段时间后重连
        new Handler().postDelayed(() -> {
            Log.i(TAG, "尝试重新连接，第 " + reconnectAttempts + " 次");
            connect(lastConnectedDeviceAddress);
        }, RECONNECT_DELAY);
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;

            try {
                // 检查权限
                if (ActivityCompat.checkSelfPermission(getApplicationContext(),
                        Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "缺少BLUETOOTH_CONNECT权限");
                    // 不再提前返回，而是让tmp保持为null
                } else {
                    // 有权限时才创建socket
                    try {
                        Log.d(TAG, "创建蓝牙Socket: " + device.getAddress());
                        tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
                    } catch (IOException e) {
                        Log.e(TAG, "创建RfcommSocket失败，尝试使用反射方法", e);
                        // 尝试使用反射获取socket (备选方法)
                        try {
                            // 使用反射获取createRfcommSocket方法
                            Method m = device.getClass().getMethod("createRfcommSocket", new Class[] { int.class });
                            tmp = (BluetoothSocket) m.invoke(device, 1);
                            Log.d(TAG, "使用反射方法创建Socket成功");
                        } catch (Exception ex) {
                            Log.e(TAG, "使用反射方法创建Socket也失败", ex);
                        }
                    }
                }
            } catch (SecurityException e) {
                Log.e(TAG, "缺少蓝牙权限", e);
            } catch (Exception e) {
                Log.e(TAG, "创建Socket过程中发生未知错误", e);
            }

            // 无论如何都要初始化mmSocket
            mmSocket = tmp;
            if (mmSocket == null) {
                Log.e(TAG, "Socket创建失败，无法连接");
            } else {
                Log.d(TAG, "Socket创建成功，准备连接");
            }
        }

        public void run() {
            try {
                // 检查权限
                if (ActivityCompat.checkSelfPermission(getApplicationContext(),
                        Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "缺少BLUETOOTH_SCAN权限");
                    updateState(STATE_CONNECTION_FAILED);
                    return;
                }

                // 取消搜索，因为它会减慢连接速度
                bluetoothAdapter.cancelDiscovery();

                // 检查socket是否为null
                if (mmSocket == null) {
                    Log.e(TAG, "Socket为null，无法连接");
                    updateState(STATE_CONNECTION_FAILED);
                    return;
                }

                try {
                    // 尝试连接
                    Log.d(TAG, "开始连接到设备: " + mmDevice.getAddress());
                    mmSocket.connect();
                    Log.d(TAG, "连接成功: " + mmDevice.getAddress());

                    // 连接成功
                    synchronized (BluetoothService.this) {
                        connectThread = null;
                    }

                    // 启动已连接线程
                    connected(mmSocket);

                } catch (IOException e) {
                    // 连接失败
                    try {
                        Log.e(TAG, "连接失败，正在关闭Socket", e);
                        mmSocket.close();
                    } catch (IOException closeException) {
                        Log.e(TAG, "关闭Socket失败", closeException);
                    } catch (SecurityException e2) {
                        Log.e(TAG, "关闭Socket时权限错误", e2);
                    }

                    updateState(STATE_CONNECTION_FAILED);
                    return;
                } catch (SecurityException e) {
                    Log.e(TAG, "连接设备时权限错误", e);
                    updateState(STATE_CONNECTION_FAILED);
                    return;
                }
            } catch (SecurityException e) {
                Log.e(TAG, "取消搜索时权限错误", e);
                updateState(STATE_CONNECTION_FAILED);
            }
        }

        public void cancel() {
            try {
                if (mmSocket != null) {
                    mmSocket.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "关闭连接线程Socket失败", e);
            } catch (SecurityException e) {
                Log.e(TAG, "关闭Socket时权限错误", e);
            }
        }
    }

    private synchronized void connected(BluetoothSocket socket) {
        this.socket = socket;
        Log.i(TAG, "开始连接后处理程序");

        // 取消之前的连接线程
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        // 取消之前的已连接线程
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        // 启动新的已连接线程
        connectedThread = new ConnectedThread(socket);
        connectedThread.start();

        // 更新状态为已连接
        updateState(STATE_CONNECTED);

        Log.i(TAG, "连接已成功建立，正在发送状态更新");
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private byte[] mmBuffer; // 临时缓冲区
        private StringBuilder dataBuffer = new StringBuilder(); // 数据缓冲区，用于存储接收到的完整行

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "获取输入输出流失败", e);
            } catch (SecurityException e) {
                Log.e(TAG, "获取输入输出流时权限错误", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            mmBuffer = new byte[1024];
            int numBytes; // 从输入流读取的字节数

            // 保持监听输入流，直到发生异常
            while (true) {
                try {
                    // 从输入流读取数据
                    numBytes = mmInStream.read(mmBuffer);

                    // 将读取的数据转换为字符串并添加到缓冲区
                    String receivedData = new String(mmBuffer, 0, numBytes);
                    dataBuffer.append(receivedData);

                    // 检查是否包含完整行，处理所有完整行
                    processCompleteLines();

                } catch (IOException e) {
                    Log.e(TAG, "读取输入流时断开连接", e);
                    updateState(STATE_DISCONNECTED);
                    // 连接断开，尝试重连
                    tryReconnect();
                    break;
                }
            }
        }

        // 处理接收到的完整行
        private void processCompleteLines() {
            // 查找所有完整行并处理
            int newlineIndex;
            while ((newlineIndex = dataBuffer.indexOf("\r\n")) != -1) {
                // 提取一行
                String line = dataBuffer.substring(0, newlineIndex);

                // 从缓冲区删除该行（包括换行符）
                dataBuffer.delete(0, newlineIndex + 2); // 2 是 \r\n 的长度

                // 如果是有效行且正在测量，处理数据
                if (line.length() > 0) {
                    // 发送到主线程
                    if (handler != null) {
                        // 创建新的缓冲区来存储这行数据
                        byte[] lineBytes = line.getBytes();

                        // 发送到主线程
                        Message msg = handler.obtainMessage(MESSAGE_READ, lineBytes.length, -1);
                        msg.obj = lineBytes;
                        msg.sendToTarget();
                    }
                    
                    // 如果正在测量，处理数据并存储
                    if (isMeasuring) {
                        processReceivedData(line.trim());
                    }
                }
            }
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
                    addDataPoint(dataPoint);

                } catch (NumberFormatException e) {
                    Log.e(TAG, "数据格式错误: " + data, e);
                }
            } else if (data.startsWith("Data2:")) {
                // 处理氧浓度数据
                try {
                    // 提取数值，去掉末尾的%符号
                    String valueStr = data.substring(data.indexOf(":") + 1, data.indexOf("%")).trim();
                    float value = Float.parseFloat(valueStr);

                    // 创建数据点
                    DataPoint dataPoint = new DataPoint(timestamp, value, DataPoint.TYPE_PERCENTAGE);

                    // 添加到数据列表
                    addDataPoint(dataPoint);

                } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
                    Log.e(TAG, "数据格式错误: " + data, e);
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
                    addDataPoint(dataPoint);

                } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
                    Log.e(TAG, "数据格式错误: " + data, e);
                }
            }
        }
        
        // 添加数据点到数据列表和数据库
        private void addDataPoint(DataPoint dataPoint) {
            // 加入到数据列表
            synchronized (dataPoints) {
                dataPoints.add(dataPoint);
                
                // 如果数据点超过最大限制，删除最早的数据点
                if (dataPoints.size() > MAX_DATA_POINTS) {
                    dataPoints.remove(0);
                }
            }
            
            // 保存到数据库
            saveDataPointToDB(dataPoint);
        }
        
        // 保存数据点到数据库
        private void saveDataPointToDB(DataPoint dataPoint) {
            boolean success = dbHelper.addDataPoint(dataPoint);
            if (!success) {
                Log.e(TAG, "保存数据点失败");
            }

            // 清理旧数据（超过一周的）
            int deleted = dbHelper.deleteOldData();
            if (deleted > 0) {
                Log.d(TAG, "已删除 " + deleted + " 条旧数据");
            }
        }

        // 写入数据到输出流
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
                Log.e(TAG, "写入输出流时发生错误", e);
            }
        }

        // 关闭连接
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "关闭已连接线程Socket失败", e);
            } catch (SecurityException e) {
                Log.e(TAG, "关闭Socket时权限错误", e);
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // 不断开连接，让前台服务继续运行
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disconnect();
        stopForeground(true);
    }

    public class LocalBinder extends Binder {
        public BluetoothService getService() {
            return BluetoothService.this;
        }
    }

    // 辅助方法：将状态转换为字符串
    private String stateToString(int state) {
        switch (state) {
            case STATE_DISCONNECTED:
                return "断开连接";
            case STATE_CONNECTING:
                return "正在连接";
            case STATE_CONNECTED:
                return "已连接";
            case STATE_CONNECTION_FAILED:
                return "连接失败";
            default:
                return "未知状态(" + state + ")";
        }
    }
}
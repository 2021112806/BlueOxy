package com.example.blueteeth;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;

public class DeviceListAdapter extends ArrayAdapter<BluetoothDevice> {

    private final LayoutInflater inflater;

    public DeviceListAdapter(Context context, ArrayList<BluetoothDevice> devices) {
        super(context, 0, devices);
        this.inflater = LayoutInflater.from(context);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_device, parent, false);
            holder = new ViewHolder();
            holder.deviceName = convertView.findViewById(R.id.device_name);
            holder.deviceAddress = convertView.findViewById(R.id.device_address);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        BluetoothDevice device = getItem(position);
        if (device != null) {
            // 检查蓝牙权限
            if (ActivityCompat.checkSelfPermission(getContext(),
                    Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                String deviceName = device.getName();
                if (deviceName != null && !deviceName.isEmpty()) {
                    holder.deviceName.setText(deviceName);
                } else {
                    holder.deviceName.setText(R.string.unknown_device);
                }
            } else {
                holder.deviceName.setText(R.string.unknown_device);
            }

            // 设备地址不需要权限
            holder.deviceAddress.setText(device.getAddress());
        }

        return convertView;
    }

    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
    }
}
package com.ubx.btscannerassistant;

/*
 * Author: Charlie Liao
 * Time: 2025/4/21-14:56
 * E-mail: charlie.liao@icu007.work
 */

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class BluetoothDeviceAdapter extends RecyclerView.Adapter<BluetoothDeviceAdapter.DeviceViewHolder> {

    private List<BluetoothDeviceInfo> deviceList;

    private static final int COLOR_CONNECTED = Color.GREEN;
//    private static final int COLOR_DISCONNECTED = 0xFFFF2196F3; // 蓝色
    private static final int COLOR_DISCONNECTED = Color.BLUE; // 蓝色
    private static final int COLOR_CONNECTING = Color.GRAY;
    private OnDeviceClickListener deviceClickListener;

    public interface OnDeviceClickListener {
        void onDeviceClick(BluetoothDeviceInfo device);
    }

    public BluetoothDeviceAdapter(List<BluetoothDeviceInfo> deviceList) {
        this.deviceList = deviceList;
    }

    public void setOnDeviceClickListener(OnDeviceClickListener listener) {
        this.deviceClickListener = listener;
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View inflateView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_bluetooth_devices, parent, false);
        return new DeviceViewHolder(inflateView);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        BluetoothDeviceInfo deviceInfo = deviceList.get(position);

        // 检查权限
        if (ActivityCompat.checkSelfPermission(holder.itemView.getContext(),
                Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            holder.deviceName.setText(deviceInfo.getName() != null ? deviceInfo.getName() : "未知设备");
        } else {
            holder.deviceName.setText("未知设备");
        }

        holder.deviceAddress.setText(deviceInfo.getAddress());
        int bondState = deviceInfo.getBondState();
        Log.d("Adapter", "onBindViewHolder: the mac:" + deviceInfo.getAddress() + ", " + deviceInfo.getName()  + "'s bondState is " + bondState);
        switch (bondState) {
            case BluetoothDevice.BOND_NONE:
                holder.connectStatus.setTextColor(Color.RED);
                break;
            case BluetoothDevice.BOND_BONDING:
                holder.connectStatus.setTextColor(COLOR_CONNECTING);
                break;
            case BluetoothDevice.BOND_BONDED:
                holder.connectStatus.setTextColor(COLOR_DISCONNECTED);
                break;
        }
        holder.itemView.setOnClickListener(v -> {
            if (deviceClickListener != null) {
                deviceClickListener.onDeviceClick(deviceInfo);
            }
        });
    }

    @Override
    public int getItemCount() {
        return deviceList.size();
    }

    static class DeviceViewHolder extends RecyclerView.ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
        TextView connectStatus;

        public DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            deviceName = itemView.findViewById(R.id.device_name);
            deviceAddress = itemView.findViewById(R.id.device_address);
            connectStatus = itemView.findViewById(R.id.connection_status);
        }
    }
}

package com.ubx.btscannerassistant;

/*
 * Author: Charlie Liao
 * Time: 2025/4/22-15:50
 * E-mail: charlie.liao@icu007.work
 */

import android.bluetooth.BluetoothDevice;

public class BluetoothDeviceInfo {

    private String address;
    private String name;
    private BluetoothDevice device;
    private int bondState;

    public int getBondState() {
        return bondState;
    }

    public BluetoothDeviceInfo(BluetoothDevice device, int bondState) {
        this.device = device;
        this.bondState = bondState;
        this.name = device.getName();
        this.address = device.getAddress();
    }

    public String getAddress() {
        return address;
    }

    public String getName() {
        return name;
    }

    public BluetoothDevice getDevice() {
        return device;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDevice(BluetoothDevice device) {
        this.device = device;
    }

    public void setBondState(int bondState) {
        this.bondState = bondState;
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        BluetoothDeviceInfo that = (BluetoothDeviceInfo) obj;
        return address.equals(that.address);
    }

    @Override
    public int hashCode() {
        return address.hashCode();
    }
}

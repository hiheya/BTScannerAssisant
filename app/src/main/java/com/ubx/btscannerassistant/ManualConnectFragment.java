package com.ubx.btscannerassistant;

/*
 * Author: Charlie Liao
 * Time: 2025/4/21-10:22
 * E-mail: charlie.liao@icu007.work
 */

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class ManualConnectFragment extends Fragment {

    private RecyclerView recyclerView;

    private Button btn_searchBtDevice;
    private BluetoothAdapter bluetoothAdapter;
    private CopyOnWriteArrayList<BluetoothDeviceInfo> deviceList = new CopyOnWriteArrayList<>();
    private BluetoothDeviceAdapter adapter;

    private boolean isDiscovering = false;

    private static final int REQUEST_LOCATION_PERMISSION = 2;

    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private BluetoothSocket bluetoothSocket;
    private ConnectThread connectThread;
    private ConnectedThread connectedThread;

    private static final int REQUEST_BLUETOOTH_CONNECT_PERMISSION = 100;


    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @SuppressLint("NotifyDataSetChanged")
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (Objects.requireNonNull(action)) {
                case BluetoothDevice.ACTION_FOUND:
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    BluetoothDeviceInfo deviceInfo = new BluetoothDeviceInfo(device, device.getBondState());
                    if (!deviceList.contains(deviceInfo)) {
                        deviceList.add(deviceInfo);
                        adapter.notifyItemInserted(deviceList.size() - 1);
                    }
                    break;
                case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                    // 扫描结束（自然结束或手动取消）
                    isDiscovering = false; // 重置状态
                    // 主线程恢复按钮状态
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).setShowSearchIcon(true);
                    }
                    /*new Handler(Looper.getMainLooper()).post(() -> {
                        btn_searchBtDevice.setEnabled(true);
                        btn_searchBtDevice.setText("搜索设备");
                        Toast.makeText(getContext(), "扫描完成", Toast.LENGTH_SHORT).show();
                    });*/
                    break;
                default:
                    break;
            }
        }
    };

    BroadcastReceiver pairingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);

                switch (state) {
                    case BluetoothDevice.BOND_BONDED:
                        Toast.makeText(context, "配对成功", Toast.LENGTH_SHORT).show();
                        break;
                    case BluetoothDevice.BOND_BONDING:
                        Toast.makeText(context, "配对中...", Toast.LENGTH_SHORT).show();
                        break;
                    case BluetoothDevice.BOND_NONE:
                        Toast.makeText(context, "配对失败", Toast.LENGTH_SHORT).show();
                        break;
                }
            } else if (BluetoothDevice.ACTION_PAIRING_REQUEST.equals(intent.getAction())) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                int variant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR);

                if (device != null && variant == BluetoothDevice.PAIRING_VARIANT_PIN) {
                    // 显示 PIN 码输入对话框
                    showPinInputDialog(device);
                }
            }
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkPermissions();
        setupBluetoothAdapter();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof MainActivity && !isDiscovering) {
            ((MainActivity) getActivity()).setShowSearchIcon(true);
        }
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        requireActivity().registerReceiver(receiver, intentFilter);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setShowSearchIcon(false);
        }
        requireActivity().unregisterReceiver(receiver);
        if (bluetoothAdapter != null) {
            bluetoothAdapter.cancelDiscovery();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_PERMISSION
            );
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_manual_connect, container, false);
        recyclerView = view.findViewById(R.id.new_devices_recycler_view);
        setupRecyclerView();
//        btn_searchBtDevice = view.findViewById(R.id.btn_search_devices);
        return view;
    }

    private void setupRecyclerView() {
        adapter = new BluetoothDeviceAdapter(deviceList);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        adapter.setOnDeviceClickListener(deviceInfo -> {
            if (deviceInfo.getBondState() == BluetoothDevice.BOND_BONDED) {
                // 设备已配对，执行连接操作（需自定义连接逻辑）
                Toast.makeText(getContext(), "设备已配对，正在连接...", Toast.LENGTH_SHORT).show();
                connectToDevice(deviceInfo.getDevice());
            } else {
                // 设备未配对，发起配对请求
                pairDevice(deviceInfo.getDevice());
            }
        });
        /*btn_searchBtDevice.setOnClickListener( v -> {
            if (!isDiscovering) { // 仅在非扫描状态响应点击
                startDiscovery();
            }
        });*/

        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            startDiscovery();
        } else {
            Toast.makeText(getContext(), "蓝牙适配器为空或未启用蓝牙", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupBluetoothAdapter() {
        BluetoothManager bluetoothManager = (BluetoothManager) requireActivity().getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
        }

        if (bluetoothAdapter == null) {
            Toast.makeText(getContext(), "设备不支持蓝牙", Toast.LENGTH_SHORT).show();
        }else if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(enableBtIntent);
        }
    }

    void startDiscovery() {
        if (bluetoothAdapter == null || adapter == null) return;

        // 禁用按钮并更新文本
        /*btn_searchBtDevice.setEnabled(false);
        btn_searchBtDevice.setText("扫描设备中...");*/

        // 清空列表
        new Handler(Looper.getMainLooper()).post(() -> {
            deviceList.clear();
            adapter.notifyDataSetChanged();
        });

        // 启动扫描
        isDiscovering = true;
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setShowSearchIcon(false);
        }
        boolean started = bluetoothAdapter.startDiscovery();

        if (!started) {
            isDiscovering = false;
            /*btn_searchBtDevice.setEnabled(true); // 失败时恢复按钮
            btn_searchBtDevice.setText("搜索设备");*/
            Toast.makeText(getContext(), "扫描启动失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void pairDevice(BluetoothDevice device) {
        try {
            if (device.createBond()) {
                Toast.makeText(getContext(), "正在配对...", Toast.LENGTH_SHORT).show();
                registerPairingReceiver();
            } else {
                Toast.makeText(getContext(), "配对请求失败", Toast.LENGTH_SHORT).show();
            }
        } catch (SecurityException e) {
            Toast.makeText(getContext(), "无蓝牙权限", Toast.LENGTH_SHORT).show();
        }
    }

    private void connectToDevice(BluetoothDevice device) {
        // 确保权限（Android 12+ 需要 BLUETOOTH_CONNECT）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                    REQUEST_BLUETOOTH_CONNECT_PERMISSION
            );
            return;
        }

        // 取消之前的连接
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        // 启动新连接线程
        connectThread = new ConnectThread(device);
        connectThread.start();
        Toast.makeText(getContext(), "正在连接...", Toast.LENGTH_SHORT).show();
    }

    private void registerPairingReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST); // 新增
        requireActivity().registerReceiver(pairingReceiver, filter);
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;
            try {
                // 创建 RFCOMM Socket
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    tmp = device.createInsecureRfcommSocketToServiceRecord(MY_UUID);
                } else {
                    tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
                }
            } catch (IOException | SecurityException e) {
                Log.e("ConnectThread", "Socket 创建失败", e);
            }
            mmSocket = tmp;
        }

        @Override
        public void run() {
            // 取消正在进行的发现（会显著降低连接速度）
            if (bluetoothAdapter != null) {
                bluetoothAdapter.cancelDiscovery();
            }

            try {
                // 连接设备（阻塞操作）
                mmSocket.connect();
                // 连接成功，启动数据传输线程
                connectedThread = new ConnectedThread(mmSocket);
                connectedThread.start();
                // 更新 UI
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), "连接成功", Toast.LENGTH_SHORT).show()
                );
            } catch (IOException | SecurityException e) {
                // 连接失败
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), "连接失败: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
                cancel();
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e("ConnectThread", "Socket 关闭失败", e);
            }
        }
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e("ConnectedThread", "流获取失败", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            // 持续监听输入流
            while (true) {
                try {
                    bytes = mmInStream.read(buffer);
                    String receivedData = new String(buffer, 0, bytes);
                    // 主线程更新 UI
                    requireActivity().runOnUiThread(() ->
                            handleReceivedData(receivedData)
                    );
                } catch (IOException e) {
                    // 连接断开
                    requireActivity().runOnUiThread(() ->
                            Toast.makeText(getContext(), "连接已断开", Toast.LENGTH_SHORT).show()
                    );
                    cancel();
                    break;
                }
            }
        }

        // 发送数据（示例）
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
                Log.e("ConnectedThread", "发送失败", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e("ConnectedThread", "Socket 关闭失败", e);
            }
        }
    }

    // 处理接收到的数据（需根据业务实现）
    private void handleReceivedData(String data) {
        Log.d("BluetoothData", "接收: " + data);
        // 示例：更新 UI 或触发操作
    }

    private void showPinInputDialog(BluetoothDevice device) {
        // 切换到主线程显示对话框
        new Handler(Looper.getMainLooper()).post(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
            builder.setTitle("输入 PIN 码");

            final EditText input = new EditText(getContext());
            input.setInputType(InputType.TYPE_CLASS_NUMBER);
            builder.setView(input);

            builder.setPositiveButton("确定", (dialog, which) -> {
                String pin = input.getText().toString();
                if (!TextUtils.isEmpty(pin)) {
                    try {
                        // Android 12+ 需要 BLUETOOTH_CONNECT 权限
                        Log.d("TAG", "showPinInputDialog: ");
                            byte[] pinBytes = pin.getBytes(StandardCharsets.UTF_8);
                            device.setPin(pinBytes);
                            device.setPairingConfirmation(true); // 确认配对
                    } catch (SecurityException e) {
                        Toast.makeText(getContext(),"权限不足", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            builder.setNegativeButton("取消", (dialog, which) -> {
                device.setPairingConfirmation(false);
            });

            builder.setCancelable(false);
            builder.show();
        });
    }
}

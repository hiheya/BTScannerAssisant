package com.ubx.btscannerassistant;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import android.view.Menu;
import android.view.MenuItem;

import com.google.android.material.navigation.NavigationView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private DrawerLayout drawer;
    private boolean showSearchIcon = false;

    private final ActivityResultLauncher<String[]> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            permissions -> {
                /*if (permissions.get(Manifest.permission.BLUETOOTH_SCAN) != null) {
                    handleBluetoothScan();
                }*/
            });

    public void setShowSearchIcon(boolean show) {
        this.showSearchIcon = show;
        invalidateOptionsMenu(); // 触发菜单重绘
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem searchItem = menu.findItem(R.id.action_search_devices);
        searchItem.setVisible(showSearchIcon); // 根据标志显示/隐藏
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_search_devices) {
            // 获取当前 Fragment 并触发搜索
            Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.content_frame);
            if (fragment instanceof ManualConnectFragment) {
                ((ManualConnectFragment) fragment).startDiscovery();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkPermissions();

        // 设置工具栏
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // 设置抽屉布局
        drawer = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        // 创建抽屉开关
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        // 默认显示第一个Fragment
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction().replace(R.id.content_frame,
                    new ManualConnectFragment()).commit();
            navigationView.setCheckedItem(R.id.nav_manual_connect);
            setTitle("手动连接BT扫描仪");
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // 处理导航视图项目点击
        int id = item.getItemId();
        Fragment selectedFragment = null;
        String title = "";

        if (id == R.id.nav_manual_connect) {
            selectedFragment = new ManualConnectFragment();
            title = "手动连接BT扫描仪";
        } else if (id == R.id.nav_connected_devices) {
            selectedFragment = new ConnectedDevicesFragment();
            title = "已连接的BT扫描仪";
        } else if (id == R.id.nav_scan_connect) {
            selectedFragment = new ScanConnectFragment();
            title = "扫码连接BT扫描仪";
        } else if (id == R.id.nav_settings) {
            selectedFragment = new SettingsFragment();
            title = "设置";
        }

        // 切换Fragment
        if (selectedFragment != null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.content_frame, selectedFragment)
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .commit();
            setTitle(title);
        }

        // 关闭抽屉
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private void checkPermissions() {
        List<String> requiredPermissions = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.add(android.Manifest.permission.BLUETOOTH_SCAN);
            requiredPermissions.add(android.Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }

        if (!requiredPermissions.isEmpty()) {
            requestPermissionLauncher.launch(requiredPermissions.toArray(new String[0]));
        }
    }

    @Override
    public void onBackPressed() {
        // 如果抽屉打开，则关闭它
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}
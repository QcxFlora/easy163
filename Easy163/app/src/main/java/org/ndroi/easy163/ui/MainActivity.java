package org.ndroi.easy163.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.net.VpnService;
import android.os.Bundle;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.Toast;

import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import org.ndroi.easy163.BuildConfig;
import org.ndroi.easy163.R;
import org.ndroi.easy163.core.Cache;
import org.ndroi.easy163.core.Local;
import org.ndroi.easy163.utils.EasyLog;
import org.ndroi.easy163.vpn.LocalVPNService;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, CompoundButton.OnCheckedChangeListener {

    private static final int VPN_REQUEST_CODE = 0x0F;
    private ToggleButton vpnToggleButton;
    private boolean hasReceivedServiceBroadcast = false;

    private final BroadcastReceiver vpnStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (hasReceivedServiceBroadcast) return; // 防止重复触发
            hasReceivedServiceBroadcast = true;

            boolean isRunning = intent.getBooleanExtra("isRunning", false);
            Log.d("帝国审计部", "审查服务状态广播: isRunning = " + isRunning);
            vpnToggleButton.setChecked(isRunning);

            if (isRunning) {
                EasyLog.log("帝国审计部审查已开启，愿公民使用顺利！");
            } else {
                EasyLog.log("帝国审计部审查已停止，敬请期待下次开启！");
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 注册本地广播监听审查服务状态
        LocalBroadcastManager.getInstance(this).registerReceiver(vpnStatusReceiver, new IntentFilter("service"));

        // Toolbar 和 DrawerLayout 初始化
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("帝国审计部");

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        // 开关按钮设置监听
        vpnToggleButton = findViewById(R.id.bt_start);
        vpnToggleButton.setOnCheckedChangeListener(this);

        // 日志文本框关联
        EasyLog.setTextView(findViewById(R.id.log));

        // 同步服务状态 UI
        syncVPNServiceState();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(vpnStatusReceiver);
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            // 优雅返回桌面，不关闭程序
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch (id) {
            case R.id.nav_github:
                openUrl("https://github.com/QcxFlora/easy163");
                showSnack("跳转到 GitHub，加星是公民的义务");
                break;
            case R.id.nav_usage:
                showAlertDialog("使用说明", "开启帝国审计部审查服务后即可享受公民服务\n\n" +
                        "若无法动员服务，建议重启设备\n" +
                        "遇异常，尝试肃清蛀虫\n" +
                        "更多帮助，请访问 GitHub");
                break;
            case R.id.nav_statement:
                showAlertDialog("免责声明", "本软件为实验性项目，仅供技术研究使用。\n" +
                        "完全免费，作者不承担使用风险。请合理使用。");
                break;
            case R.id.nav_clear_cache:
                Cache.clear();
                Local.clear();
                showToast("肃清已完成，蛀虫已驱逐");
                break;
            case R.id.nav_about:
                showAlertDialog("关于帝国审计部", "当前版本：" + BuildConfig.VERSION_NAME + "\n" +
                        "版本更新请关注 GitHub Release");
                break;
            default:
                break;
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }

    private void showAlertDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(true)
                .setNegativeButton("动员", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void showSnack(String msg) {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        Snackbar.make(drawer, msg, Snackbar.LENGTH_SHORT).show();
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (isChecked) {
            requestAndStartVPN();
        } else {
            stopVPNService();
        }
    }

    private void syncVPNServiceState() {
        Intent intent = new Intent("control");
        intent.putExtra("cmd", "check");
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void requestAndStartVPN() {
        Intent vpnIntent = VpnService.prepare(this);
        if (vpnIntent != null) {
            startActivityForResult(vpnIntent, VPN_REQUEST_CODE);
        } else {
            onActivityResult(VPN_REQUEST_CODE, RESULT_OK, null);
        }
    }

    private void stopVPNService() {
        Intent intent = new Intent("control");
        intent.putExtra("cmd", "stop");
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        Log.d("帝国审计部", "已结束审查服务");
        showToast("帝国审计部审查已结束");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            Intent intent = new Intent(this, LocalVPNService.class);
            startService(intent);
            EasyLog.log("帝国审计部审查已开启，愿公民合理使用");
        }
    }
        }

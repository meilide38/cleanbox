package com.cleanbox.app.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.cleanbox.app.R;
import com.cleanbox.app.util.FileUtils;
import com.cleanbox.app.util.StorageUtils;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_PERMISSION = 2101;

    private TextView tvUsed;
    private TextView tvSub;
    private TextView tvPercent;
    private ProgressBar bar;
    private View cardPermission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvUsed = findViewById(R.id.tvUsed);
        tvSub = findViewById(R.id.tvSub);
        tvPercent = findViewById(R.id.tvPercent);
        bar = findViewById(R.id.bar);
        cardPermission = findViewById(R.id.cardPermission);

        findViewById(R.id.cardPermission).setOnClickListener(v -> requestAccess());

        findViewById(R.id.cardLarge).setOnClickListener(v ->
                openScan(ScanActivity.TYPE_LARGE, getString(R.string.title_large)));
        findViewById(R.id.cardPackage).setOnClickListener(v ->
                openScan(ScanActivity.TYPE_PACKAGE, getString(R.string.title_package)));
        findViewById(R.id.cardEmpty).setOnClickListener(v ->
                openScan(ScanActivity.TYPE_EMPTY, getString(R.string.title_empty)));
        findViewById(R.id.cardDuplicate).setOnClickListener(v ->
                openScan(ScanActivity.TYPE_DUPLICATE, getString(R.string.title_duplicate)));
        findViewById(R.id.cardMedia).setOnClickListener(v ->
                openScan(ScanActivity.TYPE_MEDIA, getString(R.string.title_media)));
        findViewById(R.id.cardApps).setOnClickListener(v ->
                startActivity(new Intent(this, AppListActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        long[] usage = StorageUtils.usage();
        long total = usage[0];
        long free = usage[1];
        long used = Math.max(0L, total - free);

        tvUsed.setText("已用 " + FileUtils.formatSize(used));
        tvSub.setText("总容量 " + FileUtils.formatSize(total) + " · 可用 " + FileUtils.formatSize(free));

        int percent = total > 0 ? (int) Math.round(used * 100d / total) : 0;
        bar.setProgress(percent);
        tvPercent.setText("占用 " + percent + "%");

        cardPermission.setVisibility(hasAccess() ? View.GONE : View.VISIBLE);
    }

    private boolean hasAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                } catch (Exception ignored) {
                }
            }
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQ_PERMISSION);
        }
    }

    private void openScan(String type, String title) {
        if (!hasAccess()) {
            new AlertDialog.Builder(this)
                    .setTitle("需要文件访问权限")
                    .setMessage("扫描和清理需要读取存储中的文件。请在接下来的设置页中开启「所有文件访问」。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("去设置", (dialog, which) -> requestAccess())
                    .show();
            return;
        }
        Intent intent = new Intent(this, ScanActivity.class);
        intent.putExtra(ScanActivity.EXTRA_TYPE, type);
        intent.putExtra(ScanActivity.EXTRA_TITLE, title);
        startActivity(intent);
    }
}

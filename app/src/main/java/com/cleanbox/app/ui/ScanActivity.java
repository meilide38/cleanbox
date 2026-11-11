package com.cleanbox.app.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cleanbox.app.R;
import com.cleanbox.app.model.FileItem;
import com.cleanbox.app.scan.Scanner;
import com.cleanbox.app.util.FileUtils;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScanActivity extends AppCompatActivity {

    public static final String EXTRA_TYPE = "type";
    public static final String EXTRA_TITLE = "title";

    public static final String TYPE_LARGE = "large";
    public static final String TYPE_PACKAGE = "package";
    public static final String TYPE_EMPTY = "empty";
    public static final String TYPE_DUPLICATE = "duplicate";
    public static final String TYPE_MEDIA = "media";

    private final List<FileItem> items = new ArrayList<>();

    private FileAdapter adapter;
    private ExecutorService executor;
    private TextView tvTitle;
    private TextView tvStatus;
    private TextView tvSummary;
    private TextView tvEmpty;
    private ProgressBar progress;
    private View statusBox;
    private CheckBox cbAll;
    private MaterialButton btnDelete;
    private String type;
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        type = getIntent().getStringExtra(EXTRA_TYPE);
        if (type == null) {
            type = TYPE_LARGE;
        }

        tvTitle = findViewById(R.id.tvTitle);
        tvStatus = findViewById(R.id.tvStatus);
        tvSummary = findViewById(R.id.tvSummary);
        tvEmpty = findViewById(R.id.tvEmpty);
        progress = findViewById(R.id.progress);
        statusBox = findViewById(R.id.statusBox);
        cbAll = findViewById(R.id.cbAll);
        btnDelete = findViewById(R.id.btnDelete);

        String title = getIntent().getStringExtra(EXTRA_TITLE);
        tvTitle.setText(title == null ? getString(R.string.app_name) : title);

        RecyclerView recycler = findViewById(R.id.recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FileAdapter(items, this::updateSummary);
        recycler.setAdapter(adapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        cbAll.setOnClickListener(v -> {
            adapter.selectAll(cbAll.isChecked());
            updateSummary();
        });
        btnDelete.setOnClickListener(v -> confirmDelete());

        executor = Executors.newSingleThreadExecutor();
        startScan();
    }

    private void startScan() {
        busy = true;
        progress.setVisibility(View.VISIBLE);
        tvStatus.setText(R.string.scanning);

        final String scanType = type;
        executor.execute(() -> {
            ArrayList<FileItem> result;
            switch (scanType) {
                case TYPE_PACKAGE:
                    result = Scanner.scanPackages(this::postStatus);
                    break;
                case TYPE_EMPTY:
                    result = Scanner.scanEmptyDirs(this::postStatus);
                    break;
                case TYPE_DUPLICATE:
                    result = Scanner.scanDuplicates(this::postStatus);
                    break;
                case TYPE_MEDIA:
                    result = Scanner.scanMedia(this::postStatus);
                    break;
                case TYPE_LARGE:
                default:
                    result = Scanner.scanLarge(50L * 1024L * 1024L, this::postStatus);
                    break;
            }

            runOnUiThread(() -> {
                items.clear();
                items.addAll(result);
                adapter.notifyDataSetChanged();
                busy = false;
                progress.setVisibility(View.GONE);
                tvStatus.setText("扫描完成 · 共 " + items.size() + " 项");
                statusBox.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
                tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                updateSummary();
            });
        });
    }

    private void postStatus(String text) {
        runOnUiThread(() -> {
            if (busy) {
                tvStatus.setText(text);
            }
        });
    }

    private void updateSummary() {
        int count = 0;
        long size = 0L;
        for (FileItem item : items) {
            if (item.selected) {
                count++;
                size += item.size;
            }
        }
        if (count == 0) {
            tvSummary.setText("共 " + items.size() + " 项");
        } else {
            tvSummary.setText("已选 " + count + " 项 · " + FileUtils.formatSize(size));
        }
        btnDelete.setEnabled(count > 0 && !busy);
        btnDelete.setText(count > 0 ? "删除 " + count : getString(R.string.delete_selected));
    }

    private void confirmDelete() {
        final List<FileItem> targets = new ArrayList<>();
        long size = 0L;
        for (FileItem item : items) {
            if (item.selected) {
                targets.add(item);
                size += item.size;
            }
        }
        if (targets.isEmpty()) {
            return;
        }
        String message = "将删除 " + targets.size() + " 项"
                + (size > 0 ? "，共 " + FileUtils.formatSize(size) : "")
                + "。删除后无法恢复。";
        new AlertDialog.Builder(this)
                .setTitle("确认删除")
                .setMessage(message)
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> doDelete(targets))
                .show();
    }

    private void doDelete(final List<FileItem> targets) {
        busy = true;
        btnDelete.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        tvStatus.setText("正在删除…");

        executor.execute(() -> {
            int okCount = 0;
            long freed = 0L;
            for (FileItem item : targets) {
                long size = item.size;
                if (FileUtils.delete(item.file)) {
                    okCount++;
                    freed += size;
                }
            }
            final int deleted = okCount;
            final long freedSize = freed;

            runOnUiThread(() -> {
                for (Iterator<FileItem> it = items.iterator(); it.hasNext(); ) {
                    FileItem item = it.next();
                    if (!item.file.exists()) {
                        it.remove();
                    }
                }
                adapter.notifyDataSetChanged();
                busy = false;
                progress.setVisibility(View.GONE);
                tvStatus.setText("已清理 " + deleted + " 项 · " + FileUtils.formatSize(freedSize));
                statusBox.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
                tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                cbAll.setChecked(false);
                updateSummary();
                Toast.makeText(this, "已释放 " + FileUtils.formatSize(freedSize),
                        Toast.LENGTH_SHORT).show();
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}

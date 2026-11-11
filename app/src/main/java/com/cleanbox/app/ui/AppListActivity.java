package com.cleanbox.app.ui;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cleanbox.app.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppListActivity extends AppCompatActivity {

    private final List<AppEntry> apps = new ArrayList<>();
    private AppAdapter adapter;
    private ExecutorService executor;

    private static class AppEntry {
        String label;
        String packageName;
        Drawable icon;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_apps);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        RecyclerView recycler = findViewById(R.id.recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AppAdapter();
        recycler.setAdapter(adapter);

        executor = Executors.newSingleThreadExecutor();
        executor.execute(this::loadApps);
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN, null);
        main.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolved = pm.queryIntentActivities(main, 0);
        List<AppEntry> loaded = new ArrayList<>();
        for (ResolveInfo info : resolved) {
            if (info.activityInfo == null) {
                continue;
            }
            AppEntry entry = new AppEntry();
            entry.packageName = info.activityInfo.packageName;
            CharSequence label = info.loadLabel(pm);
            entry.label = label == null ? entry.packageName : label.toString();
            try {
                entry.icon = info.loadIcon(pm);
            } catch (Exception ignored) {
            }
            loaded.add(entry);
        }
        Collections.sort(loaded, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });

        runOnUiThread(() -> {
            apps.clear();
            apps.addAll(loaded);
            adapter.notifyDataSetChanged();
        });
    }

    private void openAppDetail(String packageName) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + packageName));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开应用详情", Toast.LENGTH_SHORT).show();
        }
    }

    private class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {

        class Holder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView label;
            final TextView pkg;

            Holder(View view) {
                super(view);
                icon = view.findViewById(R.id.ivIcon);
                label = view.findViewById(R.id.tvLabel);
                pkg = view.findViewById(R.id.tvPkg);
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_app, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            final AppEntry entry = apps.get(position);
            holder.label.setText(entry.label);
            holder.pkg.setText(entry.packageName);
            if (entry.icon != null) {
                holder.icon.setImageDrawable(entry.icon);
            } else {
                holder.icon.setImageResource(R.mipmap.ic_launcher);
            }
            holder.itemView.setOnClickListener(v -> openAppDetail(entry.packageName));
        }

        @Override
        public int getItemCount() {
            return apps.size();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}

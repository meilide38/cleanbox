package com.cleanbox.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cleanbox.app.R;
import com.cleanbox.app.model.FileItem;
import com.cleanbox.app.util.FileUtils;

import java.util.List;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.Holder> {

    public interface Listener {
        void onSelectionChanged();
    }

    private final List<FileItem> items;
    private final Listener listener;

    public FileAdapter(List<FileItem> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    static class Holder extends RecyclerView.ViewHolder {
        final CheckBox checkBox;
        final TextView name;
        final TextView path;
        final TextView size;
        final TextView note;

        Holder(View view) {
            super(view);
            checkBox = view.findViewById(R.id.cb);
            name = view.findViewById(R.id.tvName);
            path = view.findViewById(R.id.tvPath);
            size = view.findViewById(R.id.tvSize);
            note = view.findViewById(R.id.tvNote);
        }
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_file, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        final FileItem item = items.get(position);
        holder.name.setText(item.file.getName());
        holder.path.setText(FileUtils.briefPath(
                item.file.getParentFile() == null ? item.file : item.file.getParentFile()));
        holder.size.setText(item.directory ? "空目录" : FileUtils.formatSize(item.size));

        if (item.note != null && item.note.length() > 0) {
            holder.note.setVisibility(View.VISIBLE);
            holder.note.setText(item.note);
        } else {
            holder.note.setVisibility(View.GONE);
        }

        holder.checkBox.setOnCheckedChangeListener(null);
        holder.checkBox.setChecked(item.selected);
        holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            item.selected = isChecked;
            if (listener != null) {
                listener.onSelectionChanged();
            }
        });

        holder.itemView.setOnClickListener(v -> {
            item.selected = !item.selected;
            holder.checkBox.setChecked(item.selected);
            if (listener != null) {
                listener.onSelectionChanged();
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void selectAll(boolean checked) {
        for (FileItem item : items) {
            item.selected = checked;
        }
        notifyDataSetChanged();
    }
}

package com.example.maintenancereminder.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.maintenancereminder.R;
import com.example.maintenancereminder.model.MaintenanceTask;
import com.example.maintenancereminder.util.DateUtils;

import java.util.ArrayList;
import java.util.List;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.VH> {
    public interface Listener { void onClick(MaintenanceTask task); }
    private final List<MaintenanceTask> items = new ArrayList<>();
    private final Listener listener;

    public TaskAdapter(Listener listener) { this.listener = listener; }

    public void submit(List<MaintenanceTask> data) { items.clear(); items.addAll(data); notifyDataSetChanged(); }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_task, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull VH holder, int position) { holder.bind(items.get(position)); }
    @Override public int getItemCount() { return items.size(); }

    class VH extends RecyclerView.ViewHolder {
        TextView tvTitle, tvMeta;
        VH(@NonNull View itemView) { super(itemView); tvTitle = itemView.findViewById(R.id.tvTaskTitle); tvMeta = itemView.findViewById(R.id.tvTaskMeta); }
        void bind(MaintenanceTask task) {
            tvTitle.setText(task.title + " [" + task.priority + "]");
            tvMeta.setText("Следующая дата: " + DateUtils.formatDate(task.nextDueDate));
            itemView.setOnClickListener(v -> listener.onClick(task));
        }
    }
}

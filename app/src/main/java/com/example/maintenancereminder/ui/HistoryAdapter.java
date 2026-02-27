package com.example.maintenancereminder.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.maintenancereminder.R;
import com.example.maintenancereminder.model.ServiceHistoryEntry;
import com.example.maintenancereminder.util.DateUtils;

import java.util.ArrayList;
import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.VH> {
    public interface Listener { void onLongClick(ServiceHistoryEntry entry); }
    private final List<ServiceHistoryEntry> items = new ArrayList<>();
    private final Listener listener;
    public HistoryAdapter(Listener listener) { this.listener = listener; }
    public void submit(List<ServiceHistoryEntry> data) { items.clear(); items.addAll(data); notifyDataSetChanged(); }
    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) { return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false)); }
    @Override public void onBindViewHolder(@NonNull VH holder, int position) { holder.bind(items.get(position)); }
    @Override public int getItemCount() { return items.size(); }

    class VH extends RecyclerView.ViewHolder {
        TextView tvTitle, tvMeta;
        VH(@NonNull View itemView) { super(itemView); tvTitle = itemView.findViewById(R.id.tvHistoryTitle); tvMeta = itemView.findViewById(R.id.tvHistoryMeta); }
        void bind(ServiceHistoryEntry e) {
            tvTitle.setText((e.deviceName == null ? "" : e.deviceName + " • ") + e.taskTitle);
            tvMeta.setText("Выполнено: " + DateUtils.formatDate(e.completionDate));
            itemView.setOnLongClickListener(v -> { listener.onLongClick(e); return true; });
        }
    }
}

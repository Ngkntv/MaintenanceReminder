package com.example.maintenancereminder;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.maintenancereminder.model.Equipment;
import com.example.maintenancereminder.util.DateUtils;

import java.util.ArrayList;
import java.util.List;

public class EquipmentAdapter extends ListAdapter<Equipment, EquipmentAdapter.ViewHolder> {

    public interface OnItemClickListener { void onItemClick(Equipment item); }
    public interface OnItemLongClickListener { void onItemLongClick(Equipment item); }

    private static final DiffUtil.ItemCallback<Equipment> DIFF_CALLBACK = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull Equipment oldItem, @NonNull Equipment newItem) {
            return oldItem.id.equals(newItem.id);
        }

        @Override
        public boolean areContentsTheSame(@NonNull Equipment oldItem, @NonNull Equipment newItem) {
            return oldItem.name.equals(newItem.name)
                    && String.valueOf(oldItem.category).equals(String.valueOf(newItem.category))
                    && String.valueOf(oldItem.nearestTaskDueDate).equals(String.valueOf(newItem.nearestTaskDueDate));
        }
    };

    private final OnItemClickListener clickListener;
    private final OnItemLongClickListener longClickListener;

    public EquipmentAdapter(List<Equipment> items, OnItemClickListener clickListener, OnItemLongClickListener longClickListener) {
        super(DIFF_CALLBACK);
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
        submitList(new ArrayList<>(items));
        setHasStableIds(true);
    }

    public void setItems(List<Equipment> newItems) { submitList(new ArrayList<>(newItems)); }

    @Override
    public long getItemId(int position) { return getItem(position).id; }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_equipment, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) { holder.bind(getItem(position)); }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvName;
        private final TextView tvCategory;
        private final TextView tvNearest;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvCategory = itemView.findViewById(R.id.tvCategory);
            tvNearest = itemView.findViewById(R.id.tvNearestDue);
        }

        void bind(Equipment item) {
            tvName.setText(item.name);
            tvCategory.setText(item.category == null || item.category.isEmpty() ? "Категория не указана" : item.category);
            tvNearest.setText(item.nearestTaskDueDate == null
                    ? "Нет регламентных работ"
                    : "Ближайшее обслуживание: " + DateUtils.formatDate(item.nearestTaskDueDate));
            itemView.setOnClickListener(v -> clickListener.onItemClick(item));
            itemView.setOnLongClickListener(v -> {
                longClickListener.onItemLongClick(item);
                return true;
            });
        }
    }
}

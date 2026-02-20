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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Адаптер для отображения списка Equipment в RecyclerView. Принимает действия
 * для короткого и длинного нажатия на элемент списка, чтобы главный экран
 * мог определять, что происходит при выборе.
 */
public class EquipmentAdapter extends ListAdapter<Equipment, EquipmentAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(Equipment item);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(Equipment item);
    }

    private static final DiffUtil.ItemCallback<Equipment> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Equipment>() {
                @Override
                public boolean areItemsTheSame(@NonNull Equipment oldItem, @NonNull Equipment newItem) {
                    return oldItem.id == newItem.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull Equipment oldItem, @NonNull Equipment newItem) {
                    return oldItem.id == newItem.id
                            && safeEquals(oldItem.name, newItem.name)
                            && safeEquals(oldItem.barcode, newItem.barcode)
                            && oldItem.lastServiceDate == newItem.lastServiceDate
                            && oldItem.serviceIntervalDays == newItem.serviceIntervalDays
                            && oldItem.nextServiceDate == newItem.nextServiceDate
                            && safeEquals(oldItem.notes, newItem.notes)
                            && safeEquals(oldItem.photoUri, newItem.photoUri);
                }
            };

    private final OnItemClickListener clickListener;
    private final OnItemLongClickListener longClickListener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    public EquipmentAdapter(List<Equipment> items,
                            OnItemClickListener clickListener,
                            OnItemLongClickListener longClickListener) {
        super(DIFF_CALLBACK);
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
        submitList(new ArrayList<>(items));
        setHasStableIds(true);
    }

    private static boolean safeEquals(String a, String b) {
        if (a == null) return b == null;
        return a.equals(b);
    }

    public void setItems(List<Equipment> newItems) {
        submitList(new ArrayList<>(newItems));
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).id;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_equipment, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Equipment item = getItem(position);
        holder.bind(item);
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvName;
        private final TextView tvBarcode;
        private final TextView tvDates;
        private Equipment currentItem;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvBarcode = itemView.findViewById(R.id.tvBarcode);
            tvDates = itemView.findViewById(R.id.tvDates);

            itemView.setOnClickListener(v -> {
                if (currentItem != null) {
                    clickListener.onItemClick(currentItem);
                }
            });

            itemView.setOnLongClickListener(v -> {
                if (currentItem != null) {
                    longClickListener.onItemLongClick(currentItem);
                    return true;
                }
                return false;
            });
        }

        void bind(Equipment item) {
            currentItem = item;
            tvName.setText(item.name);
            tvBarcode.setText("Штрихкод: " + item.barcode);
            String last = dateFormat.format(item.lastServiceDate);
            String next = dateFormat.format(item.nextServiceDate);
            tvDates.setText("Последнее ТО: " + last + " | Следующее ТО: " + next);
        }
    }
}

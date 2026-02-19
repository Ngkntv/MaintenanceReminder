package com.example.maintenancereminder;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.maintenancereminder.model.Equipment;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/**
 * Адаптер для отображения списка Equipment в RecyclerView. Принимает действия
 * для короткого и длинного нажатия на элемент списка, чтобы главный экран
 * мог определять, что происходит при выборе.
 */
public class EquipmentAdapter extends RecyclerView.Adapter<EquipmentAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(Equipment item);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(Equipment item);
    }

    private List<Equipment> items;
    private final OnItemClickListener clickListener;
    private final OnItemLongClickListener longClickListener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    public EquipmentAdapter(List<Equipment> items,
                            OnItemClickListener clickListener,
                            OnItemLongClickListener longClickListener) {
        this.items = items;
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
    }

    public void setItems(List<Equipment> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
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
        Equipment item = items.get(position);
        holder.bind(item, clickListener, longClickListener);
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvName;
        private final TextView tvBarcode;
        private final TextView tvDates;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvBarcode = itemView.findViewById(R.id.tvBarcode);
            tvDates = itemView.findViewById(R.id.tvDates);
        }

        void bind(Equipment item,
                  OnItemClickListener click,
                  OnItemLongClickListener longClick) {
            tvName.setText(item.name);
            tvBarcode.setText(String.format(Locale.getDefault(), "Штрихкод: %s", item.barcode));
            String last = dateFormat.format(item.lastServiceDate);
            String next = dateFormat.format(item.nextServiceDate);
            tvDates.setText(String.format(Locale.getDefault(), "Последнее ТО: %s | Следующее ТО: %s", last, next));

            itemView.setOnClickListener(v -> click.onItemClick(item));
            itemView.setOnLongClickListener(v -> {
                longClick.onItemLongClick(item);
                return true;
            });
        }
    }
}
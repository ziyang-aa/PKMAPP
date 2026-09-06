package com.example.pkmapp.details;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pkmapp.R;
import com.example.pkmapp.data.Transaction;
import com.example.pkmapp.data.TransactionType;
import com.example.pkmapp.record.MoneyParser;

import java.util.ArrayList;
import java.util.List;

public final class TransactionListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_HEADER = 1;
    private static final int VIEW_TYPE_TRANSACTION = 2;
    private final List<DetailsListItem> items = new ArrayList<>();

    public void submit(List<DetailsListItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).getKind() == DetailsListItem.Kind.DATE_HEADER
                ? VIEW_TYPE_HEADER : VIEW_TYPE_TRANSACTION;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_HEADER) {
            return new HeaderViewHolder(inflater.inflate(R.layout.item_transaction_date_header,
                    parent, false));
        }
        return new TransactionViewHolder(inflater.inflate(R.layout.item_transaction, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        DetailsListItem item = items.get(position);
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).header.setText(item.getHeaderLabel());
            return;
        }
        ((TransactionViewHolder) holder).bind(item.getTransaction());
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static final class HeaderViewHolder extends RecyclerView.ViewHolder {
        private final TextView header;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            header = itemView.findViewById(R.id.transaction_date_header);
        }
    }

    private static final class TransactionViewHolder extends RecyclerView.ViewHolder {
        private final TextView category;
        private final TextView note;
        private final TextView type;
        private final TextView amount;

        TransactionViewHolder(@NonNull View itemView) {
            super(itemView);
            category = itemView.findViewById(R.id.transaction_category);
            note = itemView.findViewById(R.id.transaction_note);
            type = itemView.findViewById(R.id.transaction_type);
            amount = itemView.findViewById(R.id.transaction_amount);
        }

        void bind(Transaction transaction) {
            boolean income = transaction.getType() == TransactionType.INCOME;
            int color = ContextCompat.getColor(itemView.getContext(), income
                    ? R.color.forest_green : R.color.expense_red);
            category.setText(transaction.getCategory());
            note.setText(transaction.getNote().isEmpty() ? transaction.getCategory()
                    : transaction.getNote());
            type.setText(income ? "收入" : "支出");
            type.setTextColor(color);
            amount.setText((income ? "+" : "-")
                    + MoneyParser.formatCents(transaction.getAmountInCents()));
            amount.setTextColor(color);
        }
    }
}
